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

## Versioning

Stable tags use semantic versions such as `v1.0.0`. Update `versionCode`,
`versionName`, and `CHANGELOG.md` together. Protocol changes are versioned
independently and must preserve old readers or introduce a new wire version.

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

The GitHub release workflow expects these repository secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

Pushing a stable tag such as `v1.0.0` then validates the tag against the app
version, builds and verifies the signed APK/AAB, writes SHA-256 checksums, and
publishes all three files to the GitHub release.

## Release contents

- signed APK and SHA-256 checksum;
- source tag and generated GitHub source archives;
- release notes including security/privacy changes and known limitations;
- passing Android, oo-api, and ConnectOnion contract tests; and
- deployed compatible `oo-api` schema/routes plus a ConnectOnion version that
  exports the SMS tools.
