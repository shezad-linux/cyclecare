# ─────────────────────────────────────────────────────────────────
# CycleCare R8 / ProGuard configuration
#
# Minification is enabled for the release build. The rules below exist
# because this app persists data as strings and instantiates classes by
# name — both of which R8 will silently break.
# ─────────────────────────────────────────────────────────────────

# ── CRITICAL: model enums are a storage format ────────────────────
# AgeBand, TrackingGoal, CycleSource, Mood, PainLevel, EnergyLevel,
# DischargeType, ReminderType, ReminderChannelKey, ReminderSoundPreset
# and ReminderDeepLinkTarget are persisted into Room as their constant
# NAMES and read back with enumValueOf().
#
# R8 is free to rename or merge enum constants. If Mood.Stable becomes
# Mood.a, enumValueOf("Stable") throws, parseEnumSafely() swallows it,
# and every affected record silently falls back to a default value —
# corrupting real user data on first launch of a shrunk build.
#
# core/model is 222 lines. Keeping it whole is cheap insurance for a
# class of data loss that is otherwise invisible until it happens.
#
# SECOND, INDEPENDENT REASON — SQL string literals.
# migration1To2 (core/database/.../CycleCareDatabase.kt) contains raw
# SQL that compares and writes enum constant names as string literals:
#
#   CASE WHEN type = 'OvulationEstimate' AND id = 'OvulationEstimate'
#        THEN 'FertileWindowStart' ELSE id END
#   CASE WHEN type = 'PeriodPrep' THEN -daysBefore ELSE 0 END
#
# R8 cannot rewrite string literals inside SQL. If the enum constants
# were renamed but these queries were not, the migration would silently
# match zero rows and leave reminder configuration uncorrected. The
# keep rule below is what keeps the literals and the enum in agreement.
-keep class com.cyclecare.core.model.** { *; }

# ── Attributes required by Room, Kotlin reflection and coroutines ──
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── WorkManager instantiates workers by class name ────────────────
# ReminderDeliveryWorker and ReminderRefreshWorker are constructed
# reflectively from the class name stored in WorkManager's own
# database. Obfuscating them means workers are never instantiated and
# reminders silently stop firing after a release build.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}

# ── Room ─────────────────────────────────────────────────────────
# The @Database class is referenced by generated code, but keeping it
# explicit documents the intent and survives schema changes.
-keep class com.cyclecare.core.database.CycleCareDatabase { *; }

# ── Manifest-declared components ─────────────────────────────────
# AGP generates keep rules from the manifest, but restating the
# custom Application is cheap and makes the dependency obvious.
-keep class com.cyclecare.app.CycleCareApplication { *; }

# ── Diagnostics redaction ────────────────────────────────────────
# CrashReporter / AppLogger are pure interfaces; NoOp implementations
# are reachable only through DI. Keep the contracts so an alternate
# implementation can be wired without another rules change.
-keep interface com.cyclecare.core.domain.CrashReporter { *; }
-keep interface com.cyclecare.core.domain.AppLogger { *; }

# ── Suppress warnings for optional/transitive SDKs ───────────────
# AdMob (play-services-ads) and UMP ship their own consumer rules but
# reference optional Play Services components that are absent at
# compile time. Silence only these, so genuine issues still surface.
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**
-dontwarn com.google.android.gms.internal.ads_identifier**

# ── Keep line numbers for readable Play Console crash traces ─────
# Without this, any future crash reporting maps to obfuscated frames.
-keepattributes SourceFile,LineNumberTable