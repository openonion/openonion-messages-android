# Privacy

OpenOnion Messages handles highly sensitive communications. It is designed so
the storage service receives ciphertext rather than SMS content.

## Data on the phone

Android's system SMS provider stores the sender, body, timestamps, and read
state so the owner can use the local inbox. OpenOnion Messages also stores a
delivery queue containing only recipient Agent addresses, random message IDs,
ciphertext, retry state, and timestamps. The paired device credential is
encrypted with Android Keystore. App backups are disabled.

Queued ciphertext is deleted from the app database after `oo-api` confirms
durable storage. A small local receipt then associates the Android SMS row with
its server ciphertext identifier so an owner-confirmed deletion can be applied
to both copies. The receipt contains no sender or body.

## Data sent to oo-api

After explicit pairing, each new inbound SMS is encrypted on the phone. The
service receives the recipient Agent address, device metadata, protocol
version, random message ID, ciphertext, ciphertext size, ingestion time, and
acknowledgement state. It does not receive a plaintext sender or body from this
app. Transport uses HTTPS; release builds reject cleartext traffic.

Pairing sends the Android manufacturer/model and app version so the Agent owner
can identify and revoke a device. Raw pairing and device tokens are not stored
server-side; only cryptographic hashes are retained.

## Agent and model processing

ConnectOnion decrypts messages inside the target Agent runtime. If that runtime
sends the plaintext to an AI model, plugin, log, or another service, that party
can process it under its own policy. The app's end-to-end encryption ends at the
decrypting Agent runtime.

## Data not collected

The app contains no advertising or analytics SDK, reads no contacts or call
logs, and does not retroactively upload an existing inbox. It does not upload
outbound SMS in v1.

## Control and deletion

Deleting a message in Android asks for confirmation, removes the local SMS, and
queues deletion of the corresponding device-scoped ciphertext from `oo-api`.
The remote operation retries safely until confirmed; the UI shows outstanding
encrypted changes. Messages that predate pairing or have not yet uploaded have
no server copy to delete.

The owner can disconnect in the app, which revokes the device credential and
removes pending ciphertext for that pairing. Pending remote deletions must
finish before disconnecting so the credential remains able to complete them.
The Agent can list and revoke any paired phone. Disconnecting does not delete
messages already stored in Android or ciphertext already accepted by `oo-api`;
the Agent can also delete individual server ciphertext records with
`delete_sms()` and account deletion follows the `oo-api` account policy.

Questions or deletion requests: `privacy@openonion.ai`.
