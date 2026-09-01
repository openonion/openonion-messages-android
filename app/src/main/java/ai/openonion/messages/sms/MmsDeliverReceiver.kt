package ai.openonion.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Declared because Android requires every SMS role holder to own the WAP push
 * entry point. OpenOnion Messages v1 does not parse or sync MMS; it records the
 * unsupported delivery without exposing payload bytes to logs.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w(TAG, "MMS received but MMS is not supported in v1")
    }

    private companion object {
        const val TAG = "OpenOnionMessages"
    }
}
