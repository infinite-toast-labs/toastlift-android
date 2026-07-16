# ToastLift Play Store Launch

## Artifact modes

| Mode | Build command | What it is for |
| --- | --- | --- |
| Debug | `make install-device-debug` | Full development product with AppReveal and every feature enabled. |
| Staging | `make install-device-stage` | Locally debug-signed or CI-signed with the dedicated staging key, with the production feature configuration. It includes the debug-only AppReveal localhost server for capture/review and installs as `dev.toastlabs.toastlift.staging`, alongside debug. |
| Production | **Promote production AAB** workflow, or local `make verify-play-release` | Release build with production configuration. Upload the workflow's signed `.aab`, not an APK, to Play Console. |

The production feature surface is in `app/src/main/assets/feature-config.production.json`.
The debug version uses `app/src/debug/assets/feature-config.debug.json` and deliberately retains the full app.
Use `make mcp-stage-screens-all` to capture the production-configured staging surface on the emulator; it does not change the Play release's permissions or dependencies.

For upload-ready screenshots from a local Mac with Android Studio installed,
use the dedicated Play AVD workflow instead of the sandbox bridge:

```bash
make playstore-screenshots-phone
make playstore-screenshots-tablets
```

The targets build the production-configured staging APK, cold-boot dedicated
API 36.1 AVDs with fixed display geometry, capture exactly two deterministic
AppReveal routes, validate Play-compatible JPEGs under the committed
`play-store/listing/en-US/screenshots/` tree, keep raw logs under ignored
`artifacts/playstore/`, and stop the AVDs. While tuning a capture, keep the
dedicated AVDs warm:

```bash
PLAYSTORE_KEEP_EMULATOR=1 make playstore-screenshots-phone
PLAYSTORE_KEEP_EMULATOR=1 make playstore-screenshots-tablets
```

## Signing

The preferred path is the manually approved **Promote production AAB** GitHub
workflow. It retrieves the Play upload key through AWS OIDC, signs and verifies
the bundle, attaches the AAB/checksum/provenance to an immutable GitHub Release,
and deletes its temporary signing material. It does not publish to Google Play.

For a local recovery build, first prepare the pinned composite dependency and
use a standard JDK 17 or 21 plus a configured Android SDK:

```bash
make prepare-appreveal
```

Set `ANDROID_HOME`/`ANDROID_SDK_ROOT` or `sdk.dir` in ignored
`local.properties`. Avoid unsupported JDK distributions if Android's
`JdkImageTransform`/`jlink` step fails; the Android Studio JBR is a supported
local fallback.

Do not commit a keystore or its passwords. Provide all four signing values as
environment variables, Gradle properties, or entries in the ignored root
`.env`:

```bash
export TOASTLIFT_PLAY_UPLOAD_STORE_FILE=/absolute/path/to/toastlift-upload.keystore
export TOASTLIFT_PLAY_UPLOAD_STORE_PASSWORD='…'
export TOASTLIFT_PLAY_UPLOAD_KEY_ALIAS='…'
export TOASTLIFT_PLAY_UPLOAD_KEY_PASSWORD='…'
make verify-play-release
```

In `.env`, use the same four `NAME=value` entries without `export`. Relative
keystore paths are resolved from the Android repository root.

`make verify-play-release` fails if the generated bundle is unsigned or invalidly
signed, including bundles with unsigned entries. Self-signed Play upload
certificates are valid; certificate-chain trust warnings alone do not make the
bundle unsigned. The target also verifies that the merged Play release manifest
does not declare `android.permission.INTERNET`.

## Play Console checklist

- Run and retain the scorecard in `docs/INTERNAL_TESTER_RUBRIC.md`: staging must
  pass first, followed by the Play-delivered internal-testing gate on a physical
  phone.
- Host the reviewed privacy policy at a public, non-geofenced URL, then add that URL both to Play Console and the in-app Privacy section.
- Complete the Data safety form using the behavior of the production artifact. This release keeps workout data on-device and removes network access; re-check every third-party dependency before declaring that no data is collected or shared.
- Complete the content rating questionnaire and the ads declaration (`No ads` if that remains true).
- Verify the Play developer identity/account, store-listing contact email, app name, icon, screenshots, and required policy declarations.
- Upload the signed `app/build/outputs/bundle/release/app-release.aab` to an internal testing track first, then install the Play-delivered build on a real device before production rollout.
- On Play's preview screen, a missing deobfuscation file is expected while
  `isMinifyEnabled = false`. Native debug symbols are recommended for crash
  analysis but are not a signing or upload failure.
- Keep the Android target SDK current. The project currently targets API 36;
  verify the applicable Play requirement again immediately before upload.

## Production privacy promise to verify

The first production configuration intentionally disables AI, accounts, ads,
cloud backup, and cloud sync. It stores workout data locally and provides
export/delete controls. The release manifest removes `INTERNET`, so the Play
artifact cannot make network connections itself. The staging build retains that
permission only for its debug-only, localhost AppReveal capture server; it is
not part of the Play artifact. Re-audit the final manifest, runtime behavior,
and added SDKs before changing this claim. `POST_NOTIFICATIONS` remains declared
and must be reflected accurately in the release review.
