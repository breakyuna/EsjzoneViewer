package com.breakyuna.esjzone.util

fun String.toHexInt(): Int {
    return this.toInt(16)
}

fun String.toHexUInt(): UInt {
    return this.toUInt(16)
}

fun String.toHexIntOrNull(): Int? {
    return this.toIntOrNull(16)
}

fun String.toHexUIntOrNull(): UInt? {
    return this.toUIntOrNull(16)
}

fun String.removeLeft(length: Int): String {
    if (this.length <= length) return ""
    return this.substring(length)
}

fun String.removeRight(length: Int): String {
    if (this.length <= length) return ""
    return this.substring(0, this.length - length)
}

fun String.removeBefore(index: Int, keepIndex: Boolean): String {
    val targetIndex = if (keepIndex) index else index + 1
    if (targetIndex < 0 || targetIndex > this.length) return ""
    return this.substring(targetIndex)
}
