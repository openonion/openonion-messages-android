# Security Policy

Security fixes are supported for the latest stable release.

Do not disclose a suspected vulnerability in a public issue. Email
`security@openonion.ai` with affected versions, impact, minimal reproduction
steps, and a safe contact method. Do not include real SMS content, private keys,
pairing links, device credentials, access tokens, or other personal data.

We will acknowledge reports as soon as practical, coordinate validation and a
fix, and credit reporters who request attribution after remediation.

The v1 cryptographic construction uses audited libsodium primitives and public
cross-language vectors, but the composed protocol has not received an
independent audit. See [Threat model](docs/threat-model.md).
