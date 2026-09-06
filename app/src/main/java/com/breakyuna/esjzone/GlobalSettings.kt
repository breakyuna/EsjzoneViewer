package com.breakyuna.esjzone

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinThemeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(
    val code: String,
    val titleRes: Int,
    val subtitleRes: Int
) {
    SYSTEM("system", R.string.settings_language_system, R.string.settings_language_system_description),
    SIMPLIFIED_CHINESE("zh-CN", R.string.settings_language_zh_cn, R.string.settings_language_zh_cn_description),
    ENGLISH("en", R.string.settings_language_en, R.string.settings_language_en_description);

    companion object {
        fun fromCode(code: String?): AppLanguage = when (code) {
            "zh-CN", "zh", "zh_CN" -> SIMPLIFIED_CHINESE
            "en" -> ENGLISH
            "system" -> SYSTEM
            else -> SYSTEM
        }
    }
}

object GlobalSettings {

    /** Cache key shared by startup and the settings screen for reader auto-save. */
    const val READER_AUTO_SAVE_KEY = "reader_auto_save"

    val DOMAINS = listOf(
        "www.esjzone.cc",
        "www.esjzone.one"
    )

    private val _adultState = mutableStateOf(true)
    val adult: State<Boolean> get() = _adultState
    private val _adultFlow = MutableStateFlow(true)
    val adultFlow: StateFlow<Boolean> = _adultFlow.asStateFlow()

    private val _themeState = mutableStateOf(CatppuccinThemeType.LATTE_YELLOW)
    val theme: State<CatppuccinThemeType> get() = _themeState
    private val _themeFlow = MutableStateFlow(CatppuccinThemeType.LATTE_YELLOW)
    val themeFlow: StateFlow<CatppuccinThemeType> = _themeFlow.asStateFlow()

    private val _domainState = mutableStateOf(DOMAINS[0])
    val domain: State<String> get() = _domainState
    private val _domainFlow = MutableStateFlow(DOMAINS[0])
    val domainFlow: StateFlow<String> = _domainFlow.asStateFlow()

    private val _languageState = mutableStateOf(AppLanguage.SYSTEM)
    val language: State<AppLanguage> get() = _languageState
    private val _languageFlow = MutableStateFlow(AppLanguage.SYSTEM)
    val languageFlow: StateFlow<AppLanguage> = _languageFlow.asStateFlow()

    private val _readerAutoSaveState = mutableStateOf(false)
    val readerAutoSave: State<Boolean> get() = _readerAutoSaveState
    private val _readerAutoSaveFlow = MutableStateFlow(false)
    val readerAutoSaveFlow: StateFlow<Boolean> = _readerAutoSaveFlow.asStateFlow()

    fun setAdult(value: Boolean) {
        _adultState.value = value
        _adultFlow.value = value
    }

    fun setTheme(value: CatppuccinThemeType) {
        _themeState.value = value
        _themeFlow.value = value
    }

    fun setDomain(value: String) {
        _domainState.value = value
        _domainFlow.value = value
    }

    fun setLanguage(value: AppLanguage) {
        _languageState.value = value
        _languageFlow.value = value
    }

    fun setReaderAutoSave(value: Boolean) {
        _readerAutoSaveState.value = value
        _readerAutoSaveFlow.value = value
    }
}
