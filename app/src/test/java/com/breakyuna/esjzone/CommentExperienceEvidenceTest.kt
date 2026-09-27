package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.experienceSupportsSubmission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentExperienceEvidenceTest {
    @Test
    fun onlyAnIncreaseFromTwoAvailableSnapshotsSupportsAnUncertainSubmission() {
        assertTrue(experienceSupportsSubmission(7264, 7368))
        assertFalse(experienceSupportsSubmission(null, 7368))
        assertFalse(experienceSupportsSubmission(7264, null))
        assertFalse(experienceSupportsSubmission(7264, 7264))
        assertFalse(experienceSupportsSubmission(7264, 7260))
    }
}
