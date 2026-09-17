package com.flutter.background.activity.launch.flutter_alarm_background_trigger

import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmDao
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmItem
import com.flutter.background.activity.launch.flutter_alarm_background_trigger.db.AlarmStatus
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmUtilsTest {
    @Test
    fun restorePersistedAlarmsRestoresFutureAndMarksMissedWithoutDuplicates() {
        val first = alarm(1, 2_000L, wakeDuration = 60_000L, uid = "first")
        val second = alarm(2, 3_000L, wakeDuration = 120_000L, uid = "second")
        val overdue = alarm(3, 1_000L)
        val invalid = alarm(4, null)
        val alreadyDone = alarm(5, 4_000L, status = AlarmStatus.DONE)
        val dao = FakeAlarmDao(first, second, overdue, invalid, alreadyDone)
        val scheduler = FakeAlarmScheduler()
        val alarmUtils = AlarmUtils(dao, scheduler)

        val firstRestore = alarmUtils.restorePersistedAlarms(now = 1_000L)
        val secondRestore = alarmUtils.restorePersistedAlarms(now = 1_000L)

        assertEquals(2, firstRestore.restored)
        assertEquals(2, firstRestore.markedDone)
        assertEquals(0, firstRestore.failed)
        assertFalse(firstRestore.permissionDenied)
        assertEquals(2, secondRestore.restored)
        assertEquals(0, secondRestore.markedDone)
        assertEquals(setOf(1, 2), scheduler.scheduledById.keys)
        assertEquals(4, scheduler.scheduleCalls)
        assertEquals(60_000L, scheduler.scheduledById.getValue(1).screenWakeDuration)
        assertEquals("second", scheduler.scheduledById.getValue(2).userUid)
        assertEquals(AlarmStatus.DONE, dao.findByUserId(3)?.status)
        assertEquals(AlarmStatus.DONE, dao.findByUserId(4)?.status)
        assertEquals(AlarmStatus.DONE, dao.findByUserId(5)?.status)
    }

    @Test
    fun restorePersistedAlarmsKeepsFutureRowsPendingWithoutPermission() {
        val future = alarm(1, 2_000L)
        val overdue = alarm(2, 500L)
        val dao = FakeAlarmDao(future, overdue)
        val scheduler = FakeAlarmScheduler(canSchedule = false)

        val restored = AlarmUtils(dao, scheduler).restorePersistedAlarms(now = 1_000L)

        assertTrue(restored.permissionDenied)
        assertEquals(0, restored.restored)
        assertEquals(1, restored.markedDone)
        assertEquals(AlarmStatus.PENDING, dao.findByUserId(1)?.status)
        assertEquals(AlarmStatus.DONE, dao.findByUserId(2)?.status)
        assertTrue(scheduler.scheduledById.isEmpty())
    }

    @Test
    fun addAlarmDoesNotInsertWhenExactAlarmPermissionIsDenied() {
        val dao = FakeAlarmDao()
        val result = RecordingResult()
        val alarmUtils = AlarmUtils(dao, FakeAlarmScheduler(canSchedule = false))

        alarmUtils.addAlarm(alarmArgs(time = 2_000L, wakeDuration = 60_000), result)

        assertEquals(AlarmUtils.EXACT_ALARM_PERMISSION_DENIED, result.errorCode)
        assertTrue(dao.getAll().orEmpty().isEmpty())
        assertNull(result.successValue)
    }

    @Test
    fun addAlarmPersistsWakeDuration() {
        val dao = FakeAlarmDao()
        val scheduler = FakeAlarmScheduler()
        val result = RecordingResult()

        AlarmUtils(dao, scheduler).addAlarm(
            alarmArgs(time = 2_000L, wakeDuration = 60_000),
            result
        )

        assertNull(result.errorCode)
        assertEquals(60_000L, scheduler.scheduledById.values.single().screenWakeDuration)
        assertEquals(60_000L, dao.getAll().orEmpty().single().screenWakeDuration)
    }

    @Test
    fun addAlarmRollsBackOnlyNewRowWhenSchedulingFails() {
        val existing = alarm(7, 5_000L, uid = "existing")
        val dao = FakeAlarmDao(existing)
        val scheduler = FakeAlarmScheduler(scheduleFailure = IllegalStateException("failure"))
        val result = RecordingResult()

        AlarmUtils(dao, scheduler).addAlarm(alarmArgs(time = 6_000L), result)

        assertEquals(AlarmUtils.SCHEDULE_FAILED, result.errorCode)
        assertEquals(listOf(7), dao.getAll().orEmpty().map { it.id })
        assertEquals("existing", dao.findByUserId(7)?.userUid)
    }

    @Test
    fun addAlarmRollsBackWhenPermissionIsRevokedDuringScheduling() {
        val dao = FakeAlarmDao()
        val scheduler = FakeAlarmScheduler(
            scheduleFailure = SecurityException("permission revoked")
        )
        val result = RecordingResult()

        AlarmUtils(dao, scheduler).addAlarm(alarmArgs(time = 6_000L), result)

        assertEquals(AlarmUtils.EXACT_ALARM_PERMISSION_DENIED, result.errorCode)
        assertTrue(dao.getAll().orEmpty().isEmpty())
    }

    private fun alarmArgs(time: Long, wakeDuration: Int = 1_000): AlarmArgs =
        AlarmArgs.fromMethodCall(
            MethodCall(
                "ADD",
                mapOf(
                    AlarmArgKey.TIME.name to time,
                    AlarmArgKey.UID.name to "new",
                    AlarmArgKey.PAYLOAD.name to "{\"source\":\"test\"}",
                    AlarmArgKey.SCREEN_WAKE_DURATION.name to wakeDuration
                )
            )
        )

    private fun alarm(
        id: Int,
        time: Long?,
        wakeDuration: Long = 1_000L,
        uid: String = "alarm-$id",
        status: AlarmStatus = AlarmStatus.PENDING
    ) = AlarmItem().apply {
        this.id = id
        this.time = time
        this.userUid = uid
        this.payload = "{\"id\":$id}"
        this.createdAt = 100L
        this.status = status
        this.screenWakeDuration = wakeDuration
    }
}

