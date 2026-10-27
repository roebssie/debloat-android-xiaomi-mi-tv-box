# Debloat Android Xiaomi Mi TV Box — Roebssie’s TV OS

A resource-conscious Android TV launcher for a Xiaomi Mi Box. The goal is a calm, minimal home screen instead of the recommendation-heavy Android TV / Google TV experience.

## Hardware target

This was built for my Xiaomi Mi Box S 4K, described here as the circa-2019 box in my setup. Xiaomi also has a separately named **TV Box S (2nd Gen)**, so the exact model should be confirmed from the box’s **Settings → About** page before reproducing the setup on another device.

- [Original Xiaomi Mi Box S specifications](https://www.mi.com/global/product/mi-box-s/specs/)
- [Xiaomi TV Box S (2nd Gen)](https://www.mi.com/global/product/xiaomi-tv-box-s-2nd-gen/)

## Before and after

### Stock Android TV-style home screen

![Stock Android TV-style home screen](screenshots/android-tv-before.png)

### Roebssie’s TV OS

The custom launcher focuses on Netflix, Spotify, YouTube, VLC, and Settings. It adds a Snoopy surf wallpaper, Pretoria North weather, a live analog clock, a glass Wi-Fi tile, and a five-minute custom screensaver.

![Roebssie’s TV OS](screenshots/poef-after.png)

## What changed

- Minimal five-app dock with Apple TV-inspired spacing, glass surfaces, shadows, and focus animation.
- Custom Snoopy wallpaper with ocean edge fill and no bottom strip.
- Live analog clock with a second hand.
- Pretoria North weather with condition-aware icons.
- Square glass Wi-Fi tile; dark when idle and white when focused.
- AirPlay service kept in the background; visible Sideplay/AirPlay menus return to Poef Home.
- Audio-only AirPlay uses a compact banner containing only a dark AirPlay mark and `AirPlay audio`.
- Five-minute custom screensaver with clock, weather, AirPlay mark, and optional media banner.
- Boot receiver opens Poef Home only; it does not open Wi-Fi settings or Sideplay’s visible activity.

## Android TV / Google TV context

Google documents that Android TV and Google TV homes can show movies, shows, app rows, sponsored content, and personalized recommendations. Google’s Apps-only mode hides personalized recommendations but can still retain sponsored content and teasers. This project replaces the home surface with a focused local launcher.

- [Google TV and Android TV overview](https://support.google.com/googletv/answer/10267283)
- [Google TV recommendations and Apps-only mode](https://support.google.com/googletv/answer/10466129)
- [Android TV Apps-only mode](https://support.google.com/androidtv/answer/10267283)

## Repository contents

- `app/` — Poef launcher source, manifest, resources, wallpaper, and sounds.
- `screenshots/` — before/after visual comparison captured from the device.
- `docs/adb-workflow.md` — safe wireless ADB workflow and verification checklist.

## Safety notes

This repository intentionally excludes device IP addresses, private signing keys, passwords, APK output files, account data, and cloud credentials. The AirPlay receiver remains a separate dependency; this launcher keeps it out of the foreground and renders its state.

The launcher targets the Mi Box’s native 1080p output. It uses anti-aliased canvas/vector-style drawing for clean rendering at that resolution; it does not turn 1080p hardware into 4K or 8K hardware.
