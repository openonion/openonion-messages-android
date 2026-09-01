# SMS Pairing Protocol v2

Pairing v2 connects one Android installation to one ConnectOnion Agent without
copying the Agent private key or recovery words to the phone. It has not yet
received an independent cryptographic audit.

## Security goals

- Android can verify that the QR challenge was authorized by the named Agent.
- The Agent owner can verify which Android device key is being approved.
- `oo-api` cannot substitute a recipient or device key without detection.
- A copied or raced QR code cannot become an active upload credential without
  the Agent's second, explicit approval.
- Long-lived device credentials remain revocable and are never stored in
  plaintext by `oo-api`.

## Protocol flow

```text
Agent / co CLI                    oo-api                       Android
     |                              |                             |
     | create nonce + pairing id    |                             |
     | sign grant with Ed25519      |                             |
     |---- grant, signature ------->| store SHA-256(nonce)        |
     |<------- signed QR link ------|                             |
     |                              |<---- scan signed QR --------|
     |                              |     verify Agent signature  |
     |                              |     create Keystore P-256   |
     |                              |<---- signed device claim ---|
     |                              |     (pending only)           |
     |<------ pending device key ---|---- six-digit code -------->|
     | compute same six digits      |                             |
     | owner compares both screens  |                             |
     | sign exact device key        |                             |
     |------ activation signature ->|                             |
     |                              |<---- poll with claim token --|
     |                              |---- upload-only token ------>|
```

There are two signatures because they answer different questions. The first
says, “this Agent authorizes this short-lived pairing challenge.” The second
says, “this Agent approves this exact Android public key after the owner
compared the screens.”

## Cryptographic values

The Agent creates a UUID, a uniformly random 32-byte nonce encoded as unpadded
Base64url, and an expiry no more than 1,800 seconds in the future. It signs the
UTF-8 bytes of compact JSON with sorted keys:

```json
{"expires_at":1788250600,"nonce":"AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8","pairing_id":"11111111-2222-4333-8444-555555555555","purpose":"openonion-sms-pair","recipient":"0x…","version":2}
```

The signature is Ed25519. The public key is already the 32-byte key encoded in
the `0x…` ConnectOnion address, so Android needs no additional certificate or
server-provided trust root.

Android creates a non-exportable P-256 signing key in Android Keystore. On
devices that support hardware-backed Keystore, Android may protect this key in
secure hardware; the protocol does not claim that every supported phone has
such hardware. Android signs compact, sorted-key JSON containing the complete
Agent grant and the DER SubjectPublicKeyInfo device key using ECDSA with
SHA-256. `oo-api` verifies both signatures before recording a pending claim.

The short authentication string shown on both screens is:

```text
SHA-256(grant_utf8 || 0x00 || device_spki_der)[0:4] mod 1,000,000
```

It is displayed as six zero-padded decimal digits. This approximately 20-bit
value is a human comparison aid, not a replacement for the full signatures.
The one-time claim and short expiry bound online attempts. Security against an
active device substitution depends on the owner actually comparing the two
screens and refusing a mismatch.

After comparison, the Agent signs this activation statement with Ed25519:

```json
{"device_public_key":"base64 DER…","pairing_id":"uuid…","purpose":"openonion-sms-activate","recipient":"0x…","version":2}
```

Only then may the pending Android claim exchange its short-lived claim token
for an `sms_dev_…` credential. That credential authorizes ciphertext upload and
device-scoped deletion for one recipient only; it cannot read or decrypt SMS.

## Server knowledge and storage

`oo-api` necessarily observes the Agent address, pairing/device identifiers,
expiry, signatures, device public key, device label, app version, IP/network
metadata, and timing. It stores the SHA-256 digest of the nonce and bearer
tokens, not their plaintext values. Pairing metadata is not message content.

SMS sender, body, receipt time, and SIM subscription are encrypted separately
on Android with the [SMS encryption protocol](encryption-protocol.md). Pairing
authentication does not weaken that end-to-end encryption: the server still
has no Agent private decryption key.

## Failure and attack cases

- **QR theft or race:** an attacker may create a pending claim, but cannot
  activate it without the owner approving that attacker's device key. The
  intended phone sees a claim conflict; the owner should create a new QR.
- **Malicious server:** it can deny service, race requests, and expose metadata.
  It cannot forge either Agent signature or make two different device keys
  produce the same checked transcript except with the short-code collision
  probability.
- **Skipped comparison:** approving without comparing removes the human
  substitution check. The UI therefore keeps the code visible until approval
  or expiry.
- **App restart:** the pending claim token and code are AES-GCM encrypted with
  an Android Keystore key and polling resumes until the challenge expires.
- **Lost phone:** revoke its device from the Agent. Revocation prevents future
  uploads but cannot erase plaintext already present in Android's SMS provider.
- **Compromised endpoint:** malware with sufficient access to the phone or
  Agent runtime can read plaintext at that endpoint. E2EE cannot protect a
  compromised endpoint.

## Interoperability

`protocol/test-vectors/sms_pairing_v2.json` freezes the canonical bytes, Agent
signature, Android-style device signature, and confirmation code. Kotlin,
Python SDK, and backend tests consume the same vector. Any change to the
canonical JSON, purpose strings, key encodings, hash construction, or signature
algorithms requires a new protocol version.
