package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageSelectionTest {

    @Test
    fun fromCode_handlesValidCodes() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("system"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromCode("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromCode("zh"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromCode("zh_CN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
    }

    @Test
    fun fromCode_recoversSafelyFromNullAndInvalidValues() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("unknown_language"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("fr"))
    }

    @Test
    fun getTargetLocale_returnsExpectedLocales() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, LocaleHelper.getTargetLocale(AppLanguage.SIMPLIFIED_CHINESE))
        assertEquals(Locale.ENGLISH, LocaleHelper.getTargetLocale(AppLanguage.ENGLISH))
    }
}
