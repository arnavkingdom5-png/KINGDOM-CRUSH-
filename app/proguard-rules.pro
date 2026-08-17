# Add project specific ProGuard rules here.
# Keep the JS interface methods reachable from the WebView (safe even with minifyEnabled false,
# needed if you ever turn minification on for a release build).
-keepclassmembers class com.kingdom.crush.WebAppInterface {
    public *;
}
