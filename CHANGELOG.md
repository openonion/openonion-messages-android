# Changelog

## 1.1.0 — 2026-11-03

- Stop replacing the phone's texting app. The app now forwards alongside the
  default SMS app, so sending, contacts, MMS, group chats and RCS work as
  before. It asks for one permission, `RECEIVE_SMS`, and drops `READ_SMS`,
  `SEND_SMS` and notifications. The local inbox, composer and per-message
  delete are gone; the Agent inbox is the place to read and delete.
- Phones upgrading from 1.0.0 get a prompt to choose their usual SMS app again.
- When Android blocks SMS access for a sideloaded APK, the app explains how to
  allow restricted settings instead of failing silently.

- Pair by scanning the QR code that `co sms pair` prints, instead of pasting a
  link. Reads both light-on-dark and dark-on-light terminal codes.
- Connecting the Agent is now the first step; the six-digit check is a
  full-screen page with Cancel, and success leads straight into turning on
  SMS forwarding.

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
