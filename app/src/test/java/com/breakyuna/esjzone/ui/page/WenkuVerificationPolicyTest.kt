package com.breakyuna.esjzone.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WenkuVerificationPolicyTest {
    @Test fun waitsForChapterWhileClearanceIsAvailable() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = true)

        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 1_000)
        )
        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 5_000)
        )
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CHAPTER_CONTENT,
            policy.next(hasReadableChapter = true, hasClearance = true, nowElapsedMillis = 6_000)
        )
    }

    @Test fun clearanceGraceCanExtendCheckAndThenFallsBack() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = true)

        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 59_000)
        )
        assertTrue(policy.shouldContinue(nowElapsedMillis = 61_000, checkStartedAtMillis = 1_000))
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CLEARANCE,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 79_000)
        )

        val noClearance = WenkuVerificationPolicy(acceptChapterContent = true)
        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            noClearance.next(hasReadableChapter = false, hasClearance = false, nowElapsedMillis = 61_000)
        )
        assertFalse(noClearance.shouldContinue(nowElapsedMillis = 61_000, checkStartedAtMillis = 1_000))
    }

    @Test fun nonChapterPageAcceptsClearanceWithoutMatchingOriginalDocument() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = false)
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CLEARANCE,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 1_000)
        )
    }

    @Test fun nonChapterPageWaitsWithoutClearanceEvenIfContentIsReadable() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = false)
        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = true, hasClearance = false, nowElapsedMillis = 1_000)
        )
    }

    @Test fun chapterContentCanCompleteWithoutClearance() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = true)
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CHAPTER_CONTENT,
            policy.next(hasReadableChapter = true, hasClearance = false, nowElapsedMillis = 1_000)
        )
    }
}
