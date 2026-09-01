# Android Permissions

| Permission | Why v1 needs it | When requested |
|---|---|---|
| `RECEIVE_SMS` | Receive carrier SMS as the default handler | After the user accepts the SMS role |
| `READ_SMS` | Render the owner-visible system inbox | After the SMS role |
| `SEND_SMS` | Let the human send SMS from the default app | After the SMS role |
| `POST_NOTIFICATIONS` | Notify privately about incoming messages on Android 13+ | After the SMS role |
| `INTERNET` | Claim pairing links and upload ciphertext | Manifest install permission |
| `ACCESS_NETWORK_STATE` | Let WorkManager wait for connectivity before upload | Added by WorkManager |
| `WAKE_LOCK` | Finish a bounded queued upload while the device is awake | Added by WorkManager |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled ciphertext work after reboot | Added by WorkManager |
| `FOREGROUND_SERVICE` | WorkManager compatibility for long-running system work | Added by WorkManager |

The app requests no contacts, call-log, location, microphone, camera, or storage
permission. Sending is initiated only from visible human UI or Android's
`RESPOND_VIA_MESSAGE` flow; the remote Agent has no SMS-send endpoint.
