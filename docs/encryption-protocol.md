# SMS Encryption Protocol v1

This document freezes the interoperable v1 wire format implemented by
OpenOnion Messages, `oo-api`, and ConnectOnion. It has not received an
independent cryptographic audit.

## Identity and construction

A ConnectOnion address is `0x` followed by the hexadecimal encoding of a
32-byte Ed25519 public key. Android converts that key to X25519 with libsodium
and encrypts the UTF-8 payload using `crypto_box_seal`.

The algorithm identifier is:

```text
x25519-xsalsa20-poly1305-sealed-box
```

The corresponding Agent runtime converts its Ed25519 private key to Curve25519
and opens the sealed box. Sealed boxes provide confidentiality to the recipient
and integrity of the ciphertext, but not cryptographic sender identity. Device
authorization is provided separately by the revocable bearer credential on the
TLS upload.

## Plaintext payload

Kotlin serialization emits compact UTF-8 JSON with defaults and explicit nulls:

```json
{"schema":1,"sender":"+61412345678","body":"Your code is 654321","received_at":"2026-09-01T02:00:00Z","subscription_id":1}
```

- `schema`: integer `1`;
- `sender`: the Android display originating address, without additional trust;
- `body`: multipart display bodies concatenated in broadcast order;
- `received_at`: `Instant` UTC representation derived from SMS timestamp;
- `subscription_id`: Android subscription ID, or JSON `null` when unavailable.

## Server-visible envelope

```json
{
  "version": 1,
  "recipient": "0x…",
  "message_id": "5f5a5a31-0b0a-4e78-88c6-585975dfbf5c",
  "algorithm": "x25519-xsalsa20-poly1305-sealed-box",
  "ciphertext": "base64…"
}
```

`ciphertext` is canonical Base64 without line breaks. Decoded size must be
between 48 bytes (the sealed-box overhead) and 65,536 bytes. The device ID is
derived from the bearer credential, not accepted from the request body.

## Authentication and transport

- Pairing v2 uses an Agent-signed 256-bit random challenge, a Keystore device
  signature, and owner comparison before activation.
- Pairing claim and device tokens contain at least 256 bits of randomness.
- `oo-api` persists SHA-256 nonce/token hashes, never raw values.
- Pairing challenges are single-use and expire in at most 30 minutes.
- Uploads require TLS and `Authorization: Bearer sms_dev_…`.
- The server rejects an envelope whose recipient differs from the device
  binding.
- Agent reads require the existing ConnectOnion Agent authentication token.

## Test vectors

[`protocol/test-vectors/sms_protocol_v1.json`](../protocol/test-vectors/sms_protocol_v1.json)
contains a deterministic Ed25519 seed, public address, converted X25519 key,
exact plaintext bytes, and a fixed Python/PyNaCl ciphertext. Android instrumented
tests decrypt that vector and prove Kotlin-produced ciphertext decrypts with the
same Agent private key. Sealed-box encryption itself is randomized, so newly
created ciphertext is not byte-for-byte deterministic.

Any change to the algorithm string, JSON encoding, fields, size limits, address
format, or key conversion requires a new protocol version and fresh vectors.

## Meaning of end-to-end encryption

The cryptographic endpoints are the Android app and the target ConnectOnion
runtime. `oo-api` has no decryption key. If the runtime later supplies plaintext
to a model provider or another tool, that provider becomes a separate data
processor; E2EE does not extend beyond the decrypting runtime.

Pairing authentication and message encryption are separate protocols. See
[SMS Pairing Protocol v2](pairing-security.md) for the signed QR and device-key
confirmation design.
