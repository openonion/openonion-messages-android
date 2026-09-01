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

Known limitation: MMS and RCS are not supported in v1.
