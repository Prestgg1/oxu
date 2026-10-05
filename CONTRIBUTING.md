# Contributing to Oxu

Thanks for taking the time to help. Oxu is a small Kotlin/Compose app and every
contribution counts.

## Reporting bugs

Open an issue with:

- what you did, what you expected, what happened instead
- your phone model, Android version and Oxu version (the GitHub release tag)
- if possible, the PDF that triggered it — or a minimal example of the text
  layout that broke

## Pull requests

1. Fork the repository and create a branch off `main`.
2. Keep the change focused; unrelated cleanups are best sent as a separate PR.
3. Make sure the project still builds:

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. Every source file carries an SPDX header:

   ```kotlin
   // SPDX-License-Identifier: GPL-3.0-or-later
   ```

   Keep it at the top of the file if you touch it.

5. Write user-facing text in English and keep it consistent with the wording
   already used in the UI.

## Translations

Oxu speaks English. If you want to add a translation of the interface, please
open an issue first so we can agree on the approach — string resources are not
in place yet.

## Code style

- Kotlin, no wildcard imports
- Compose only for UI, `AppState` holds the state
- No comments unless they explain something that the code cannot

## Signing

Never commit `keystore.properties`, `*.jks` or `*.keystore`. The release signing
key for the published APKs is not shared; F-Droid builds and signs its own copy
from source.