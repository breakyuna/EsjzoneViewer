package com.breakyuna.esjzone.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Credential-free starter journey for generating a profile from the app's local startup path.
 *
 * Keep this flow deterministic: add authenticated journeys only when the test fixture can provide
 * a disposable account without embedding cookies, tokens, or other secrets in this repository.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun appStartup() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            startActivityAndWait()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.breakyuna.esjzone"
    }
}
