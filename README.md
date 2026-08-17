# Kingdom Crush — Android Wrapper

Native Android WebView wrapper around `c.html` (unmodified). Implements the
`AndroidBridge` interface that `c.html` already expects, wires up AdMob
(banner / interstitial / rewarded), and keeps the game's Firebase leaderboard,
save system, sharing, and sound working exactly as before.

`c.html` itself was **not changed at all** — it lives untouched at
`app/src/main/assets/c.html`.

## Project structure

```
kingdom-crush-android/
├── build.gradle                     (root)
├── settings.gradle
├── gradle.properties
├── app/
│   ├── build.gradle                 (AdMob dependency, applicationId, SDK versions)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml      (AdMob App ID, permissions, FileProvider)
│       ├── assets/
│       │   └── c.html               ← your game, byte-for-byte unchanged
│       ├── java/com/kingdom/crush/
│       │   ├── MainActivity.java    (WebView + AdMob + fullscreen + back button)
│       │   ├── WebAppInterface.java (the AndroidBridge object c.html calls)
│       │   └── ShareHelper.java     (achievement image sharing)
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/ (strings, colors, themes)
│           ├── xml/file_paths.xml
│           └── mipmap-*/ic_launcher.png
└── .github/workflows/build-apk.yml  ← builds the APK in the cloud for you
```

## How the AndroidBridge maps to your c.html calls

| c.html calls...                          | Native implementation does...                                                        |
|-------------------------------------------|----------------------------------------------------------------------------------------|
| `AndroidBridge.showBanner()`              | Creates/shows a bottom `AdView` banner                                                |
| `AndroidBridge.hideBanner()`              | Hides the banner container                                                            |
| `AndroidBridge.showInterstitial()`        | Shows the pre-loaded interstitial, then loads the next one                            |
| `AndroidBridge.showRewarded(type)`        | Shows the rewarded ad; **only** if the user actually earns it, calls back into JS     |
| *(native → JS callback)*                  | `window.onRewardedAdComplete(type)` — same `type` string c.html sent, unchanged       |
| `AndroidBridge.openExternalUrl(url)`      | Opens the URL in the device browser (used for your YouTube link)                      |
| `AndroidBridge.shareAchievement(...)`     | Decodes the base64 PNG c.html generated and opens the Android share sheet             |
| *(native → JS callback)*                  | `window.onAndroidBack()` is asked first on every back-press, exactly as c.html expects|

Nothing else in `c.html` was touched — gameplay, Firebase leaderboard, shop,
levels, sounds, animations, save/load are all identical to what you uploaded.

## Test ads vs. real ads

`MainActivity.java` has:

```java
private static final boolean USE_TEST_ADS = true;
```

While `true`, the app uses **Google's official test ad unit IDs**, so you can
tap/watch ads freely while testing without risking your real AdMob account
(Google can suspend accounts for "invalid traffic" from a developer clicking
their own live ads).

Your real IDs are already filled in right above it:

```java
private static final String PROD_BANNER_ID       = "ca-app-pub-2082766092953444/6472058478";
private static final String PROD_INTERSTITIAL_ID = "ca-app-pub-2082766092953444/3113754624";
private static final String PROD_REWARDED_ID     = "ca-app-pub-2082766092953444/2870212043";
```

**Before you publish to the Play Store**, open `MainActivity.java` and change:

```java
private static final boolean USE_TEST_ADS = false;
```

That single flag switches all three ad slots over to your real, live ad units.
The AdMob **App ID** in `AndroidManifest.xml` is already your real one
(`ca-app-pub-2082766092953444~4668022214`) — that one is fine to leave as-is
even during testing, it only identifies the app to AdMob.

## Building the APK from your phone (no computer needed)

You don't need Android Studio. GitHub Actions builds the APK for you in the
cloud, and you download the finished file straight to your phone.

**1. Create a GitHub repository**
   - Install the **GitHub** app (or use github.com in your phone's browser).
   - Create a new repository, e.g. `kingdom-crush-android`. Keep it Private if
     you don't want the ad unit IDs public, or Public — either works.

**2. Upload this project folder**
   - Easiest phone method: in the GitHub app/website, use "Add file → Upload
     files" and upload the whole `kingdom-crush-android` folder contents
     (you can drag the extracted folder in on desktop browser, or use the
     GitHub mobile app's upload picker, or a file-manager app that can
     upload a zip and have GitHub auto-extract it isn't supported — so the
     simplest path on a phone is usually: open the repo in a mobile browser,
     tap "Add file → Upload files", then select all the files/folders from
     the extracted zip in your phone's file manager and upload them keeping
     the same folder structure).
   - Make sure `.github/workflows/build-apk.yml` ends up at that exact path —
     GitHub only picks up workflows from `.github/workflows/`.

**3. Let it build**
   - Once the files are pushed to the `main` branch, go to the **Actions**
     tab of your repository. A "Build Kingdom Crush APK" run will start
     automatically (or tap **Run workflow** to trigger it manually).
   - Wait for it to finish (a few minutes) — you'll see a green checkmark.

**4. Download the APK**
   - Open the finished workflow run, scroll to **Artifacts**, and download
     `kingdom-crush-debug-apk`. It's a zip containing `app-debug.apk`.
   - Unzip it (most phone file managers can unzip), then tap the `.apk` file
     to install it. You may need to allow "Install unknown apps" for your
     browser/file manager the first time — Android will prompt you for this.

This produces a **debug-signed APK**, which is perfectly installable and
testable on your own device but is not suitable for Play Store submission —
release builds need a proper signing key. When you're ready for that step
(Play Store release), let me know and we can add a release-signing config to
the workflow using a keystore stored in GitHub Secrets.

## Notes on Firebase

Your leaderboard uses the **Firebase JS SDK loaded from a CDN inside c.html**
(`firebase-app-compat.js`, etc.), not a native Android Firebase SDK. That means:
- No `google-services.json` file is needed.
- No native Firebase Gradle dependencies are needed.
- It works purely because the WebView has internet access and
  `setAllowUniversalAccessFromFileURLs(true)` is set (already done in
  `MainActivity.java`), which lets the file-based `c.html` page make the
  cross-origin requests Firestore needs.

## Notes on localStorage / save data

`c.html`'s save system uses `localStorage`. `MainActivity.java` explicitly
enables `WebSettings.setDomStorageEnabled(true)` — without this your coins,
level progress, boosters, and cosmetics would silently fail to persist inside
the APK (even though they work fine in a normal desktop/mobile browser).
