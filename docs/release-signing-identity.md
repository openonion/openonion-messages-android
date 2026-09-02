# Android release signing identity

This document records the non-secret identity and recovery procedure for the
OpenOnion Messages Android publisher. It intentionally contains no private key
or password.

## Decision

OpenOnion Messages uses a dedicated Android application-signing key. It is not
derived from a SLIP seed, ConnectOnion Agent identity, SMS encryption key, or
user recovery phrase.

Release signing has a different lifecycle and threat boundary from message
encryption. Keeping it independent means publishing infrastructure never needs
an Agent or user seed, and compromise or rotation of one system does not expose
the others. A future identity system may publish or attest the public
certificate fingerprint, but it must not derive this private signing key.

Android requires installed APKs and their updates to have a trusted signing
certificate. The same publisher identity therefore needs to remain available
for future updates. See Android's official
[app-signing documentation](https://developer.android.com/studio/publish/app-signing).

## Public identity

- Package: `ai.openonion.messages`
- Key alias: `openonion-messages-release`
- Key algorithm and size: RSA, 4096 bits
- Signature algorithm: SHA-256 with RSA
- Certificate subject: `CN=OpenOnion Messages, O=OpenOnion`
- Certificate validity: 2026-09-02 through 2054-01-18
- Certificate SHA-256:
  `54:2E:1A:5A:E3:4A:4F:51:14:A6:68:BF:2C:00:AF:3E:BB:69:2A:31:5A:05:72:66:B5:83:7D:90:47:3F:EB:A4`

The certificate and its fingerprint are public. The corresponding private key
and credentials are secret.

## Generation

The identity was generated on 2026-09-02 with JDK 17 `keytool` as a PKCS#12
keystore. `keytool` generated the RSA key pair; an independent 256-bit random
password was generated from the operating system's cryptographic random source.
The certificate validity is 10,000 days, exceeding Android's recommended
minimum of 25 years.

The generation was a one-time operation. Do not run `keytool -genkeypair` again
for this package and do not silently replace the certificate fingerprint above.

## Local custody and recovery

The encrypted working copy is stored with owner-only permissions at:

```text
$HOME/Library/Application Support/OpenOnion/Signing/openonion-messages-release.p12
```

The macOS login Keychain contains the complete recovery record under:

```text
service: ai.openonion.messages.android-release.v1
account: openonion
```

That record contains the encrypted keystore and its credentials. Do not print,
copy into shell history, commit, email, or chat its value. Recovery should write
the keystore directly from the Keychain to the owner-only path above, verify
that its certificate SHA-256 matches this document, and remove any temporary
plaintext credential files immediately after signing.

If neither the encrypted working copy nor the Keychain record is available,
stop the release. Do not generate a replacement and present it as a compatible
update. If the key may be compromised, treat that as a security incident before
publishing anything.

## GitHub Actions

The repository uses these encrypted GitHub Actions Secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

Only the tag-triggered `Release` workflow restores them. It writes the keystore
to the runner's temporary directory, signs and verifies the APK/AAB, attests the
checksums, and publishes the release. Manual workflow runs use a disposable key
and cannot publish.

After every release, download the public APK and verify both the GitHub
attestation and the signing certificate. The certificate SHA-256 must match the
fingerprint in this document. A successful build alone is not sufficient.

If the app is later published through Google Play, review Play App Signing and
create a separate upload key where appropriate. Do not repurpose or upload the
private publisher key without a documented security decision.
