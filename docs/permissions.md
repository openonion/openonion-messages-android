# Android Permissions

| Permission | Why it is needed | When requested |
|---|---|---|
| `RECEIVE_SMS` | Get a copy of each new SMS to encrypt for the paired Agent | After pairing, on **Turn on SMS forwarding** |
| `CAMERA` | Scan the pairing QR code | When the scanner opens |
| `INTERNET` | Claim pairing links and upload ciphertext | Manifest install permission |
| `ACCESS_NETWORK_STATE` | Let WorkManager wait for connectivity before upload | Added by WorkManager |
| `WAKE_LOCK` | Finish a bounded queued upload while the device is awake | Added by WorkManager |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled ciphertext work after reboot | Added by WorkManager |
| `FOREGROUND_SERVICE` | WorkManager compatibility for long-running system work | Added by WorkManager |

The app never asks to be the default SMS app. The phone's own texting app keeps
storing, showing, sending and notifying; Android broadcasts each incoming SMS to
both. The app cannot read the existing inbox, send SMS, or access contacts,
call logs, location, microphone or storage. The remote Agent has no send
endpoint.

Android 13+ restricts SMS access for APKs installed outside an app store. The
permission request then fails without a dialog, and the app guides the owner to
**App info → ⋮ → Allow restricted settings**.
