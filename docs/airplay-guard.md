# AirPlay integration and receiver guard

I integrated and configured the separate Sideplay AirPlay receiver service for this personal TV OS, then wrote the Poef-side Android guard around it.

The guard has two responsibilities:

- keep audio-only AirPlay quiet in the background and return its text/menu window to Poef Home;
- leave a genuine video-only mirror surface visible so an incoming Mac or iPhone display can render on the TV.

The receiver implementation remains a separate dependency. The Poef launcher owns the integration, the background lifecycle, the media banner, the accessibility window filtering, and the device-side validation. Audio AirPlay and a live 1080p Mac mirroring session were tested on the Mi Box without changing the working audio path.

The public source snapshot includes the relevant guard classes under `app/src/com/poef/launcher/` and intentionally excludes private signing keys, device addresses, passwords, and APK output files.
