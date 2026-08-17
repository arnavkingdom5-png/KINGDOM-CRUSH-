package com.kingdom.crush;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Kingdom Crush native wrapper.
 *
 * This activity ONLY hosts c.html inside a WebView and implements the
 * AndroidBridge interface exactly as c.html expects it (see WebAppInterface.java).
 * No gameplay, UI, or Firebase logic lives here — all of that stays inside c.html,
 * unmodified, running as normal JS/DOM/Firestore-JS-SDK content.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "KingdomCrush";

    // =====================================================================
    // AD UNIT IDS
    // Flip USE_TEST_ADS to false ONLY for the build you upload to Play Console.
    // Google's official test IDs are used while USE_TEST_ADS = true so you can
    // click/watch ads freely during development without risking your AdMob
    // account for invalid traffic.
    // =====================================================================
    private static final boolean USE_TEST_ADS = true;

    private static final String PROD_BANNER_ID       = "ca-app-pub-2082766092953444/6472058478";
    private static final String PROD_INTERSTITIAL_ID = "ca-app-pub-2082766092953444/3113754624";
    private static final String PROD_REWARDED_ID     = "ca-app-pub-2082766092953444/2870212043";

    private static final String TEST_BANNER_ID       = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_ID     = "ca-app-pub-3940256099942544/5224354917";

    private String bannerAdUnitId()       { return USE_TEST_ADS ? TEST_BANNER_ID       : PROD_BANNER_ID; }
    private String interstitialAdUnitId() { return USE_TEST_ADS ? TEST_INTERSTITIAL_ID : PROD_INTERSTITIAL_ID; }
    private String rewardedAdUnitId()     { return USE_TEST_ADS ? TEST_REWARDED_ID     : PROD_REWARDED_ID; }

    // =====================================================================

    WebView webView;
    FrameLayout bannerContainer;
    AdView bannerAdView;

    InterstitialAd interstitialAd;
    RewardedAd rewardedAd;

    // Guards against double-loading (e.g. interstitial requested again before the
    // previous one finished loading).
    private boolean interstitialLoading = false;
    private boolean rewardedLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        bannerContainer = findViewById(R.id.banner_container);

        applyImmersiveFullscreen();

        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "Mobile Ads SDK initialized");
            loadInterstitial();
            loadRewarded();
        });

        setupWebView();
        webView.loadUrl("file:///android_asset/c.html");
    }

    private void applyImmersiveFullscreen() {
        View decor = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decor.setSystemUiVisibility(flags);
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decor.setSystemUiVisibility(flags);
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveFullscreen();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // Kingdom Crush's save system uses window.localStorage — this MUST be on
        // or progress/coins/levelStars/etc. will silently fail to persist.
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // c.html is loaded from file:///android_asset/ but the Firestore leaderboard
        // JS SDK makes cross-origin requests to Google's servers — this flag lets
        // those requests through from a file:// origin.
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
    }

    // =====================================================================
    // BANNER
    // =====================================================================

    void showBanner() {
        runOnUiThread(() -> {
            if (bannerAdView == null) {
                bannerAdView = new AdView(this);
                bannerAdView.setAdUnitId(bannerAdUnitId());
                bannerAdView.setAdSize(AdSize.BANNER);
                bannerContainer.addView(bannerAdView);
                bannerAdView.loadAd(new AdRequest.Builder().build());
            }
            bannerContainer.setVisibility(View.VISIBLE);
        });
    }

    void hideBanner() {
        runOnUiThread(() -> bannerContainer.setVisibility(View.GONE));
    }

    // =====================================================================
    // INTERSTITIAL
    // =====================================================================

    private void loadInterstitial() {
        if (interstitialLoading || interstitialAd != null) return;
        interstitialLoading = true;
        InterstitialAd.load(this, interstitialAdUnitId(), new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        interstitialLoading = false;
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialAd = null;
                                loadInterstitial(); // pre-fetch the next one
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                interstitialAd = null;
                                loadInterstitial();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        interstitialAd = null;
                        interstitialLoading = false;
                        Log.w(TAG, "Interstitial failed to load: " + loadAdError.getMessage());
                    }
                });
    }

    void showInterstitial() {
        runOnUiThread(() -> {
            if (interstitialAd != null) {
                interstitialAd.show(this);
            } else {
                // Not ready — just make sure one is on the way for next time.
                loadInterstitial();
            }
        });
    }

    // =====================================================================
    // REWARDED
    // =====================================================================

    private void loadRewarded() {
        if (rewardedLoading || rewardedAd != null) return;
        rewardedLoading = true;
        RewardedAd.load(this, rewardedAdUnitId(), new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                        rewardedLoading = false;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        rewardedAd = null;
                        rewardedLoading = false;
                        Log.w(TAG, "Rewarded failed to load: " + loadAdError.getMessage());
                    }
                });
    }

    /**
     * type is the exact reward-type string c.html passed to
     * AndroidBridge.showRewarded(type) — e.g. 'revive', 'extraMoves',
     * 'doubleCoins', 'mysteryChest', or a shop item id. It is echoed back
     * verbatim to window.onRewardedAdComplete(type) ONLY if the user actually
     * earns the reward (closes early / network failure => no callback, no reward).
     */
    void showRewarded(String type) {
        runOnUiThread(() -> {
            if (rewardedAd == null) {
                loadRewarded();
                return; // ad not ready — c.html grants nothing, which is correct
            }

            final boolean[] earned = {false};

            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadRewarded(); // pre-fetch the next one
                    if (earned[0]) {
                        notifyRewardedAdComplete(type);
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    rewardedAd = null;
                    loadRewarded();
                }
            });

            rewardedAd.show(this, rewardItem -> earned[0] = true);
        });
    }

    private void notifyRewardedAdComplete(String type) {
        // Preserve the exact string c.html sent us; escape it safely for JS.
        String safeType = type == null ? "" : type.replace("\\", "\\\\").replace("'", "\\'");
        String js = "window.onRewardedAdComplete('" + safeType + "');";
        webView.evaluateJavascript(js, null);
    }

    // =====================================================================
    // MISC BRIDGE ACTIONS
    // =====================================================================

    void openExternalUrl(String url) {
        runOnUiThread(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Log.w(TAG, "openExternalUrl failed: " + e.getMessage());
            }
        });
    }

    void shareAchievement(String base64Png, String title, String text) {
        runOnUiThread(() -> ShareHelper.shareImage(this, base64Png, title, text));
    }

    // =====================================================================
    // BACK BUTTON — delegates to window.onAndroidBack() inside c.html first,
    // exactly as c.html's own comment describes.
    // =====================================================================

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript("window.onAndroidBack && window.onAndroidBack();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                boolean handled = "true".equals(value);
                if (!handled) {
                    runOnUiThread(MainActivity.super::onBackPressed);
                }
            }
        });
    }
  }
      
