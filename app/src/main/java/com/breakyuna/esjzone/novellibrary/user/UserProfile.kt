package com.breakyuna.esjzone.novellibrary.user

data class UserProfile(
    val name: String,
    val avatarUrl: String,
    /** Current account experience points, when exposed by the profile page. */
    val exp: Int? = null,
    /** Current account rank, for example `F級 Lv2` or `SSS級 Max`. */
    val level: String? = null,
    /** Registration date text from the member card, for example `2026-08-07`. */
    val registerDate: String? = null,
    /** Experience total required for the next level, when the card tooltip exposes it. */
    val expRequired: Int? = null
)
