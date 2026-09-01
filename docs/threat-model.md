# Threat Model

## Protected assets

- SMS sender, body, receipt time, and SIM subscription;
- Agent private keys;
- device bearer credentials and one-time pairing links;
- the binding between a phone and an Agent address; and
- mailbox availability, ordering, and acknowledgement state.

## Intended protections

The design protects SMS content from a passive network observer, a database
reader, accidental server-side plaintext logging, and another authenticated
`oo-api` user. An unpaired or revoked device cannot write to an Agent mailbox,
and a paired device cannot route ciphertext to a different Agent.

Pairing credentials are encrypted by Android Keystore, `oo-api` stores only
token hashes, network security disables cleartext traffic in release builds,
and the server schema contains no SMS plaintext fields.

## Explicit non-goals

v1 does not protect plaintext from:

- malware or a privileged attacker on the Android device;
- compromise of the target Agent runtime or private key;
- a model provider after the Agent intentionally sends it plaintext;
- the carrier or original SMS sender; or
- a user who intentionally exports or shares a message.

It does not hide routing metadata, guarantee cellular delivery, or turn SMS
into a phishing-resistant authentication factor.

## Agent safety boundary

Every decrypted message is returned with `trusted: false`. Sender fields can be
spoofed, and bodies can contain prompt injection. Agent policies must treat the
message as input data, require separate authorization for consequential
actions, and must not interpret SMS text as authority.

## Abuse resistance

- Pairing is one-time, expiring, and owner initiated.
- The active Agent address remains visible in the app.
- The phone can revoke itself; the Agent can list and revoke every device.
- Device credentials are scoped only to ciphertext upload for one recipient.
- Upload size and protocol values are bounded; client message IDs are
  idempotent.
- Notifications use Android's private lock-screen visibility.
- No analytics or advertising SDK receives SMS-derived data.

## Known limitations

- The protocol has cross-language tests but no independent cryptographic audit.
- `oo-api` can delete, delay, reorder, or withhold ciphertext even though it
  cannot decrypt it.
- A stolen unlocked phone may expose the local Android SMS provider.
- v1 does not parse or preserve MMS. Owners who depend on MMS should not use it
  as their default handler yet.
- Server ciphertext retention currently follows the `oo-api` account policy;
  acknowledgement is not deletion.
