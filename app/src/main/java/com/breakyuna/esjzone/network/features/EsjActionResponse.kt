package com.breakyuna.esjzone.network.features

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Favorites and history deletion require an explicit numeric 200 in the body. */
internal fun hasSuccessfulEsjActionStatus(body: String): Boolean = runCatching {
    val root = Json.parseToJsonElement(body) as? JsonObject ?: return@runCatching false
    val status = root["status"] as? JsonPrimitive ?: return@runCatching false
    !status.isString && status.content == "200"
}.getOrDefault(false)
