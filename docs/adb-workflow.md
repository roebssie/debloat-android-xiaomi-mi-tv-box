# Wireless ADB workflow

The work used wireless Android Debug Bridge (ADB) for inspection, installation, and verification. Replace the example address with the device’s current address; never commit a real address to a public repository.

```sh
adb connect TV_IP:5555
adb devices
```

## Install and restore the launcher

```sh
adb install -r PoefLauncher.apk
adb shell cmd package set-home-activity com.poef.launcher/.MainActivity
adb shell am start -n com.poef.launcher/.MainActivity
```

## Keep the box awake and use the custom screensaver

```sh
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components com.poef.launcher/.PoefDream
```

## Verify the post-boot state

```sh
adb shell dumpsys activity activities
adb shell dumpsys activity services io.github.sudnep.sideplay
adb shell appops get io.github.sudnep.sideplay
```

Expected state: Poef Home is resumed, Sideplay is only a background AirPlay service, and picture-in-picture is denied so its visible activity cannot take over.

## Verification checklist

- Reboot: Xiaomi/Android firmware screens may appear before third-party apps; after that, Poef should be the first app screen.
- Home, Back, Select, D-pad, Wi-Fi tile, volume, and app launch/return paths.
- Spotify media banner appears only while Spotify is active.
- Audio-only AirPlay uses the compact `AirPlay audio` banner and never a full-screen Sideplay menu.
- Five-minute idle transition to `PoefDream`.
- No fatal launcher exception or ANR in logcat.
