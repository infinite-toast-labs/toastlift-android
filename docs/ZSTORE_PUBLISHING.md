# ToastLift private Zstore release

Zstore receives its own Android variant rather than repackaging `debug`,
`staging`, or Play `release`. The result is a debuggable universal APK with the
production-v1 feature configuration, no embedded AI credentials, no AppReveal
server, and no network permission. Its package is
`dev.toastlabs.toastlift.zstore`, so a Zstore install can coexist with both local
development and a future Google Play install.

## Build contract

```bash
make verify-zstore-candidate
```

The output is
`app/build/outputs/apk/zstore/app-zstore-unsigned.apk`. It must remain unsigned:
the private Zstore operator host owns a distinct, long-lived ToastLift key and
signs only after independently checking package, version, permissions, digest,
and provenance. Never reuse an Android debug, staging, Play upload, or Play
app-signing key.

Every update advances `version.txt`. ToastLift's normal mapping remains the
store's ordering rule: `major * 1,000,000 + minor * 1,000 + patch`. A published
package/versionCode pair is immutable.

## Candidate handoff

After this workflow exists on the default branch, run **Build private Zstore
candidate** with a full immutable commit SHA. The workflow runs only while this
repository is private, receives no signing configuration, and retains the
unsigned APK, checksum, and JSON provenance for seven days. The trusted Zstore
host verifies that bundle without executing source from it, signs with the
managed per-app key, and creates a draft release.

Promotion is deliberately staged: draft, then canary, device verification, and
only then stable. The first managed install may require uninstalling an older
ad-hoc package because backward signer compatibility is intentionally not part of
this first release. Once the Zstore signer is pinned, preserve it for all future
in-place updates and keep its encrypted off-container backup current.
