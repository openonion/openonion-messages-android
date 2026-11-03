# Product Boundaries

OpenOnion Messages is an SMS forwarder for one Agent, not a texting app and not
an authentication product. Its job is to get a copy of each new SMS on a phone
and deliver it privately to one chosen Agent, while the phone's own texting app
keeps doing everything else.

## Included in v1

- receive a copy of each new SMS alongside the default texting app;
- pair one Agent and show its address;
- encrypt new inbound SMS on-device;
- retry ciphertext delivery and let either endpoint revoke the device; and
- expose decrypted messages as explicitly untrusted Agent data.

## Excluded from v1

- replacing the default texting app;
- MMS and RCS;
- reading the existing inbox or sending SMS;
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
