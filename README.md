<div align="center">

# Trivia Capture

**A lightweight, open-source floating screenshot tool for Android.**

Tap a small overlay button from inside any app and the screen (or just the region you saved) is captured, saved as a JPEG and registered with the Gallery — ready to view, edit, crop and share.

![License](https://img.shields.io/badge/License-GPLv3-red.svg)
![Android](https://img.shields.io/badge/Android-5.0%2B-3ddc84.svg)
![Status](https://img.shields.io/badge/Status-Active-blue.svg)

</div>

---

## What it is

Trivia Capture started as a fork of *Trivia Hack* (formerly *LoKo HacK*), but the OCR and trivia-answer features are gone. What remains is one focused tool: **a floating button that takes screenshots without leaving the app you are in.**

No accounts, no ads, no tracking.

## Features

| | |
|---|---|
| **Floating capture button** | A small draggable bubble that sits on top of other apps. Tap to capture, drag to move it wherever it is out of the way. |
| **Capture profiles** | Save a reusable capture region. Draw a box over a screenshot and the floating button will capture only that region — or leave it empty for full-screen capture. |
| **Full-screen by default** | Out of the box the button captures the entire screen. |
| **Gallery-ready output** | Screenshots are saved as valid JPEGs in `Pictures/TriviaHacK` and registered with Android's MediaStore, so they appear in the Gallery with normal **Edit**, **Crop** and **Share** actions. |
| **Adjustable button size** | Resize the floating button from the Settings screen. |
| **Save toggle** | Turn screenshot saving on or off; the button still gives visual feedback either way. |
| **Clear save feedback** | The button turns green with a checkmark only after the screenshot is actually written and published. |
| **Crash reporting** | Local crash reports to help make the app more stable. |

## How it works

1. Grant the **overlay** permission so the floating button can appear over other apps.
2. Press **START SCREENSHOT** and accept Android's screen-capture consent dialog.
3. A small button appears on screen. Tap it to capture, drag it to reposition.
4. The screenshot is saved to `Pictures/TriviaHacK` and shows up in your Gallery.

To capture only a region, open **Select Profiles** first, draw the area you want, name it, and every capture will use it. Leave the profile empty for full-screen shots.

## Permissions

| Permission | Why it is needed |
|---|---|
| **Overlay** (`SYSTEM_ALERT_WINDOW`) | Draws the floating capture button over other apps. |
| **Storage** (read/write) | Saves screenshots to the shared Pictures folder. |
| **Screen capture** (`MediaProjection`) | Android's standard consent dialog for capturing the screen; granted once per session. |
| **Internet** | Used only for crash reporting and the update check. |
| **Accessibility** | Declared so the accessibility status indicator on the home screen works. The app **does not** read the content of other apps. |

## Install

This fork publishes build artifacts from its CI rather than the upstream release page:

1. Open the [**Actions**](../../actions) tab.
2. Select the newest green **Build APK** run.
3. Download the `trivia-hack-debug-apk` artifact and install the `.apk` on your device.

Every artifact is signed with the same certificate, so newer builds install as normal updates over older ones (no uninstall, no data loss).

## Build from source

```bash
# Clone
git clone https://github.com/mastertrivia/trivia-hack-screenshot.git
cd trivia-hack-screenshot

# Build the debug APK (JDK 8+ and the Android SDK are required)
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Tech

- Java, Android SDK (`minSdk 19`, `targetSdk 28`)
- `MediaProjection` + `VirtualDisplay` for screen capture
- A version-aware MediaStore save path (Android 10+ scoped storage, legacy file + media scan below)
- Room, AppCompat and a small set of third-party UI libraries

## Supported capture targets

Because the tool captures the screen itself, it works with **any app** — no per-app integration is required.

## Credits

Trivia Capture is a GPLv3 fork of [**Trivia Hack / LoKo HacK**](https://github.com/SubhamTyagi/loco-answers) by [Shubham Tyagi](https://subhamtyagi.github.io/) and keeps the original license and attribution. The floating-screenshot redesign and MediaStore rework are changes made in this fork.

Third-party libraries:

- [BoomMenu](https://github.com/Nightonke/BoomMenu) by Weiping Huang
- [Process Button](https://github.com/dmytrodanylyk/android-process-button) by Danylyk Dmytro
- [SweetAlertDialog](https://github.com/F0RIS/sweet-alert-dialog) by Anton
- [CrashReporter](https://github.com/MindorksOpenSource/CrashReporter) by Bal Sikandar
- [Android Image Cropper](https://github.com/ArthurHub/Android-Image-Cropper) by Arthur
- [AppUpdater](https://github.com/javiersantos/AppUpdater) by Javier Santos

## License

Released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for details.

```
Copyright (C) 2018, 2019 SHUBHAM TYAGI
This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
```
