package com.breakyuna.esjzone.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.breakyuna.esjzone.AppLanguage
import java.util.Locale

object LocaleHelper {

    fun syncSystemLocale(context: Context, appLanguage: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
                val targetLocales = when (appLanguage) {
                    AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                    AppLanguage.SIMPLIFIED_CHINESE -> LocaleList.forLanguageTags("zh-CN")
                    AppLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
                }
                if (localeManager.applicationLocales != targetLocales) {
                    localeManager.applicationLocales = targetLocales
                }
            }
        }
    }

    fun getTargetLocale(appLanguage: AppLanguage): Locale {
        return when (appLanguage) {
            AppLanguage.SYSTEM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    Resources.getSystem().configuration.locale ?: Locale.getDefault()
                }
            }
            AppLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
        }
    }

    fun createLocalizedContext(context: Context, appLanguage: AppLanguage): Context {
        val targetLocale = getTargetLocale(appLanguage)
        Locale.setDefault(targetLocale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
        return context.createConfigurationContext(config)
    }
}
