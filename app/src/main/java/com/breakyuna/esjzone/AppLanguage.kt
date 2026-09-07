package com.breakyuna.esjzone

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
