-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**
-keep class io.github.tdlibandroid.** { *; }
-keepclassmembers class com.shoaib.tgforward.engine.AmazonClient$AmznJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
