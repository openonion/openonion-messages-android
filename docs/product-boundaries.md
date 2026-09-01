# Product Boundaries

OpenOnion Messages is an Agent-readable SMS inbox, not an authentication
product. Its job is to receive ordinary SMS on a phone, keep the owner-visible
Android inbox useful, and deliver prospective messages privately to one chosen
Agent.

## Included in v1

- receive, display, and notify for SMS;
- human-initiated outbound SMS required of a usable default handler;
- pair one Agent and show its address;
- encrypt new inbound SMS on-device;
- retry ciphertext delivery and let either endpoint revoke the device; and
- expose decrypted messages as explicitly untrusted Agent data.

## Excluded from v1

- MMS and RCS;
- remote or autonomous Agent sending;
- contact and call-log access;
- retroactive upload of the existing inbox;
- OTP extraction, login orchestration, or authentication claims;
- automatic actions triggered solely by SMS content;
- shared or multi-Agent inboxes; and
- a claim that encryption protects plaintext after the Agent hands it to an
  external model or tool.

These exclusions are product and safety commitments, not a backlog promise.
Each future expansion needs explicit user controls and a separate review.
