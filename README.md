# Vita Home (Chromecast with Google TV launcher)

## Build
Option A - GitHub Actions (nothing to install): create an empty GitHub repo, upload everything in this
folder (including the hidden .github folder), open the Actions tab, run "build-apk", download the
`vita-home-apk` artifact (app-debug.apk).
Option B - Android Studio: File > Open this folder, Build > Build APK(s).

## Install (from your PC, in the folder with platform-tools)
    .\platform-tools\adb.exe -s 192.168.50.3:<port> install -r app-debug.apk
    .\platform-tools\adb.exe -s 192.168.50.3:<port> shell am start -n local.vitahome/.HomeActivity
Run it once so it creates its folders, then add themes (zip files from psvt.ovh, unmodified):
    .\platform-tools\adb.exe -s 192.168.50.3:<port> push theme.zip /sdcard/Android/data/local.vitahome/files/themes/NANA.zip
Press MENU or open the "Themes" bubble to cycle themes.

## Make it the Home screen (only after you've seen it working)
    adb shell cmd package set-home-activity local.vitahome/.HomeActivity
Undo:
    adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity

## Dock
Edit defaults in HomeActivity.kt, or push a favourites.txt (one package name per line) to
/sdcard/Android/data/local.vitahome/files/favourites.txt
