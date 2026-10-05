# Oxu

![Build](https://github.com/Prestgg1/oxu/actions/workflows/build.yml/badge.svg)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Read PDFs and translate any text you select — instantly.**

Oxu extracts the text of a PDF page by page, lets you select a sentence, and
translates it into your language. Nothing is stored on a server, the app has no
accounts, no tracking and no ads.

The source language is detected automatically by the translation service, so you
only ever pick the language you want to *read* in.

## Features

- Page-by-page text extraction from any PDF (text based, not scanned)
- Long-press to select text, then one tap to translate
- 21 target languages, adjustable text size, dark theme
- Recent files with the last page you were on
- Opens PDFs from the file manager or by sharing them into Oxu
- Two translation backends:
  - **MyMemory** — free, no API key, has a daily limit
  - **LibreTranslate** — your own server, better quality, no limits
- Fully offline for reading; only the text you select is sent for translation

## Screenshots

| Home | Reading a PDF |
| --- | --- |
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Reader](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) |

| Selecting text | Translating |
| --- | --- |
| ![Selection](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) | ![Translation](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png) |

| Choosing a language | Settings |
| --- | --- |
| ![Languages](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png) | ![Settings](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png) |

## Download

- **F-Droid** — _pending inclusion_
- **GitHub Releases** — <https://github.com/Prestgg1/oxu/releases>

The release APKs are signed. Verify the signature with:

```bash
apksigner verify --print-certs oxu-1.0.0.apk
```

## Build from source

Requirements: JDK 17 and the Android SDK (compile SDK 37, build tools 36).

```bash
./gradlew :app:assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Signed release build

Put your keystore outside the repository and describe it in
`keystore.properties` in the project root (this file is git-ignored):

```properties
storeFile=/absolute/path/to/oxu-release.jks
storePassword=...
keyAlias=oxu
keyPassword=...
```

Then:

```bash
./gradlew :app:assembleRelease
```

Without `keystore.properties` the release build simply stays unsigned, which is
what F-Droid and CI need.

## Privacy

Oxu has no analytics and no network calls other than the translation request
you trigger. The selected text (and nothing else) is sent to the translation
service you chose in Settings. With LibreTranslate you can point the app at a
server you control, in which case the text never leaves your own infrastructure.

## How it works

- Text extraction: [PDFBox for Android](https://github.com/TomRoush/PdfBox-Android)
- UI: Jetpack Compose, Material 3
- Translation: MyMemory or LibreTranslate, plain HTTP client

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and pull requests are very
welcome.

## License

Oxu is free software released under the **GNU General Public License v3.0 or
later** — see [LICENSE](LICENSE).

Copyright (C) 2026 Prestgg1

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.
