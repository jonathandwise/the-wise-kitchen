# The Wise Kitchen

Jonathan's cookbook as an Android phone app (Kotlin, Jetpack Compose), for reading recipes in large
text in the kitchen. Built from "The Wise Kitchen" cookbook PDF (in the claude.ai Cookbook project).
He installs it from GitHub releases; installed copies update themselves.

## Adding or changing a recipe (the common request)
1. Edit or add to the right chapter file in `cookbook/` (format: `cookbook/FORMAT.md`). Rewrite the
   recipe into the book's house style: ingredients, numbered steps, a few tips, `from:` source line,
   °F with °C alongside, plain wording.
2. Run `python3 tools/build_cookbook.py`. It validates the files, updates `data/recipes.json` and
   rebuilds `The Wise Kitchen.pdf` (the printable cookbook, via `tools/build_pdf.py`; needs
   `pip install reportlab pypdf`, fonts are in `tools/fonts`).
3. `git pull --rebase` first (the PDF workflow commits to `main`), then commit the `.txt`,
   `data/recipes.json` and the PDF, and push. The app downloads `data/recipes.json` from `main` on
   launch, so no app release is needed for recipe changes. If the PDF wasn't rebuilt, the
   "Cookbook PDF" workflow rebuilds and commits it (the PDF's keywords hold the data's contentHash).
4. Send Jonathan the new PDF and remind him the copy in the claude.ai project is a snapshot he can
   replace; the repo's PDF is the current one.

## Building
There is no Android SDK in Claude sessions (Google's Maven is blocked), so every build runs on GitHub
Actions on push to `main`. Results are on branches (fetch with `git fetch origin <branch>`):
- `builds`: the APK of the latest successful build.
- `test-results`: emulator screenshots, `report.txt`, `app-log.txt`, `logcat.txt`.
- `ci-logs`: the build log and test reports when the build fails.

## Releasing an app update (only for app changes, not recipes)
1. Make the change; push and check the build and emulator screenshots.
2. Bump `appVersion` in `app/build.gradle.kts` (semver).
3. Add a `## <version>` section at the top of `CHANGELOG.md` (short, plain bullets; shown in the app).
4. Push. The workflow publishes release `v<version>` with the APK and `latest.json` once the
   emulator test passes and the build is signed with the permanent key (repo secrets
   `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Never reuse a version.

## Notes
- The data has a `schema` number. Bump `Cookbook.SUPPORTED_SCHEMA` and `SCHEMA` in the build
  script together only for changes old apps can't read; old apps then ask for an app update.
- Launch-intent extras (`open`, `screen`, `query`, `theme`, `textScale`) are used by `ci/e2e.py`.
- He chose for v1: big adjustable text, light theme like the PDF with a dark option; no cook mode,
  timers, checkboxes or keep-screen-on (may want them later).
