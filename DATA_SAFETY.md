# Data Safety Disclosure

This file is the source-of-truth summary for distribution forms. Store answers
must be rechecked against the exact signed build and current `oo-api` policy.

| Data | Collected/transmitted | Purpose | Protection |
|---|---:|---|---|
| SMS sender and body | Yes, only as ciphertext | Agent inbox | E2EE phone → Agent runtime |
| Agent address | Yes | Routing and account functionality | TLS, access control |
| Device name/platform/app version | Yes | Pairing, security, revocation | TLS, account scoped |
| Random message and device IDs | Yes | Idempotency and abuse prevention | TLS, account scoped |
| Contacts or call logs | No | — | — |
| Advertising identifiers | No | — | — |
| Precise location | No | — | — |

Data is not sold and is not used for advertising. SMS content can become
visible to a model provider only when the Agent runtime deliberately supplies
the decrypted plaintext to that provider.

Android permissions are documented in [docs/permissions.md](docs/permissions.md).
