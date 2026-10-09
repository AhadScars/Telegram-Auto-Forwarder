package com.shoaib.tgforward.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Calls SiteStripe from a hidden WebView so the request carries the Associates
 * session. A plain HTTP client is blocked by Amazon. One fetch runs at a time.
 */
object AmazonClient {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val TIMEOUT_MS = 20_000L
    private const val PAGE_SETTLE_MS = 400L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private lateinit var appContext: Context
    private var webView: WebView? = null
    private var pageReady = false
    private var loadedWhileLoggedIn = false
    private var waitingForPage = false
    private var fetchAfterLoad: Runnable? = null
    private var timeoutRunnable: Runnable? = null
    private var activeTask: Task? = null
    private var pumping = false
    private var awaitingTag = false
    private var trackingId: String? = null
    private val queue = ArrayDeque<Task>()

    private class Task(
        val longUrl: String,
        val done: (String?) -> Unit,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        main.post {
            runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        }
    }

    fun loggedIn(): Boolean {
        return try {
            AmznTo.isLoggedIn(cookieHeader())
        } catch (_: Exception) {
            false
        }
    }

    fun logout() {
        val manager = try {
            CookieManager.getInstance()
        } catch (_: Exception) {
            return
        }
        val names = runCatching { cookieHeader() }.getOrDefault("")
            .split(';')
            .map { it.trim().substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val expiry = "Expires=Thu, 01 Jan 1970 00:00:00 GMT"
        for (name in names) {
            manager.setCookie(AmznTo.HOME, "$name=; $expiry; Path=/; Domain=.amazon.in")
            manager.setCookie(AmznTo.HOME, "$name=; $expiry; Path=/")
            manager.setCookie("https://amazon.in/", "$name=; $expiry; Path=/; Domain=.amazon.in")
        }
        manager.flush()
        val clear = {
            loadedWhileLoggedIn = false
            pageReady = false
            trackingId = null
            awaitingTag = false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) clear() else main.post(clear)
    }

    /** Called from the forwarder's IO thread. Returns an amzn.to URL, or null. */
    fun shortenBlocking(longUrl: String): String? {
        if (!::appContext.isInitialized) return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!loggedIn()) return null
        val latch = CountDownLatch(1)
        val box = arrayOfNulls<String>(1)
        val accepted = main.post {
            enqueue(Task(longUrl) { value ->
                box[0] = value
                latch.countDown()
            })
        }
        if (!accepted) return null
        if (!latch.await(TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS)) return null
        return box[0]
    }

    private fun cookieHeader(): String {
        val manager = CookieManager.getInstance()
        return listOf(AmznTo.HOME, "https://amazon.in/")
            .mapNotNull { manager.getCookie(it) }
            .joinToString("; ")
    }

    private fun enqueue(task: Task) {
        queue.add(task)
        pump()
    }

    private fun pump() {
        if (pumping) return
        val task = queue.poll() ?: return
        pumping = true
        try {
            start(task)
        } catch (_: Exception) {
            timeoutRunnable?.let { main.removeCallbacks(it) }
            timeoutRunnable = null
            fetchAfterLoad?.let { main.removeCallbacks(it) }
            fetchAfterLoad = null
            waitingForPage = false
            awaitingTag = false
            activeTask = null
            pumping = false
            task.done(null)
            pump()
        }
    }

    private fun start(task: Task) {
        activeTask = task
        val timeout = Runnable { finish(null) }
        timeoutRunnable = timeout
        main.postDelayed(timeout, TIMEOUT_MS)
        val view = ensureWebView()
        if (pageReady && loadedWhileLoggedIn) {
            runFetch(view, task.longUrl)
        } else {
            waitingForPage = true
            view.loadUrl(AmznTo.HOME)
        }
    }

    private fun finish(value: String?) {
        val timeout = timeoutRunnable ?: return
        timeoutRunnable = null
        main.removeCallbacks(timeout)
        fetchAfterLoad?.let { main.removeCallbacks(it) }
        fetchAfterLoad = null
        waitingForPage = false
        awaitingTag = false
        val task = activeTask
        activeTask = null
        pumping = false
        task?.done(value)
        pump()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val view = WebView(appContext)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(view, true)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = USER_AGENT
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        view.addJavascriptInterface(AmznJsBridge(), "AmznBridge")
        view.resumeTimers()
        view.onResume()
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view == null || url == null || !url.contains("amazon.in")) return
                pageReady = true
                loadedWhileLoggedIn = loggedIn()
                if (!waitingForPage) return
                fetchAfterLoad?.let { main.removeCallbacks(it) }
                val run = Runnable {
                    if (!waitingForPage) return@Runnable
                    waitingForPage = false
                    val longUrl = activeTask?.longUrl ?: return@Runnable
                    runFetch(view, longUrl)
                }
                fetchAfterLoad = run
                main.postDelayed(run, PAGE_SETTLE_MS)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true && waitingForPage) {
                    finish(null)
                }
            }
        }
        webView = view
        return view
    }

    private fun runFetch(view: WebView, longUrl: String) {
        if (trackingId == null) {
            awaitingTag = true
            evaluate(view, AmznTo.tagMapUrl())
        } else {
            awaitingTag = false
            evaluate(view, AmznTo.requestUrl(longUrl, trackingId))
        }
    }

    private fun evaluate(view: WebView, url: String) {
        val quoted = JSONObject.quote(url)
        val script = """
            (function() {
              try {
                fetch($quoted, {credentials:'include', headers:{'Accept':'application/json'}})
                  .then(function(r){ return r.text(); })
                  .then(function(t){ AmznBridge.onResult(t == null ? '' : String(t)); })
                  .catch(function(){ AmznBridge.onResult(''); });
              } catch (e) {
                AmznBridge.onResult('');
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    class AmznJsBridge {
        @JavascriptInterface
        fun onResult(body: String?) {
            AmazonClient.onBridgeResult(body)
        }
    }

    private fun onBridgeResult(body: String?) {
        main.post {
            val task = activeTask
            if (awaitingTag && task != null) {
                awaitingTag = false
                val found = AmznTo.parseTrackingId(body)
                if (!found.isNullOrBlank()) trackingId = found
                val view = webView
                if (view == null) {
                    finish(null)
                } else {
                    evaluate(view, AmznTo.requestUrl(task.longUrl, trackingId ?: AmznTo.tagOf(task.longUrl)))
                }
                return@post
            }
            finish(AmznTo.parseShortUrl(body))
        }
    }
}
