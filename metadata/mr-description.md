Oxu 1.1.x adds on-device translation through a native library (bergamot-translator,
Marian NMT), so the auto-update copy of the 1.0.1 recipe can't build it. This MR adds
1.1.1 (1.1.0 was never built here and is skipped) as one build per ABI:

* `submodules: true`: bergamot-translator is a git submodule
  (`bergamot/src/main/cpp/bergamot-translator`, pinned commit). The recipe follows
  [Offline Translator](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/dev.davidv.translator.yml),
  which uses the same engine.
* `scandelete` for the two `wasm/module/**/package.json` files of bergamot-translator:
  they belong to its WebAssembly build, which Oxu never runs (same as Offline Translator).
* `ndk: 28.2.13676358`, the version set in `bergamot/build.gradle.kts`.
* `sudo`: `make` for the ExternalProject build of pcre2 (ssplit-cpp), and
  `git config --system core.abbrev 10`: marian writes `git log -1 --pretty=%h`
  into the library, so the abbreviation length has to be fixed for a reproducible build.
* `gradleprops: oxu.abis=<abi>` with `VercodeOperation` `10 * %c + 1/2/3`
  (armeabi-v7a, arm64-v8a, x86_64). The app sets that versionCode itself when built for a
  single ABI. The arm64 APK is 10.8 MB instead of 29.6 MB for a universal one.

Reproducible builds stay on (`Binaries` + `AllowedAPKSigningKeys`, per-build `binary`).
I built `v1.1.1` from clean clones with JDK 21, once per ABI, and arm64 a second time
in another directory: both arm64 unsigned APKs were identical
(`53773f4be7066683ba2ef7e058b5f7e66858d2f0abe3881027600296de8521b7`). The GitHub APKs
are those builds after the recipe's `zipalign.py` step, signed with our key (v2/v3, no v1).

The offline models are downloaded at runtime, only when the user asks for a language,
from Mozilla's Remote Settings (`firefox.settings.services.mozilla.com`, the same source
Firefox uses; MPL-2.0 models). The existing NonFreeNet anti-feature is unchanged: it is
about the MyMemory backend, which is still the default.

* [x] The `commit` field is the full hash of the `v1.1.1` tag.
* [x] `fdroid rewritemeta` and `fdroid lint` are clean.
