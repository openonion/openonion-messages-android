## Outcome

Describe the user-visible or operational result.

## Changes

- List the smallest relevant implementation changes.

## Security and privacy

- [ ] I reviewed permissions, plaintext boundaries, logs, credentials, and Agent authority.
- [ ] This change does not add real SMS, phone numbers, Agent addresses, tokens, keys, or personal data.
- [ ] Protocol, permission, cryptography, or retention changes are documented and explicitly called out.

## Verification

- [ ] `./gradlew test lint assembleDebug assembleRelease`
- [ ] `./gradlew connectedDebugAndroidTest` when Android behavior changed
- [ ] Real cross-repository lifecycle test when synchronization or deletion changed
- [ ] Screenshots or recordings when UI or assets changed

## Compatibility

List any required oo-api, ConnectOnion, Android API, schema, or wire-protocol version.
