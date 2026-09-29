package com.breakyuna.esjzone

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(value: String?): AppThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
