package com.breakyuna.esjzone.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
}

fun currentDateString(): String {
    return formatThreadLocal.get()?.format(Date()) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
}

fun String.formattedDate(): Date {
    return try {
        formatThreadLocal.get()?.parse(this) ?: Date(0)
    } catch (_: Exception) {
        Date(0)
    }
}
