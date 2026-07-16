# ToastLift feature-to-Play Store playbook

This is the working handoff for a human or coding agent taking ToastLift from a
new product idea to a Play Store release. Keep it current when the product,
build system, policy posture, or companion website changes. The checked-in code
and build configuration remain the source of truth if this document conflicts
with them.

## Start here

| Need | Source of truth |
| --- | --- |
| Product surface by build mode | `app/src/debug/assets/feature-config.debug.json` and `app/src/main/assets/feature-config.production.json` |
| Feature schema, loader, and safe fallbacks | `app/src/main/java/dev/toastlabs/toastlift/config/FeatureConfig.kt` |
| Android variants, package IDs, version, and signing | `app/build.gradle.kts` |
| Reproducible build, install, and capture commands | `Makefile` and `AGENTS.md` |
| Play Console checklist | `docs/PLAY_STORE_LAUNCH.md` |
| Privacy-policy wording to review | `docs/PRIVACY_POLICY_TEMPLATE.md` |
| Public-site source | `../toastlift-support-website` and [its private GitHub repository](https://github.com/infinite-toast-labs/toastlift-support-website) |
| Optional signing-material backup infrastructure | `iac-secrets/README.md` |

## Current v1 product contract

ToastLift's first Play Store release is a free, intentionally minimal workout
generator. It favors a small, coherent experience over exhaustive settings.

The production JSON currently keeps these core capabilities:

- Ad-hoc workout generation and editing.
- Training freshness.
- Workout history and the token system.
- Weekly muscle targets.
- Exercise library search, favorites, filters, and exercise detail/history.
- On-device data controls, including export/delete, plus the public privacy
  policy link.

The production JSON deliberately removes or disables:

- All AI features and their configuration UI.
- Workout plans/programs, saved templates, and the manual workout builder.
- Custom exercises.
- Exercise Family Tree / "Explore family".
- Advanced history dashboards, bounty cards, and most developer settings.

The product contract is encoded in
`app/src/main/assets/feature-config.production.json`, not in a remote feature
flag service. Do not re-enable a production feature just because its underlying
code still exists.

Current Android baseline is `version.txt`; `versionName` is its SemVer value and
`versionCode` is `major * 1_000_000 + minor * 1_000 + patch`.
`minSdk = 26`, `targetSdk = 36`. Android automatic backup is disabled with
`android:allowBackup="false"`.

Important privacy/build fact: the main manifest declares `INTERNET` for the
debug build, but the release source-set overlay removes it. The merged Play
release manifest therefore has no `INTERNET` permission, so the shipped app
cannot make network connections itself. Staging retains the permission only for
the debug-only AppReveal localhost capture server; that exception never ships to
Play. Production AI keys are blank and the product offers no cloud backup or
cloud sync. The release still declares `POST_NOTIFICATIONS`; audit that
permission and every added dependency before changing privacy claims.

## The three Android modes

| Mode | Purpose | Package / signing | Feature configuration | Main command |
| --- | --- | --- | --- | --- |
| Debug | Full development product and feature work. | `dev.toastlabs.toastlift.debug`; debug-signed. | `feature-config.debug.json`; full development surface, including AI and Exercise Family Tree. | `make install-device-debug` |
| Staging | Test the exact production product surface on an emulator or development phone. | `dev.toastlabs.toastlift.staging`; locally debug-signed and CI-signed by the dedicated staging key, so it can coexist with debug. | `feature-config.production.json`; AI BuildConfig values are blank. AppReveal remains available for visual review. | `make install-device-stage` |
| Release / production | The artifact uploaded to Google Play. | `dev.toastlabs.toastlift`; release-signed only when all signing values are present. | `feature-config.production.json`; AI BuildConfig values are blank and the AppReveal no-op dependency is used. | `make verify-play-release` |

Staging intentionally inherits the debug build type so it is debuggable and can
use AppReveal. Therefore, **never use `BuildConfig.DEBUG` to decide whether a
user-facing feature belongs in production**. Use `AppFeatureConfig`; staging
must behave like release with regard to product capabilities.

## Feature configuration rules

The feature model is organized by navigation, global capabilities, and screens:

```text
navigation
global
screens.home
screens.generate
screens.explore.library
screens.explore.history
screens.profile
```

`FeatureConfigLoader` reads the asset selected by the build type. Its production
fallbacks are fail-closed for the features intentionally removed from v1: a
missing or malformed production asset must not accidentally enable AI, programs,
templates, custom exercises, or Exercise Family Tree.

### Adding or changing a feature

For every new product idea, follow this sequence:

1. State the user value and whether it is part of the minimal free v1 surface.
   Prefer removing an option over adding a setting when the default can be
   opinionated.
2. Add a clearly named field to the appropriate `AppFeatureConfig` section in
   `FeatureConfig.kt`. Add loader support and a safe production default.
3. Add the field to both JSON files. Keep debug explicit; set production to the
   intended value. A new capability is not implicitly production-ready.
4. Gate every entry point, not only the most visible button:
   - navigation and screen sections;
   - menus, cards, sheets, and detail actions;
   - ViewModel/service actions and deep/debug routes.
5. Add or update parser/default tests in
   `app/src/test/java/dev/toastlabs/toastlift/config/FeatureConfigTest.kt`.
6. Build and test all three variants. Use staging for a production-surface visual
   review before changing a release decision.
7. If the feature changes data handling, permissions, support expectations, or
   screenshots, update the companion website, in-app privacy link, and Play
   Console declarations in the same change.

Exercise Family Tree is the reference example: it is `true` in debug and
`false` in production. The UI hides all "Explore family" affordances in
staging/release, and the ViewModel rejects a direct attempt to open the sheet.
This defense in depth is the expected pattern for features removed from v1.

There are no remote production flags for this release. Changing the product
surface requires a new staged/release build and a Play rollout.

## Build, install, and validate

All commands below run from this Android repository.

### Everyday development

```bash
make test                    # Debug unit tests
make lint                    # Debug lint
make build-debug             # app/build/outputs/apk/debug/app-debug.apk
make install-device-debug    # Build and install on the first physical device
```

Set `DEVICE_SERIAL=<serial>` only when the automatic physical-device selection
is not correct. `make devices` lists devices through the configured ADB bridge.

### Production-surface testing

```bash
make build-stage
make install-device-stage

# Stronger variant coverage before a release:
./gradlew --no-daemon --console=plain \
  testDebugUnitTest testStagingUnitTest testReleaseUnitTest lintRelease
```

The staging APK is
`app/build/outputs/apk/staging/app-staging.apk`. It is a separate application,
so installing it does not replace the full debug build.

### AppReveal screenshots and visual QA

Use the Make targets rather than raw `adb screencap` for an AppReveal capture:

```bash
make mcp-screens-all            # Full debug surface: regular screens and sheets
make mcp-stage-screens-all      # Production-configured staging surface
make mcp-full-scroll-screen SCREEN_KEY=sheet.exercise_history

# Physical-device variants when needed:
make mcp-phone-screens-all
make mcp-phone-full-scroll-all
```

The capture workflow builds/installs a fresh app, discovers the dynamic AppReveal
session from logcat, and writes results under `android-e2e/<timestamp>-...`.
Before accepting a run, check `results.tsv` for failures,
`debug_end.json` for `realStateUnchanged=true`, and `contact-sheet.png` when it
exists. `android-e2e/` is generated local output and must not be committed.

Use staging screenshots to review the Play surface and to refresh the public
site's screenshots when a visible production experience changes. The website
currently stores them in `public/screenshots/`.

For final Google Play uploads from a local Mac, use the host-native dedicated
AVD targets. They intentionally do not use or modify the container-oriented
emulator bridge targets:

```bash
make playstore-screenshots-phone
make playstore-screenshots-tablets
```

Each device class captures exactly two production-configured staging routes
into the committed `play-store/listing/en-US/screenshots/` tree, with fixed
resolution, density, emulator port, locale/time zone, disabled animations,
file-size checks, and alt-text metadata. Raw AppReveal responses and logs remain
under ignored `artifacts/playstore/`.
The polished default stops each dedicated AVD after capture. During visual
iteration, set `PLAYSTORE_KEEP_EMULATOR=1` to reuse the already-booted AVD and
avoid repeated cold boots.

### Produce the Play artifact

Prefer the manually approved **Promote production AAB** GitHub workflow after
the same commit has a successful staging deployment. It restores the Play
upload signer through AWS OIDC, runs release checks, creates the immutable
version tag/GitHub Release, attaches the signed AAB/checksum/provenance, and
removes temporary signing material. It deliberately stops short of Google Play;
upload that released AAB to the internal track manually.

For a local recovery build, prepare the pinned AppReveal checkout first:

```bash
make prepare-appreveal
```

Use a standard JDK 17 or 21 and configure the Android SDK through
`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or ignored `local.properties`. Then set
signing values only in the ignored Android-root `.env`, the environment, or
Gradle properties. Never commit, print, paste into source, or pass real signing
passwords as command-line arguments.

```bash
export TOASTLIFT_PLAY_UPLOAD_STORE_FILE=/absolute/path/to/toastlift-upload.keystore
export TOASTLIFT_PLAY_UPLOAD_STORE_PASSWORD='...'
export TOASTLIFT_PLAY_UPLOAD_KEY_ALIAS='...'
export TOASTLIFT_PLAY_UPLOAD_KEY_PASSWORD='...'

make verify-play-release
```

The ignored `keystore/`, `*.jks`, and `*.keystore` paths are intended for local
signing material. `make verify-play-release` builds the release AAB and verifies
its signature. Upload only this output to Play Console:

```text
app/build/outputs/bundle/release/app-release.aab
```

`make build-release` alone can produce an unsigned release APK if signing is
not configured; it is not the Play upload artifact. Always use
`make verify-play-release` as the final local artifact gate. It requires a real
JAR signature block, rejects unsigned or partially unsigned bundles, accepts a
valid self-signed Play upload certificate, and verifies that the merged release
manifest has no `INTERNET` permission.

## Signing-material backup

`iac-secrets/` is a TypeScript CDK app that backs up the Play upload key. Its
current physical secret name, `/toastlift/android/release-signing`, is a
legacy-compatible storage name; it does not contain the Play app-signing key.

```bash
cd iac-secrets
./scripts/deploy.sh
AWS_REGION=us-east-1 ./scripts/backup-play-upload-signing.sh
```

The backup script reads the ignored Android `.env` and keystore, never writes a
local export, and never prints secret material. It needs authenticated AWS
credentials with permission to deploy the stack and update the secret. The local
AWS CLI is configured with `us-east-1` and JSON as defaults, but do not assume
the stack or real secret is deployed: verify it with the authenticated AWS
account before relying on this as a recovery path.

## Companion website and support

The website is a separate private repository:

- Local sibling: `../toastlift-support-website`
- Remote: [infinite-toast-labs/toastlift-support-website](https://github.com/infinite-toast-labs/toastlift-support-website)
- Initial commit: `70f6227 feat(site): launch company and product pages`
- Serving model: Cloudflare Worker plus static public assets.

Its public information architecture is intentional:

| URL | Purpose |
| --- | --- |
| `https://www.toastlabs.dev/` | ToastLabs company/portfolio landing page |
| `https://www.toastlabs.dev/toastlift/` | ToastLift product page and screenshots |
| `https://www.toastlabs.dev/toastlift/support/` | Support information |
| `https://www.toastlabs.dev/toastlift/privacy/` | Public privacy policy |
| `https://www.toastlabs.dev/toastlift/contact/` | Contact form |

The Android Profile privacy section opens the public privacy-policy URL. Keep
the URL and the policy text aligned with the released artifact. The website
contact form is a Worker endpoint with fixed-recipient delivery, a honeypot,
timing/same-origin checks, field limits, and a per-IP-hash rate limit. Its
deployment secrets and email routing are website-repository responsibilities;
read that repository's `README.md` before modifying or deploying it. Never put
Cloudflare tokens, support addresses, or routing credentials in the Android
repository.

## Play Store release procedure

1. **Freeze the intended product surface.** Review every production feature flag
   and verify that the required core features—training freshness, tokens, weekly
   muscle targets, and ad-hoc generation—remain enabled.
2. **Advance the version.** Merge the reviewed Release Please PR that updates
   `version.txt` and `CHANGELOG.md`; Gradle derives the monotonically increasing
   `versionCode` and user-facing `versionName` from that one SemVer source.
3. **Review policy and permissions.** Inspect the merged release manifest and
   dependencies. Confirm that `INTERNET` remains absent, then reconcile
   notification behavior, local storage, export/delete behavior, and any new
   SDK with the privacy policy and Google Play Data safety answers. The first
   release must make no cloud-backup claim.
4. **Validate code.** Run the three-variant unit-test/lint command above. Resolve
   new lint errors; existing non-fatal compiler deprecation warnings do not
   substitute for reviewing lint output.
5. **Validate the product.** Install staging on a physical phone. Exercise
   onboarding, generator, a full workout lifecycle, history, training freshness,
   token behavior, weekly muscle targets, export/delete, and the privacy-policy
   link. Confirm removed features cannot be reached from menus or debug routes.
6. **Capture visual evidence.** Run the staging AppReveal capture, inspect the
   generated reports/contact sheet, and update website screenshots if the public
   product page would otherwise be misleading.
7. **Build the signed bundle.** Prefer **Promote production AAB** for the staged
   commit and use the AAB/checksum/provenance attached to its immutable GitHub
   Release. For local recovery, run `make prepare-appreveal` followed by
   `make verify-play-release`. Never record signing passwords.
8. **Complete Play Console.** Use `docs/PLAY_STORE_LAUNCH.md`: privacy policy
   URL, Data safety, content rating, ads declaration, listing metadata, and
   screenshots. Upload the signed AAB to an internal testing track first.
9. **Test the Play-delivered build.** Install it from the internal track on a
   real device before a production rollout. This catches differences between a
   locally installed APK and Play's delivery/signing path.
10. **Publish in a controlled rollout.** Monitor user support, crashes/ANRs,
    listing feedback, and the contact channel. Do not change policy claims or
    public screenshots independently of the artifact they describe.

## Agent handoff checklist

When an agent receives a new feature request, it should first answer these
questions before changing code:

1. Is it debug-only, staging/release, or a later product release?
2. Which feature-config flag owns it, and what is its safe production default?
3. Which user-visible paths and non-UI paths invoke it?
4. Does it alter data collection, permissions, network use, support, privacy,
   screenshots, or Play declarations?
5. What staging scenario demonstrates that the production surface is correct?
6. What tests, lint, and signed-bundle checks prove it is safe to upload?

Use small, reversible, config-gated changes. Preserve the existing dirty
worktree unless the request explicitly covers those files, and never add
generated `android-e2e/`, a keystore, `.env`, or secret backups to git.
