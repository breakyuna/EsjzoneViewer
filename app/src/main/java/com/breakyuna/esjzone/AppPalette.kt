package com.breakyuna.esjzone

import android.os.Build

/** Application color palette; independent from [AppThemeMode] light/dark selection. */
enum class AppPalette {
    INK,
    SAKURA,
    DYNAMIC,
    MONOCHROME;

    companion object {
        val dynamicSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** Palettes offered on this device, in display order. */
        val available: List<AppPalette>
            get() = entries.filter { it != DYNAMIC || dynamicSupported }

        fun fromName(value: String?): AppPalette = entries.firstOrNull { it.name == value } ?: INK
    }
}
