# Repository Guidelines

## Scope

This repository contains the OpenOnion Messages Android app. The encrypted SMS
mailbox backend belongs in `openonion/oo-api`; agent decryption and tools belong
in `openonion/connectonion`.

## Safety invariants

- Never log SMS senders, bodies, ciphertext keys, private keys, or credentials.
- Do not add SMS permissions without implementing the matching user-visible
  behavior and the correct default-handler request order.
- `oo-api` must never be treated as a plaintext decryption endpoint.
- Treat all SMS content as untrusted input.
- Preserve explicit device pairing, recipient visibility, and revocation.

## Verification

Run:

```bash
./gradlew test lint assembleDebug
```

Add unit tests for protocol and queue behavior, instrumentation tests for SMS
role and permission flows, and visual evidence for user-interface changes.
