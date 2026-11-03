# AquaDeck

A fullscreen Android "head unit" display in the style of early-2000s OEL car stereos.
It shows what's playing in Tidal, Spotify or any other music app, controls it, and
plays retro OEL animations (Pioneer `.lkd` files, GIFs, videos) that react to the beat.

* Download the APK from **Releases** (built automatically by GitHub Actions on every push).
* First start: press **OPEN → Media access settings** and allow AquaDeck, so it can read
  the current track and control playback. Allowing the microphone permission lets it read
  the audio spectrum (if the phone allows it); otherwise a simulated beat is used.
* Animations: **OPEN → Carozerra clip library** downloads clips on the device from
  [youxufkhan/carozerra](https://github.com/youxufkhan/carozerra), or load your own file.
  No Pioneer artwork is included in this repository.
