# Setup

## 1. Create a signed Agent QR

From a ConnectOnion project that already has an identity and authentication:

```bash
co sms pair
```

The command displays a short-lived QR and waits for the phone. Do not post the
QR or link in an issue, log, screenshot, or shared chat.

## 2. Install and enable Android

Download the signed APK from the
[latest GitHub Release](https://github.com/openonion/openonion-messages-android/releases/latest).
Use the APK, not the AAB, for direct installation. Verify the accompanying
SHA-256 file and GitHub attestation as described in
[Releasing](releasing.md), then install it on Android 8.0 or newer.

Open the app, choose **Use as default SMS**, and approve only the Android
permissions shown after the role request. Scan the QR with the system camera
and open it in OpenOnion Messages; pasting the link under **Connect agent**
remains a fallback.

Both the CLI and Android show six digits. Compare them before approving in the
CLI. A mismatch means the phone key is not the key the Agent is about to trust:
cancel, let the link expire, and create a new pairing.

Pairing affects only SMS received afterward. The existing inbox stays local and
is not uploaded.

## 3. Read from the Agent

```python
from connectonion import get_sms, wait_for_sms

recent = get_sms(last=10)
next_message = wait_for_sms(timeout_seconds=60)
```

Returned dictionaries include `sender`, `body`, `received_at`, server metadata,
and `trusted: False`. Acknowledgement records successful processing; it does not
erase the local SMS or server ciphertext.

To remove both copies, use the delete action beside a message in Android and
confirm **Delete everywhere**. The local SMS is removed immediately; if the
phone is offline, encrypted server deletion remains queued and retries when a
network connection returns.

## 4. Disconnect or revoke

Use **Disconnect agent** in Android to revoke that phone and remove its local
credential. From the Agent, use `list_sms_devices()` and
`revoke_sms_device(device_id)` to revoke a lost or unavailable phone.

## Important v1 limitation

MMS and RCS are not supported. If your number depends on MMS, do not make this
app the default handler on that phone yet.
