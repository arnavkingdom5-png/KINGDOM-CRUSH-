package com.kingdom.crush;

import android.webkit.JavascriptInterface;

/**
 * Exposed to c.html as `window.AndroidBridge`.
 *
 * Every method name/signature here matches exactly what c.html already calls
 * (verified by inspecting c.html directly):
 *   AndroidBridge.showBanner()
 *   AndroidBridge.hideBanner()
 *   AndroidBridge.showRewarded(type)
 *   AndroidBridge.showInterstitial()
 *   AndroidBridge.openExternalUrl(url)
 *   AndroidBridge.shareAchievement(base64Png, title, text)
 *
 * c.html feature-detects every one of these (`window.AndroidBridge && AndroidBridge.x`)
 * before calling, so nothing here needs to exist for the game to run in a normal
 * browser — this class only has to exist and behave correctly inside the APK.
 *
 * Each method simply forwards to MainActivity, which hops onto the UI thread —
 * JS interface calls arrive on a WebView background thread, and Android Views /
 * the Ads SDK must only be touched from the UI thread.
 */
public class WebAppInterface {
    private final MainActivity activity;

    WebAppInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void showBanner() {
        activity.showBanner();
    }

    @JavascriptInterface
    public void hideBanner() {
        activity.hideBanner();
    }

    @JavascriptInterface
    public void showInterstitial() {
        activity.showInterstitial();
    }

    @JavascriptInterface
    public void showRewarded(String type) {
        activity.showRewarded(type);
    }

    @JavascriptInterface
    public void openExternalUrl(String url) {
        activity.openExternalUrl(url);
    }

    @JavascriptInterface
    public void shareAchievement(String base64Png, String title, String text) {
        activity.shareAchievement(base64Png, title, text);
    }
}
