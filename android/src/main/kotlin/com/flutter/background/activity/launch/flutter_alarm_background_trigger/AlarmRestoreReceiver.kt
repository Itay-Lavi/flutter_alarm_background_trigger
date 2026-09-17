package com.flutter.background.activity.launch.flutter_alarm_background_trigger

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()
        thread(name = "alarm-restore", start = true) {
            try {
                AlarmUtils(context.applicationContext).restorePersistedAlarms()
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to restore persisted alarms", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmRestoreReceiver"
    }
}
