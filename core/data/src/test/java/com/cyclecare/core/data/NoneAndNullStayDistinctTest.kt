package com.cyclecare.core.data

import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * Spec `02` §9.10.2: no `None` value is ever converted to `NULL`, or
 * the other way round.
 *
 * `None` means "explicitly none" — the user said there was no pain.
 * `NULL` means "not tracked" — the user never said. They are different
 * answers, and a migration or a mapper that turns one into the other
 * rewrites what the user recorded.
 *
 * The migration is checked on the row level in
 * `CycleCareDatabaseRoomTest` and in
 * `CycleCareDatabaseDataIntegrityTest`. This is the other end of it:
 * the read and write paths, which are where the distinction would be
 * lost if it were ever going to be.
 */
class NoneAndNullStayDistinctTest {

    @Test
    fun aRecordedNoneReadsBackAsNone() {
        assertThat("None".toPainLevel(NoOpAppLogger)).isEqualTo(PainLevel.None)
        assertThat("None".toDischargeType(NoOpAppLogger)).isEqualTo(DischargeType.None)
    }

    @Test
    fun anUntrackedValueReadsBackAsUntracked() {
        assertThat(null.toPainLevel(NoOpAppLogger)).isNull()
        assertThat(null.toDischargeType(NoOpAppLogger)).isNull()
        assertThat(null.toMood(NoOpAppLogger)).isNull()
        assertThat(null.toEnergyLevel(NoOpAppLogger)).isNull()
    }

    @Test
    fun aRecordedNoneIsWrittenAsNoneAndAnUntrackedValueIsWrittenAsNull() {
        val recorded = log(painLevel = PainLevel.None, dischargeType = DischargeType.None)
        val untracked = log(painLevel = null, dischargeType = null)

        assertThat(recorded.toEntity(nowEpochMillis = 0L).painLevel).isEqualTo("None")
        assertThat(recorded.toEntity(nowEpochMillis = 0L).dischargeType).isEqualTo("None")
        assertThat(untracked.toEntity(nowEpochMillis = 0L).painLevel).isNull()
        assertThat(untracked.toEntity(nowEpochMillis = 0L).dischargeType).isNull()
    }

    @Test
    fun theRoundTripKeepsTheTwoApart() {
        val recorded = log(painLevel = PainLevel.None, dischargeType = DischargeType.None)
        val untracked = log(painLevel = null, dischargeType = null)

        // A recorded "none" comes back as a recorded "none", and an
        // untracked field comes back untracked. Neither becomes the
        // other on the way through.
        assertThat(recorded.toEntity(0L).toModel(NoOpAppLogger).painLevel).isEqualTo(PainLevel.None)
        assertThat(recorded.toEntity(0L).toModel(NoOpAppLogger).dischargeType)
            .isEqualTo(DischargeType.None)
        assertThat(untracked.toEntity(0L).toModel(NoOpAppLogger).painLevel).isNull()
        assertThat(untracked.toEntity(0L).toModel(NoOpAppLogger).dischargeType).isNull()
    }

    @Test
    fun aFieldWithNoNoneVariantFallsBackRatherThanInventingAnAnswer() {
        // Mood and EnergyLevel have no "None" variant at all, which is
        // why v3 made them nullable. A stored "None" in either is not a
        // real value, so it degrades to the documented fallback rather
        // than being read as "not tracked".
        assertThat("None".toMood(NoOpAppLogger)).isEqualTo(Mood.Stable)
        assertThat("None".toEnergyLevel(NoOpAppLogger)).isEqualTo(EnergyLevel.Steady)
    }

    private fun log(
        painLevel: PainLevel?,
        dischargeType: DischargeType?,
    ) = SymptomLog(
        id = "log-a",
        date = LocalDate.parse("2026-03-11"),
        mood = Mood.Stable,
        painLevel = painLevel,
        energyLevel = EnergyLevel.Steady,
        dischargeType = dischargeType,
        medication = null,
        notes = null,
    )
}
