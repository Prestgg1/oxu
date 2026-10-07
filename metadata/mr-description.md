<!--
F-Droid merge request description for Oxu.
Copy everything below into the merge request description at
https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new?merge_request%5Bsource_branch%5D=io.github.Prestgg.oxu&merge_request%5Btarget_branch%5D=master
-->

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs.
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). Reason: I tried, and the signature copy verified (v2 and v3) but the rebuilt APK does not match my local build byte for byte — the `CHUNKED_SHA512` digest differs, so the toolchain still produces different output. I did pin what I could (Gradle 9.7.1 via the committed wrapper, AGP 9.4.1, build-tools 36, JDK 17) and I sign the upstream release with `zipalign -P 16` + `apksigner`, but I would like help to find the remaining difference. I understand the APK will then be signed with your key, and that this cannot be changed later.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller. Not needed: 16 MB universal APK, no native ABI-specific code.
* [x] Only the latest versions should be kept in the metadata before it's merged.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained. The only warning in the `check apk` job is `WARNING: Requested API level 36 is larger than maximum we have, returning API level 28 instead.` (and the same from `androguard`). That comes from the scanner not knowing targetSdk 36 yet, not from anything in the app or the metadata.
* [x] F-Droid CI runners are under GitLab's FOSS program.

## Anti-Features

* **NonFreeNet** — the default translation backend is MyMemory
  (`api.mymemory.translated.net`), a proprietary online service, so translating
  depends on a non-free network service. Reading PDFs works entirely offline, and
  only the text the user selects is ever sent. In Settings the user can point Oxu
  at any LibreTranslate server they run themselves, and then no text leaves their
  own infrastructure.

The same reason is recorded in `MaintainerNotes` in the metadata file, and the
store description states that translation needs an online service unless you run
your own LibreTranslate server.

## Notes

Oxu (https://github.com/Prestgg1/oxu) is a small Kotlin/Jetpack Compose PDF reader: it
extracts the text of a PDF page by page, lets you select text with a long press and
translates the selection into a language of your choice. The source language is
detected by the translation service, so only the target language is picked by hand.

* No proprietary dependencies: AndroidX/Compose (Apache-2.0) and PDFBox for Android (Apache-2.0)
* Only permission is `INTERNET`, used for the translation request the user triggers
* Translation backends: MyMemory (free, no key) or a self-hosted LibreTranslate
* minSdk 26, targetSdk 36, AGP 9.4.1 with built-in Kotlin, Gradle wrapper committed
* Release builds are minified with R8 (7.4 MB APK)
* `subdir: app` is the Gradle module directory, which is where the `build/`
  directory is generated; the Gradle root is the repository root
  (`settings.gradle.kts` there includes `:app`)
* The release signing key is not in the repository. `app/build.gradle.kts` only signs
  the release build when a git-ignored `keystore.properties` is present, so the
  build recipe produces an unsigned APK as expected
* Screenshots, icon, summary, full description and changelog are in the upstream
  repository under `fastlane/metadata/android/en-US/`
* Releases are tagged (`v1.0.0`) and `UpdateCheckMode: Tags` is set, so future
  versions are picked up automatically
