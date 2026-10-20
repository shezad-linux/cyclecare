package com.cyclecare.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder

/**
 * The alarm APIs the reminder scheduler uses.
 *
 * Behind an interface so the tier decision can be asserted in a test
 * without an emulator, and so the scheduler does not reach for
 * [AlarmManager] directly and become untestable.
 */
internal interface ReminderAlarmFacade {
    /**
     * Whether the user has granted the app access to exact alarms.
     *
     * Never assume this. It is denied by default on a fresh install and the
     * user can revoke it at any time.
     */
    fun canScheduleExactAlarms(): Boolean

    /** Tier 1: exact, and delivered through Doze. */
    fun setExactAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest)

    /** Tier 2: inexact, but delivered through Doze. Needs no permission. */
    fun setAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest)

    fun cancel(uniqueName: String)
}

/**
 * What is being scheduled.
 *
 * The facade owns the pending intent, so the scheduler does not have to
 * know how an alarm is addressed, and a test can say which reminder it is
 * looking at.
 */
internal data class ReminderAlarmRequest(
    /** The rule the reminder belongs to. */
    val ruleId: String,

    /** The stable identity of the reminder, which is also its request code. */
    val uniqueName: String,

    /** What the alarm carries to the receiver. */
    val payload: ReminderAlarmPayload,
)

