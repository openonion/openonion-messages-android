# Changelog

## 1.0.0 — 2026-09-01

- First stable OpenOnion Messages release.
- Added Android default SMS role, local inbox, notifications, and human sending.
- Added one-time Agent pairing and self-revocation.
- Added libsodium sealed-box E2EE and a Keystore-protected device credential.
- Added durable ciphertext queue, WorkManager retries, and idempotent delivery.
- Added owner-confirmed synchronized deletion from Android and the device-scoped
  server ciphertext inbox, including durable offline retries.
- Added oo-api ciphertext mailbox and ConnectOnion Agent tools.
- Added the green, white, and black OpenOnion visual system, accessible light
  and dark themes, and final launcher/brand assets.
- Added a hosted real Android → oo-api → PostgreSQL → ConnectOnion lifecycle
  gate that proves Agent decryption and database deletion.
- Published cross-language protocol vectors, privacy disclosure, threat model,
  and release documentation.
- Published the project under Apache-2.0 with a NOTICE, contributor code of
  conduct, support policy, and documented, attested GitHub downloads.
- Coordinated the Kotlin 2.3.21, Room 2.8.4, KSP 2.3.11, and
  kotlinx.serialization 1.11.0 maintenance updates so database schema tooling
  stays version-compatible.
- Updated the LazySodium Android wrapper to 5.2.0 and JNA native bridge to
  5.19.1 after cryptographic-vector and emulator verification.

Known limitation: MMS and RCS are not supported in v1.
