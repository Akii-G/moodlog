# Moodlog for Android

Moodlog on your Android phone: log your mood, energy, anxiety and a note, see your
month and charts, mark events, keep a dream journal, and sync it all with Moodlog on
your PC over your own Wi-Fi, encrypted. It can also send sleep, steps and resting
heart rate from Health Connect. In English and Italian. Android 8 or newer.

## Install

1. On your phone, open the [latest release](https://github.com/Akii-G/moodlog/releases/latest)
   and download `Moodlog-Android-<version>.apk`.
2. Open the downloaded file. The first time, Android asks to allow installing apps from
   your browser: allow it, then go back and tap **Install**.
3. To update, do the same with the newer `.apk`: it installs over the old one and keeps
   your entries.

Play Protect may say it doesn't know the app, because it isn't on Google Play. Tap
**More details → Install anyway**.

## Connect it to your PC

1. On the PC, open Moodlog → **Phone sync** and switch the receiver on.
2. In the app, open **Settings** and enter the address and token shown there. Tap
   **Test connection**.
3. The phone and the PC must be on the same Wi-Fi. If `pop-os.local` doesn't work,
   use the address Phone sync shows under "If that fails".

Moodlog syncs when you open it and a few seconds after each change. Entries made
away from home stay on the phone and sync when you're back.

## Health Connect

In **Settings → Health Connect**, allow access to steps, heart rate and sleep, then
turn on **Send with each sync**. Your watch or fitness app (for example Samsung Health,
Google Fit or Fitbit) needs to share its data with Health Connect. On Android 13 and
older, install Health Connect from the Play Store first; the app shows a button for it.

## For developers

Kotlin and Jetpack Compose, plus the Health Connect library. The data file
and the sync are the same as the iPhone app's; `SyncCrypto.kt` must match `seal()` and
`unseal()` in `src/moodlog`.

    cd android
    ./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew testDebugUnitTest      # encryption, merging, screenshots in app/build/screenshots

`python3 test/test_android_sync.py` (from the repository root) syncs the app's code
with the real PC receiver. GitHub Actions (`.github/workflows/android.yml`) builds the
signed `.apk` and attaches it to every release; the signing key is in the repository
secrets `MOODLOG_KEYSTORE_B64` and `MOODLOG_KEYSTORE_PASSWORD`.

Translations are in `app/src/main/assets/i18n/<language>.strings`, keyed by the English
text; `tools/extract_android_strings.py --check` lists what's missing.
