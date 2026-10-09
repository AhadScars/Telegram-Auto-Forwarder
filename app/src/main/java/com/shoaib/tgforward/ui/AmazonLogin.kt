package com.shoaib.tgforward.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shoaib.tgforward.engine.AmazonClient
import com.shoaib.tgforward.engine.AmznTo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmazonLoginScreen(onDone: () -> Unit, onClose: () -> Unit) {
    val done = rememberUpdatedState(onDone)
    val finished = remember { mutableStateOf(false) }
    val sawLoggedOut = remember { mutableStateOf(!AmazonClient.loggedIn()) }
    val finish = remember {
        {
            if (!finished.value) {
                finished.value = true
                runCatching { CookieManager.getInstance().flush() }
                done.value()
            }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text("Amazon login", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Band") }
                },
                actions = {
                    TextButton(onClick = finish) { Text("Ho gaya") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Associates account. Ho jaye to Ho gaya.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    loginWebView(context, sawLoggedOut, finish)
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun loginWebView(
    context: android.content.Context,
    sawLoggedOut: MutableState<Boolean>,
    onLoggedIn: () -> Unit,
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = AmazonClient.MOBILE_USER_AGENT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!AmazonClient.loggedIn()) {
                    sawLoggedOut.value = true
                } else if (sawLoggedOut.value) {
                    view?.post { onLoggedIn() }
                }
            }
        }
        loadUrl(AmznTo.SIGN_IN)
    }
}
