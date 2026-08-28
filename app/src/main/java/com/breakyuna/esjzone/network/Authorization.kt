package com.breakyuna.esjzone.network

import java.io.Serializable

data class Authorization(
    val ewsKey: String,
    val ewsToken: String,
    /** Host that issued this session, used to keep sessions isolated per site domain. */
    val domain: String = "",
) : Serializable

/**
 * Returns whether both persisted session cookies can be used for a request.
 * The literal `null` is kept for compatibility with the original cache format.
 */
fun Authorization.hasCredentials(): Boolean =
    ewsKey.isNotBlank() && !ewsKey.equals("null", ignoreCase = true) &&
        ewsToken.isNotBlank() && !ewsToken.equals("null", ignoreCase = true)
