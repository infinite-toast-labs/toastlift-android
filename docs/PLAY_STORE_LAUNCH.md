# ToastLift Play Store Launch

## Artifact modes

| Mode | Build command | What it is for |
| --- | --- | --- |
| Debug | `make install-device-debug` | Full development product with AppReveal and every feature enabled. |
| Staging | `make install-device-stage` | Locally debug-signed or CI-signed with the dedicated staging key, with the production feature configuration. It includes the debug-only AppReveal localhost server for capture/review and installs as `dev.toastlabs.toastlift.staging`, alongside debug. |
| Production | `make build-prod` / `make bundle-prod` | Release build with production configuration. Upload the signed `.aab`, not the APK, to Play Console. |

The production feature surface is in `app/src/main/assets/feature-config.production.json`.
The debug version uses `app/src/debug/assets/feature-config.debug.json` and deliberately retains the full app.
Use `make mcp-stage-screens-all` to capture the production-configured staging surface on the emulator; it does not change the Play release's permissions or dependencies.

## Signing

Do not commit a keystore or its passwords. Before creating the Play bundle, provide all four values as environment variables or Gradle properties:

```bash
export TOASTLIFT_RELEASE_STORE_FILE=/absolute/path/to/toastlift-upload.keystore
export TOASTLIFT_RELEASE_STORE_PASSWORD='…'
export TOASTLIFT_RELEASE_KEY_ALIAS='…'
export TOASTLIFT_RELEASE_KEY_PASSWORD='…'
make verify-play-release
```

`make verify-play-release` fails if the generated bundle is unsigned or invalidly
signed. It also verifies that the merged Play release manifest does not declare
`android.permission.INTERNET`.

## Play Console checklist

- Host the reviewed privacy policy at a public, non-geofenced URL, then add that URL both to Play Console and the in-app Privacy section.
- Complete the Data safety form using the behavior of the production artifact. This release keeps workout data on-device and removes network access; re-check every third-party dependency before declaring that no data is collected or shared.
- Complete the content rating questionnaire and the ads declaration (`No ads` if that remains true).
- Verify the Play developer identity/account, store-listing contact email, app name, icon, screenshots, and required policy declarations.
- Upload the signed `app/build/outputs/bundle/release/app-release.aab` to an internal testing track first, then install the Play-delivered build on a real device before production rollout.
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
