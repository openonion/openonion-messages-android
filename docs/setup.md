# Setup

## 1. Create a one-time Agent link

From a ConnectOnion project that already has an identity and authentication:

```python
from connectonion import create_sms_pairing

pairing = create_sms_pairing()
print(pairing["pairing_link"])
```

The link is a short-lived secret. Do not post it in an issue, log, screenshot,
or shared chat.

## 2. Install and enable Android

Install the signed APK from the GitHub release. Open the app, choose **Use as
default SMS**, and approve only the Android permissions shown after the role
request. Paste the pairing link under **Connect agent**.

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

## 4. Disconnect or revoke

Use **Disconnect agent** in Android to revoke that phone and remove its local
credential. From the Agent, use `list_sms_devices()` and
`revoke_sms_device(device_id)` to revoke a lost or unavailable phone.

## Important v1 limitation

MMS and RCS are not supported. If your number depends on MMS, do not make this
app the default handler on that phone yet.
