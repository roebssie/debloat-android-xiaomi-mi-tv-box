# AirPlay integration and receiver guard

I integrated and configured the separate Sideplay AirPlay receiver service for this personal TV OS, then wrote the Poef-side Android guard around it.

The guard has two responsibilities:

- keep audio-only AirPlay quiet in the background and return its text/menu window to Poef Home;
- leave a genuine video-only mirror surface visible so an incoming Mac or iPhone display can render on the TV.

The menu guard waits briefly for the receiver window to settle, uses window-content access to inspect the active tree, requires receiver-specific labels and an interactive control, and ignores SurfaceView, TextureView, VideoView, and GLSurfaceView nodes. This prevents the receiver's idle “Poef is ready” page from appearing while avoiding Home transitions during video playback. A repeated 20 ms task-stealing fallback was removed because it could interrupt a live mirror and make audio catch up after a video stall.

The receiver implementation remains a separate dependency. The Poef launcher owns the integration, the background lifecycle, the media banner, the accessibility window filtering, and the device-side validation. The intended verification sequence is idle receiver, audio-only connection, video connection, disconnect, and reconnect; the receiver service must remain foreground-active while its visible idle menu is sent back to Poef Home.

The public source snapshot includes the relevant guard classes under `app/src/com/poef/launcher/` and intentionally excludes private signing keys, device addresses, passwords, and APK output files.