/** The real alarm APIs. */
internal class AndroidReminderAlarmFacade(
    private val context: Context,
) : ReminderAlarmFacade {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            // The permission does not exist before API 31, so exact alarms
            // cannot be granted and Tier 2 is the only option available.
            false
        }

    override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntentFor(request),
        )
    }

    override fun setAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntentFor(request),
        )
    }

    override fun cancel(uniqueName: String) {
        alarmManager.cancel(pendingIntentFor(uniqueName))
    }

    /**
     * The pending intent for a reminder.
     *
     * The request code is derived from the reminder's unique name, so
     * scheduling the same reminder again replaces the previous alarm
     * instead of adding a second one.
     */
    private fun pendingIntentFor(request: ReminderAlarmRequest): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request.uniqueName.hashCode(),
            request.payload.writeTo(Intent(context, ReminderAlarmReceiver::class.java)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * The pending intent that addresses an existing alarm.
     *
     * A pending intent matches another when the request code and the
     * intent's identity agree, and extras are not part of that identity, so
     * this addresses the same alarm the scheduling path created.
     */
    private fun pendingIntentFor(uniqueName: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            uniqueName.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * How a reminder is delivered.
 *
 * Spec `13` §11:
 *
 * | Tier | Mechanism | Use for | Permission |
 * |---|---|---|---|
 * | 1 | `setExactAndAllowWhileIdle` | `PeriodPrep`, `LatePeriod` only | `SCHEDULE_EXACT_ALARM` |
 * | 2 | `setAndAllowWhileIdle` | Everything else time-sensitive | none |
 * | 3 | `WorkManager` | Periodic refresh, sync, cleanup | none |
 */
internal enum class ReminderDeliveryTier {
    /** Tier 1. Exact delivery, which needs the user-granted permission. */
    EXACT,

    /** Tier 2. Inexact but Doze-tolerant, and needs no permission. */
    INEXACT_WHILE_IDLE,
}

/**
 * The tier a reminder type is delivered on.
 *
 * Tier 1 is reserved for the two types that are actually wrong to be late:
 * a heads-up before a predicted period, and a late-period check after the
 * predicted start has passed. Everything else is time-sensitive but not
 * worth a permission prompt, and Tier 2 reaches it without one.
 */
internal fun tierFor(type: ReminderType): ReminderDeliveryTier =
    when (type) {
        ReminderType.PeriodPrep, ReminderType.LatePeriod -> ReminderDeliveryTier.EXACT
        else -> ReminderDeliveryTier.INEXACT_WHILE_IDLE
    }

/**
 * The payload an alarm carries to the receiver.
 *
 * An alarm cannot carry a WorkManager `Data`, so the same fields travel as
 * intent extras.
 */
internal data class ReminderAlarmPayload(
    val notificationId: Int,
    val title: String,
    val body: String,
    val privateBody: String,
    val ruleId: String,
    val reminderType: ReminderType,
    val channelKey: ReminderChannelKey,
    val deepLinkTarget: ReminderDeepLinkTarget,
    val targetEpochDay: Long?,
    val scheduledAtEpochMillis: Long,
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(keyNotificationId, notificationId)
        putExtra(keyTitle, title)
        putExtra(keyBody, body)
        putExtra(keyPrivateBody, privateBody)
        putExtra(keyRuleId, ruleId)
        putExtra(keyReminderType, reminderType.name)
        putExtra(keyChannelKey, channelKey.name)
        putExtra(keyDeepLinkTarget, deepLinkTarget.name)
        putExtra(keyTargetEpochDay, targetEpochDay ?: Long.MIN_VALUE)
        putExtra(keyScheduledAtEpochMillis, scheduledAtEpochMillis)
    }

    companion object {
        const val keyNotificationId = "reminder_notification_id"
        const val keyTitle = "reminder_title"
        const val keyBody = "reminder_body"
        const val keyPrivateBody = "reminder_private_body"
        const val keyRuleId = "reminder_rule_id"
        const val keyReminderType = "reminder_type"
        const val keyChannelKey = "reminder_channel_key"
        const val keyDeepLinkTarget = "reminder_deep_link_target"
        const val keyTargetEpochDay = "reminder_target_epoch_day"
        const val keyScheduledAtEpochMillis = "reminder_scheduled_at_epoch_millis"

        fun from(intent: Intent): ReminderAlarmPayload? {
            val ruleId = intent.getStringExtra(keyRuleId) ?: return null
            val type = intent.getStringExtra(keyReminderType)
                ?.let { raw -> runCatching { enumValueOf<ReminderType>(raw) }.getOrNull() }
                ?: return null
            val channelKey = intent.getStringExtra(keyChannelKey)
                ?.let { raw -> runCatching { enumValueOf<ReminderChannelKey>(raw) }.getOrNull() }
                ?: return null
            val deepLinkTarget = intent.getStringExtra(keyDeepLinkTarget)
                ?.let { raw -> runCatching { enumValueOf<ReminderDeepLinkTarget>(raw) }.getOrNull() }
                ?: return null
            val scheduledAt = intent.getLongExtra(keyScheduledAtEpochMillis, -1L)
            if (scheduledAt < 0L) return null
            val epochDay = intent.getLongExtra(keyTargetEpochDay, Long.MIN_VALUE)
            return ReminderAlarmPayload(
                notificationId = intent.getIntExtra(keyNotificationId, 0),
                title = intent.getStringExtra(keyTitle).orEmpty(),
                body = intent.getStringExtra(keyBody).orEmpty(),
                privateBody = intent.getStringExtra(keyPrivateBody).orEmpty(),
                ruleId = ruleId,
                reminderType = type,
                channelKey = channelKey,
                deepLinkTarget = deepLinkTarget,
                targetEpochDay = epochDay.takeIf { it != Long.MIN_VALUE },
                scheduledAtEpochMillis = scheduledAt,
            )
        }

        fun from(reminder: ScheduledReminder): ReminderAlarmPayload = ReminderAlarmPayload(
            notificationId = reminder.uniqueWorkName.hashCode(),
            title = reminder.title,
            body = reminder.body,
            privateBody = reminder.privateBody,
            ruleId = reminder.ruleId,
            reminderType = reminder.type,
            channelKey = reminder.channelKey,
            deepLinkTarget = reminder.deepLinkTarget,
            targetEpochDay = reminder.targetEpochDay,
            scheduledAtEpochMillis = reminder.scheduledAt.toInstant().toEpochMilli(),
        )
    }
}
