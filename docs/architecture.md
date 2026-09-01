# Architecture

## Responsibility map

OpenOnion Messages owns Android SMS receipt, the local inbox, explicit Agent
pairing, on-device encryption, the durable ciphertext queue, human-initiated
local sending, and the visible connection state.

`oo-api` owns short-lived pairing authorization, revocable device credentials,
ciphertext persistence, mailbox ordering, idempotency, acknowledgement state,
and access control by recipient Agent address. Its SMS schema has no sender or
body column.

ConnectOnion owns Agent identity, private-key access, decryption inside the
Agent runtime, and Agent-facing tools including `get_sms()` and
`wait_for_sms()`.

## Pairing

1. An authenticated Agent creates a random, one-time pairing token valid for
   60–1,800 seconds.
2. The owner pastes its `openonion://sms/pair` link into the Android app.
3. `oo-api` atomically consumes the token and returns a revocable device bearer
   credential bound to exactly one Agent address.
4. Android encrypts that credential at rest with an AES-256-GCM key held by
   Android Keystore.

Only token hashes are stored by `oo-api`. Disconnecting from the app revokes the
current device before removing the local credential. The Agent can also list
and revoke devices independently.

## Message flow

1. Android delivers a multipart SMS to the role holder.
2. The app reconstructs the sender and body and writes the message to Android's
   system SMS provider.
3. If an Agent is paired, the app creates the versioned JSON payload and seals
   it to the Agent's Ed25519-derived X25519 public key.
4. Only the recipient address, protocol fields, random client message ID, and
   ciphertext enter the Room delivery queue.
5. WorkManager uploads the envelope with the device bearer credential.
6. `oo-api` authenticates the device, checks that the recipient matches its
   binding, and stores the ciphertext idempotently.
7. ConnectOnion authenticates as the recipient Agent, fetches envelopes, and
   decrypts them locally with the project's private identity.
8. An acknowledgement is written only after successful decryption.

## Delivery behavior

Delivery is at-least-once. A random UUID remains stable across phone retries,
and the server enforces uniqueness on `(device_id, client_message_id)`. A retry
with different ciphertext for the same identifier is rejected.

Network failures and HTTP 429/5xx responses retry with WorkManager backoff.
Invalid or revoked credentials fail closed. Pairing is prospective: messages
received before pairing are not bulk-uploaded.

## Metadata boundary

The server necessarily learns the recipient Agent address, paired device ID
and label, app version, ciphertext size, ingestion time, and acknowledgement
time. Sender, body, SIM subscription, and device receipt time remain encrypted.

## Versioned exclusions

v1 excludes MMS, RCS, remote Agent sending, multi-Agent fan-out, shared-inbox
grants, key recovery, and OTP-specific automation. Each requires a separate
security and protocol decision.
