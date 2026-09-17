package com.flutter.background.activity.launch.flutter_alarm_background_trigger

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.common.serializeToMap
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmDao
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmDatabase
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmItem
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmStatus
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONArray
import org.json.JSONObject

internal data class AlarmRestoreResult(
    val restored: Int,
    val markedDone: Int,
    val failed: Int,
    val permissionDenied: Boolean
)

class AlarmUtils internal constructor(
    private val alarms: AlarmDao,
    private val scheduler: AlarmScheduler
) {
    constructor(context: Context) : this(
        createAlarmDao(context.applicationContext),
        AndroidAlarmScheduler(context.applicationContext)
    )

    private fun sendSingleAlarm(result: Result, alarm: AlarmItem?) {
        if (alarm == null) {
            result.error("NOT_FOUND", "Alarm not found", "Alarm not found with given params")
            return
        }
        result.success(JSONObject(alarm.serializeToMap()).toString())
    }

    private fun sendListOfAlarms(result: Result, alarms: List<AlarmItem>?) {
        if (alarms == null) {
            result.error("NOT_FOUND", "Alarms not found", "Alarms not found with given params")
            return
        }
        result.success(JSONArray(alarms.map { it.serializeToMap() }).toString())
    }

    fun addAlarm(args: AlarmArgs, result: Result) {
        val canSchedule = try {
            scheduler.canScheduleExactAlarms()
        } catch (error: Throwable) {
            result.error(SCHEDULE_FAILED, "Unable to check exact alarm access", error.message)
            return
        }
        if (!canSchedule) {
            result.error(
                EXACT_ALARM_PERMISSION_DENIED,
                "Exact alarm permission is not granted",
                null
            )
            return
        }

        val insertedId = alarms.insert(AlarmItem.fromAlarmArgs(args))
        if (insertedId == null || insertedId <= 0L) {
            result.error(SCHEDULE_FAILED, "Unable to persist alarm", null)
            return
        }

        val alarm = alarms.findByUserId(insertedId.toInt())
        if (alarm == null) {
            result.error(SCHEDULE_FAILED, "Persisted alarm could not be read", null)
            return
        }

        try {
            scheduler.schedule(alarm)
        } catch (error: SecurityException) {
            alarms.delete(alarm)
            result.error(
                EXACT_ALARM_PERMISSION_DENIED,
                "Exact alarm permission is not granted",
                error.message
            )
            return
        } catch (error: Throwable) {
            alarms.delete(alarm)
            result.error(SCHEDULE_FAILED, "Unable to schedule alarm", error.message)
            return
        }

        sendSingleAlarm(result, alarm)
    }

    fun getAlarm(args: AlarmArgs, result: Result) {
        sendSingleAlarm(result, alarms.findByUserId(args.id!!))
    }

    fun getAlarmByTime(args: AlarmArgs, result: Result) {
        sendListOfAlarms(result, alarms.findByTime(args.time!!))
    }

    fun getAlarmByUid(args: AlarmArgs, result: Result) {
        sendListOfAlarms(result, alarms.findByUserUid(args.uid!!))
    }

    fun getAlarmByPayload(args: AlarmArgs, result: Result) {
        sendListOfAlarms(result, alarms.findByPayload(args.payload!!))
    }

    fun getAllAlarms(args: AlarmArgs, result: Result) {
        sendListOfAlarms(result, alarms.getAll())
    }

    fun deleteAlarm(args: AlarmArgs, result: Result) {
        val item = alarms.findByUserId(args.id!!)
        if (item != null) {
            scheduler.cancel(item)
            alarms.delete(item)
            result.success(true)
        } else {
            result.error("NOT_FOUND", "Alarm not found", null)
        }
    }

    fun deleteAlarmByTime(args: AlarmArgs, result: Result) {
        val affectedAlarms = alarms.findByTime(args.time!!) ?: emptyList()
        affectedAlarms.forEach(scheduler::cancel)
        val affected = alarms.deleteByTime(args.time!!)
        result.success(affected > 0)
    }

    fun deleteAlarmByUid(args: AlarmArgs, result: Result) {
        val affectedAlarms = alarms.findByUserUid(args.uid!!) ?: emptyList()
        affectedAlarms.forEach(scheduler::cancel)
        val affected = alarms.deleteByUid(args.uid!!)
        result.success(affected > 0)
    }

    fun deleteAlarmByPayload(args: AlarmArgs, result: Result) {
        val affectedAlarms = alarms.findByPayload(args.payload!!) ?: emptyList()
        affectedAlarms.forEach(scheduler::cancel)
        val affected = alarms.deleteByPayload(args.payload!!)
        result.success(affected > 0)
    }

    fun deleteAllAlarms(args: AlarmArgs, result: Result) {
        val allAlarms = alarms.getAll() ?: emptyList()
        allAlarms.forEach(scheduler::cancel)
        val affected = alarms.deleteAll()
        result.success(affected > 0)
    }

    fun initialize(args: AlarmArgs, result: Result) {
        onBackgroundActivityLaunch(FlutterAlarmBackgroundTriggerPlugin.channel!!)
    }

    fun onBackgroundActivityLaunch(channel: MethodChannel) {
        val pending = alarms.findByStatus(AlarmStatus.PENDING.name) ?: emptyList()
        if (pending.isEmpty()) return

        val now = System.currentTimeMillis()
        val due = pending.filter { it.time != null && it.time!! <= now }
        if (due.isEmpty()) return

        due.forEach { item ->
            item.status = AlarmStatus.DONE
            alarms.update(item)
        }
        sendBackgroundAlarmEvent(channel, due)
    }

    internal fun restorePersistedAlarms(
        now: Long = System.currentTimeMillis()
    ): AlarmRestoreResult {
        val pending = alarms.findByStatus(AlarmStatus.PENDING.name) ?: emptyList()
        val future = mutableListOf<AlarmItem>()
        var markedDone = 0

        pending.forEach { item ->
            val time = item.time
            if (time == null || time <= now) {
                item.status = AlarmStatus.DONE
                alarms.update(item)
                markedDone++
            } else {
                future.add(item)
            }
        }

        if (future.isEmpty()) {
            return AlarmRestoreResult(0, markedDone, 0, false)
        }

        val canSchedule = try {
            scheduler.canScheduleExactAlarms()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to check exact alarm access while restoring alarms", error)
            return AlarmRestoreResult(0, markedDone, future.size, false)
        }
        if (!canSchedule) {
            return AlarmRestoreResult(0, markedDone, 0, true)
        }

        var restored = 0
        var failed = 0
        for (item in future) {
            try {
                scheduler.schedule(item)
                restored++
            } catch (error: SecurityException) {
                Log.w(TAG, "Exact alarm access was revoked while restoring alarms", error)
                return AlarmRestoreResult(restored, markedDone, failed, true)
            } catch (error: Throwable) {
                failed++
                Log.e(TAG, "Unable to restore alarm ${item.id}", error)
            }
        }
        return AlarmRestoreResult(restored, markedDone, failed, false)
    }

    private fun sendBackgroundAlarmEvent(channel: MethodChannel, alarms: List<AlarmItem>) {
        channel.invokeMethod(
            MethodNames.ON_BACKGROUND_ACTIVITY_LAUNCH.name,
            JSONArray(alarms.map { it.serializeToMap() }).toString()
        )
    }

    internal companion object {
        const val EXACT_ALARM_PERMISSION_DENIED = "EXACT_ALARM_PERMISSION_DENIED"
        const val SCHEDULE_FAILED = "SCHEDULE_FAILED"
        private const val TAG = "AlarmUtils"

        private fun createAlarmDao(context: Context): AlarmDao =
            Room.databaseBuilder(
                context,
                AlarmDatabase::class.java,
                "${FlutterAlarmBackgroundTriggerPlugin.PLUGIN_NAME}.db"
            ).allowMainThreadQueries().build().alarmsDao()!!
    }
}
