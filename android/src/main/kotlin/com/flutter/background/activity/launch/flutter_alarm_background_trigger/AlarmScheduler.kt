package com.flutter.background.activity.launch.flutter_alarm_background_trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmItem

internal interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun schedule(item: AlarmItem)
    fun cancel(item: AlarmItem)
}

internal class AndroidAlarmScheduler(context: Context) : AlarmScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager =
        applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(item: AlarmItem) {
        val triggerAtMillis = requireNotNull(item.time) { "Alarm time is required" }
        if (!canScheduleExactAlarms()) {
            throw SecurityException("Exact alarm permission is not granted")
        }

        val pendingIntent = createPendingIntent(item)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(item: AlarmItem) {
        alarmManager.cancel(createPendingIntent(item))
    }

    private fun createPendingIntent(item: AlarmItem): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val intent = Intent(applicationContext, AlarmBroadcastReceiver::class.java).apply {
            putExtra(AlarmArgKey.PAYLOAD.name, item.payload)
            putExtra(AlarmArgKey.UID.name, item.userUid)
            putExtra(AlarmArgKey.ID.name, item.id)
            putExtra(AlarmArgKey.TIME.name, item.time)
            putExtra(AlarmArgKey.STATUS.name, item.status.name)
            putExtra(AlarmArgKey.SCREEN_WAKE_DURATION.name, item.screenWakeDuration)
        }
        return PendingIntent.getBroadcast(applicationContext, item.id, intent, flags)
    }
}
