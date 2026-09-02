# Releasing

## Verification

Use JDK 17 and Android SDK 36:

```bash
./gradlew test lint assembleRelease
./gradlew connectedDebugAndroidTest
```

The connected suite requires an API 26+ emulator or device. It verifies both
cross-language cryptographic vectors and an encrypted queue upload through the
real WorkManager worker.

Before publishing the first release, or changing encryption, pairing, or
deletion behavior, also run the separate
[real backend lifecycle gate](end-to-end-testing.md). The ordinary connected
suite intentionally skips the three `RealBackendSyncInstrumentedTest` methods
when backend arguments are absent; a green Android job alone is not proof of
Android → PostgreSQL → Agent synchronization.

```bash
gh workflow run sms-android-e2e.yml --repo openonion/oo-api --ref main
```

Check the checkout SHAs in that job before using its results: it checks out
the current default branches of the Android and ConnectOnion repositories.
Record the exact Android candidate, successful Android CI, lifecycle gate, and
release preflight runs in the version's verification report. The first-release
report is [1.0.0 verification](releases/1.0.0-verification.md).

## Versioning

Stable tags use semantic versions such as `v1.0.0`. Update `versionCode`,
`versionName`, and `CHANGELOG.md` together. Protocol changes are versioned
independently and must preserve old readers or introduce a new wire version.
Every stable version also requires curated notes at
`docs/releases/<version>.md`. The release workflow rejects tags that are not
reachable from `main`, do not match the Android version, or lack either the
changelog entry or curated notes.

## Signing

The repository never stores keystores or passwords. Configure release signing
through a local `keystore.properties` file or CI secrets, sign the minified
release APK/AAB with the durable OpenOnion Android release key, and verify it:

```bash
apksigner verify --verbose --print-certs OpenOnion-Messages-v1.0.0.apk
sha256sum OpenOnion-Messages-v1.0.0.apk
```

Back up the release key in the organization's credential system before
publishing. Never replace it silently: Android treats a new key as a different
publisher.

Before configuring the durable key, run the `Release` workflow manually from
`main`. Manual runs create a one-day ephemeral CI-only key, exercise the same
minified APK/AAB build and metadata/signature checks, then discard the key and
publish nothing. A manual run can never create a GitHub Release; only a pushed
stable tag can restore the repository signing secrets and publish artifacts.

The GitHub release workflow expects these repository secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

Pushing a stable tag such as `v1.0.0` then validates the tag against the app
version, builds and verifies the signed APK/AAB, inspects the final APK package
metadata, writes SHA-256 checksums, creates GitHub/Sigstore build-provenance
attestations, and publishes the verified assets to the GitHub release.

After downloading a release, verify both its checksum and its origin:

```bash
sha256sum --check OpenOnion-Messages-v1.0.0-SHA256SUMS.txt
gh attestation verify OpenOnion-Messages-v1.0.0.apk \
  --repo openonion/openonion-messages-android
```

## Release contents

- signed APK and AAB plus their SHA-256 checksum file;
- Apache-2.0 `LICENSE` and project `NOTICE`;
- source tag and generated GitHub source archives;
- release notes including security/privacy changes and known limitations;
- passing Android, oo-api, and ConnectOnion contract tests; and
- deployed compatible `oo-api` schema/routes plus a ConnectOnion version that
  exports the SMS tools.
