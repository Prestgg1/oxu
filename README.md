# Oxu

![Build](https://github.com/Prestgg1/oxu/actions/workflows/build.yml/badge.svg)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Read PDFs and translate any text you select — instantly.**

Oxu extracts the text of a PDF page by page, lets you select a sentence, and
translates it into your language. Nothing is stored on a server, the app has no
accounts, no tracking and no ads.

You pick the language your documents are written in and the language you want to
*read* in. The source language is never guessed, so even a single word or a name
translates correctly. It can be changed in Settings or straight from the translation sheet.

## Features

- Page-by-page text extraction from any PDF (text based, not scanned)
- EPUB, DOCX, ODT, FB2, HTML and plain text (TXT, Markdown) books and documents
- Web reader: open a link (or share it from your browser) and read only the article
  text, without menus, ads or pictures; read pages stay available offline
- Text shared from any app opens straight in the reader
- Long-press to select text, then one tap to translate
- 21 target languages, adjustable text size, dark theme
- Recent files with the last page you were on
- Opens documents from the file manager or by sharing them into Oxu
- Three translation backends:
  - **MyMemory** — free, no API key, has a daily limit
  - **LibreTranslate** — your own server, better quality, no limits
  - **Offline** — on-device translation; download only the languages you need
    (about 20–35 MB each) and delete them any time
- Online services can fall back to downloaded languages when there's no internet
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

- **F-Droid** — <https://f-droid.org/packages/io.github.Prestgg.oxu/>
- **GitHub Releases** — <https://github.com/Prestgg1/oxu/releases>

The release APKs are signed. Verify the signature with:

```bash
apksigner verify --print-certs oxu-1.0.0.apk
```

## Build from source

Requirements: JDK 17+, the Android SDK (compile SDK 37, build tools 36), NDK
28.2.13676358 and CMake 3.22.1 (for the offline translation engine). The engine
lives in git submodules:

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

The native engine is built for `arm64-v8a`, `armeabi-v7a` and `x86_64`. For a
faster local build, limit it to the ABI of your device:
`./gradlew :app:assembleDebug -Poxu.abis=arm64-v8a`.

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

### Reproducible release build

F-Droid builds Oxu from source and publishes the result under **our** signing
key. For that to work, the build has to be byte-for-byte reproducible, so it has
to run exactly like F-Droid's does:

```bash
git clone https://github.com/Prestgg1/oxu.git && cd oxu
git checkout v1.1.0                    # a clean tree at the tagged commit
git submodule update --init --recursive
git config --global core.abbrev 10     # marian embeds a short git hash; F-Droid sets the same
export JAVA_HOME=/path/to/jdk-21       # F-Droid builds with JDK 21
# NDK 28.2.13676358 and CMake 3.22.1 from the Android SDK

cd app                                  # the Gradle module, i.e. F-Droid's `subdir`
../gradlew assembleRelease

# same alignment F-Droid applies in its postbuild step
git clone https://gitlab.com/fdroid/reproducible-apk-tools.git
python3 ../reproducible-apk-tools/zipalign.py --page-size 16 --pad-like-apksigner \
    --replace app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk

apksigner sign --ks <your.keystore> --v1-signing-enabled false aligned.apk
```

The four things that have to match, because each of them changes the bytes:

| | |
| --- | --- |
| **JDK 21** | a different JDK produces a slightly different `classes.dex` |
| **Clean tree at the tagged commit** | AGP embeds the git state in `META-INF/version-control-info.textproto` |
| **`app/` as the build root** | matches `subdir` in the build metadata |
| **NDK 28.2 and `core.abbrev 10`** | the native translation engine is compiled into the APK |
| **The `zipalign.py` call above** | v1 signing must stay off, otherwise `META-INF/*.SF` files are added |

If you only care about running the app, `./gradlew :app:assembleDebug` is enough.

## Privacy

Oxu has no analytics and no network calls other than the translation request
you trigger and, if you use offline translation, downloading the language list
and the models you pick from Mozilla's servers. Offline translation runs entirely
on the device. The selected text (and nothing else) is sent to the translation
service you chose in Settings. With LibreTranslate you can point the app at a
server you control, in which case the text never leaves your own infrastructure.

## How it works

- Text extraction: [PDFBox for Android](https://github.com/TomRoush/PdfBox-Android),
  [jsoup](https://jsoup.org) (MIT) for EPUB/HTML and web pages
- UI: Jetpack Compose, Material 3
- Translation: MyMemory or LibreTranslate, plain HTTP client
- Offline translation: [bergamot-translator](https://github.com/browsermt/bergamot-translator)
  (Marian NMT, MPL-2.0) with [Firefox Translations models](https://github.com/mozilla/translations)
  by Mozilla. The Android build recipe is adapted from
  [Offline Translator](https://github.com/DavidVentura/offline-translator) (GPL-3.0).

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