private class FakeAlarmScheduler(
    private val canSchedule: Boolean = true,
    private val scheduleFailure: Throwable? = null
) : AlarmScheduler {
    val scheduledById = linkedMapOf<Int, AlarmItem>()
    var scheduleCalls = 0

    override fun canScheduleExactAlarms(): Boolean = canSchedule

    override fun schedule(item: AlarmItem) {
        scheduleCalls++
        scheduleFailure?.let { throw it }
        scheduledById[item.id] = item.copyForTest()
    }

    override fun cancel(item: AlarmItem) = Unit
}

private class FakeAlarmDao(vararg initial: AlarmItem) : AlarmDao {
    private val items = linkedMapOf<Int, AlarmItem>()
    private var nextId = 1

    init {
        initial.forEach {
            items[it.id] = it.copyForTest()
            nextId = maxOf(nextId, it.id + 1)
        }
    }

    override fun getAll(): List<AlarmItem> = items.values.map(AlarmItem::copyForTest)

    override fun loadAllByIds(userIds: IntArray?): List<AlarmItem> =
        userIds?.toList().orEmpty().mapNotNull(items::get).map(AlarmItem::copyForTest)

    override fun findByUserUid(userUid: String?): List<AlarmItem> =
        items.values.filter { it.userUid == userUid }.map(AlarmItem::copyForTest)

    override fun findByUserId(id: Int): AlarmItem? = items[id]?.copyForTest()

    override fun findByTime(time: Long): List<AlarmItem> =
        items.values.filter { it.time == time }.map(AlarmItem::copyForTest)

    override fun findByPayload(payload: String?): List<AlarmItem> =
        items.values.filter { it.payload == payload }.map(AlarmItem::copyForTest)

    override fun findByStatus(status: String): List<AlarmItem> =
        items.values.filter { it.status.name == status }.map(AlarmItem::copyForTest)

    override fun insert(alarm: AlarmItem?): Long? {
        if (alarm == null) return null
        val id = if (alarm.id == 0) nextId++ else alarm.id
        items[id] = alarm.copyForTest().apply { this.id = id }
        return id.toLong()
    }

    override fun update(alarm: AlarmItem?): Int? {
        if (alarm == null || !items.containsKey(alarm.id)) return 0
        items[alarm.id] = alarm.copyForTest()
        return 1
    }

    override fun delete(alarm: AlarmItem?) {
        if (alarm != null) items.remove(alarm.id)
    }

    override fun deleteAll(): Int {
        val count = items.size
        items.clear()
        return count
    }

    override fun deleteByTime(time: Long): Int = deleteWhere { it.time == time }

    override fun deleteByUid(uid: String): Int = deleteWhere { it.userUid == uid }

    override fun deleteByPayload(payload: String): Int = deleteWhere { it.payload == payload }

    private fun deleteWhere(predicate: (AlarmItem) -> Boolean): Int {
        val ids = items.values.filter(predicate).map { it.id }
        ids.forEach(items::remove)
        return ids.size
    }
}

private class RecordingResult : Result {
    var successValue: Any? = null
    var errorCode: String? = null

    override fun success(result: Any?) {
        successValue = result
    }

    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        this.errorCode = errorCode
    }

    override fun notImplemented() = Unit
}

private fun AlarmItem.copyForTest() = AlarmItem().also {
    it.id = id
    it.time = time
    it.payload = payload
    it.userUid = userUid
    it.createdAt = createdAt
    it.status = status
    it.screenWakeDuration = screenWakeDuration
}
