# ToastLift CI/CD and release promotion

Development CI runs on pull requests and `main`. It validates the Gradle
wrapper, requires a Conventional Commit-style PR title, runs debug unit tests
and lint, and assembles a transient debug APK. It has no `.env`, AWS identity,
signing material, or APK upload.

`ci/android-ci.env` is the non-secret build contract: module/toolchain values,
the full pinned AppReveal commit, variant task sets, artifacts, and retention.
Local builds continue to use `../appreveal-toastlift/Android`; CI sets
`APPREVEAL_COMPOSITE_BUILD` to the checked-out public AppReveal revision.

Release Please only creates or updates a version/changelog PR on `main`. It
does not create a tag or GitHub Release. The `v1.0.0` entry is the reviewed
baseline; subsequent version changes come from merged Release Please PRs.

## Promotion

Run **Promote staging APK** with a full 40-character SHA reachable from `main`.
After the `staging` environment approval, it retrieves only the staging signing
secret using OIDC, runs staging and release checks, and uploads the signed APK,
checksum, and provenance manifest for 30 days.

Run **Promote production AAB** with the identical SHA only after that staging
environment deployment reports success. After `production` approval it runs the
release checks and `make verify-play-release`, creates an immutable `v<SemVer>`
tag and public GitHub Release, then attaches the signed AAB, checksum, and
provenance. It does not upload to Play; upload the released AAB to the internal
track manually and follow the production playbook.

## One-time administrator setup

1. Deploy `iac-secrets` and create/backup the dedicated staging signer as
   described in its README.
2. Set the three non-secret repository variables described there.
3. Make both Android and AppReveal source repositories public. Protect `main`
   with PR-only changes and required `Development CI` checks.
4. Configure `staging` and `production` environments to allow only `main` and
   require the repository owner. Leave self-review prevention disabled for the
   solo-maintainer policy.

No signing key, password, AWS access key, or LLM credential belongs in GitHub.
