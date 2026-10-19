package com.cyclecare.core.data

import com.cyclecare.core.database.CycleRecordEntity
import com.cyclecare.core.database.ReminderEventEntity
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.database.UserProfileEntity
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate

internal fun UserProfile.toEntity() = UserProfileEntity(
    ageBand = ageBand.name,
    averageCycleLength = averageCycleLength,
    periodLength = periodLength,
    lastPeriodStartEpochDay = lastPeriodStart.toEpochDay(),
    trackingGoal = trackingGoal.name,
    remindersEnabled = remindersEnabled,
    privacyModeEnabled = privacyModeEnabled,
)

internal fun UserProfileEntity.toModel(logger: AppLogger) = UserProfile(
    ageBand = ageBand.toAgeBand(logger),
    averageCycleLength = averageCycleLength,
    periodLength = periodLength,
    lastPeriodStart = LocalDate.ofEpochDay(lastPeriodStartEpochDay),
    trackingGoal = trackingGoal.toTrackingGoal(logger),
    remindersEnabled = remindersEnabled,
    privacyModeEnabled = privacyModeEnabled,
)

internal fun CycleRecord.toEntity(nowEpochMillis: Long) = CycleRecordEntity(
    id = id,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate?.toEpochDay(),
    cycleLength = cycleLength,
    source = source.name,
    notes = notes,
    createdAtEpochMillis = nowEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
    deletedAtEpochMillis = null,
)

internal fun CycleRecordEntity.toModel(logger: AppLogger) = CycleRecord(
    id = id,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = endEpochDay?.let { LocalDate.ofEpochDay(it) },
    cycleLength = cycleLength,
    source = source.toCycleSource(logger),
    notes = notes,
)

internal fun SymptomLog.toEntity(nowEpochMillis: Long) = SymptomLogEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    mood = mood?.name,
    painLevel = painLevel?.name,
    energyLevel = energyLevel?.name,
    dischargeType = dischargeType?.name,
    medication = medication,
    notes = notes,
    createdAtEpochMillis = nowEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
    deletedAtEpochMillis = null,
)

internal fun SymptomLogEntity.toModel(logger: AppLogger) = SymptomLog(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    mood = mood.toMood(logger),
    painLevel = painLevel.toPainLevel(logger),
    energyLevel = energyLevel.toEnergyLevel(logger),
    dischargeType = dischargeType.toDischargeType(logger),
    medication = medication,
    notes = notes,
)

internal fun ReminderRule.toEntity() = ReminderRuleEntity(
    id = id,
    type = type.name,
    enabled = enabled,
    offsetDays = daysBefore,
    hourOfDay = hourOfDay,
    minute = minute,
    // Any time reaching the database before v3 came from a seed, never
    // from the user, so nothing is marked pinned yet.
    userPinnedTime = false,
)

internal fun ReminderRuleEntity.toModel(logger: AppLogger) = ReminderRule(
    id = id,
    type = type.toReminderType(logger),
    enabled = enabled,
    daysBefore = offsetDays,
    hourOfDay = hourOfDay,
    minute = minute,
)

internal fun ReminderEvent.toEntity() = ReminderEventEntity(
    id = id,
    ruleId = ruleId,
    type = type.name,
    channelKey = channelKey.name,
    scheduledAtEpochMillis = scheduledAt.toInstant().toEpochMilli(),
    deliveredAtEpochMillis = deliveredAt.toInstant().toEpochMilli(),
    title = title,
    body = body,
    privateBody = privateBody,
    deepLinkTarget = deepLinkTarget.name,
    targetEpochDay = targetEpochDay,
    readAtEpochMillis = readAtEpochMillis,
    dismissedAtEpochMillis = dismissedAtEpochMillis,
    deliveryState = "DELIVERED",
)

internal fun ReminderEventEntity.toModel(logger: AppLogger) = ReminderEvent(
    id = id,
    ruleId = ruleId,
    type = type.toReminderType(logger),
    channelKey = channelKey.toReminderChannelKey(logger),
    scheduledAt = java.time.Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
    deliveredAt = java.time.Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
    title = title,
    body = body,
    privateBody = privateBody,
    deepLinkTarget = deepLinkTarget.toReminderDeepLinkTarget(logger),
    targetEpochDay = targetEpochDay,
    readAtEpochMillis = readAtEpochMillis,
    dismissedAtEpochMillis = dismissedAtEpochMillis,
)

