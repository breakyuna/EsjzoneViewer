package com.breakyuna.esjzone.network

import java.io.Serializable

data class Authorization(
    val ewsKey: String,
    val ewsToken: String,
) : Serializable