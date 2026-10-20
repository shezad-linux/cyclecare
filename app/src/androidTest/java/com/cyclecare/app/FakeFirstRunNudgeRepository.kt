package com.cyclecare.app

import com.cyclecare.core.domain.FirstRunNudgeRepository

/**
 * The first-run nudge flag, in memory.
 *
 * The real one is a row in the app settings table, which is not available
 * to a test that swaps the whole container out.
 */
class FakeFirstRunNudgeRepository(
    /**
     * Whether the nudge has already been shown.
     *
     * Defaults to true, because the tests that use this are not testing
     * the first run, and a modal nudge would block the navigation they are
     * exercising. The nudge's own tests ask for it explicitly.
     */
    private var shown: Boolean = true,
) : FirstRunNudgeRepository {
    var showCount: Int = 0
        private set

    override suspend fun shouldShowFirstRunNudge(): Boolean = !shown

    override suspend fun markFirstRunNudgeShown() {
        shown = true
        showCount += 1
    }
}
