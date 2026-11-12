package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface ExperienceRecordsCache {
    suspend fun read(type: ExperienceRecordType): ExperienceRecordsState?
    fun writer(type: ExperienceRecordType): suspend (ExperienceRecordsState) -> Unit
}

/** Account/category snapshots share the page cache's quota and clear lifecycle. */
internal class PageExperienceRecordsCache(authorization: Authorization) : ExperienceRecordsCache {
    private val accountScope = authorization.takeIf { it.hasCredentials() }
        ?.let(EsjzoneClient::accountScope)

    private fun key(type: ExperienceRecordType): String? =
        accountScope?.let { "experience-records-v1|$it|${type.code}" }

    override suspend fun read(type: ExperienceRecordType): ExperienceRecordsState? =
        withContext(Dispatchers.IO) {
            key(type)?.let(PageCache::readStale)?.let { ExperienceRecordSnapshot.decode(type, it) }
        }

    override fun writer(type: ExperienceRecordType): suspend (ExperienceRecordsState) -> Unit {
        val cacheKey = key(type) ?: return {}
        // A later request or a cache clear makes this token obsolete.
        val token = PageCache.beginWrite(cacheKey)
        return { state ->
            withContext(Dispatchers.IO) {
                PageCache.write(cacheKey, ExperienceRecordSnapshot.encode(state), expected = token)
            }
        }
    }
}

/** Explicit JSON fields avoid reflection-dependent record DTOs in R8 builds. */
internal object ExperienceRecordSnapshot {
    fun encode(state: ExperienceRecordsState): String {
        val rows = JsonArray()
        state.rows.forEach { record ->
            rows.add(JsonObject().apply {
                addProperty("time", record.time)
                addProperty("exp", record.exp)
                addProperty("note", record.note)
                add("extras", JsonArray().apply { record.extras.forEach { add(it) } })
            })
        }
        return JsonObject().apply {
            addProperty("total", state.total)
            addProperty("endReached", state.endReached)
            add("rows", rows)
        }.toString()
    }

    fun decode(type: ExperienceRecordType, json: String): ExperienceRecordsState? = runCatching {
        val snapshot = JsonParser.parseString(json).asJsonObject
        val total = snapshot.get("total").asInt.also { require(it >= 0) }
        val rows = snapshot.getAsJsonArray("rows").map { element ->
            val row = element.asJsonObject
            fun string(name: String): String? = row.get(name)?.takeUnless { it.isJsonNull }?.asString
            ExperienceRecord(
                time = string("time"),
                exp = string("exp"),
                note = string("note"),
                extras = row.getAsJsonArray("extras").map { it.asString }
            )
        }
        ExperienceRecordsState(
            type = type,
            rows = rows,
            total = total,
            loaded = true,
            endReached = snapshot.get("endReached").asBoolean
        )
    }.getOrNull()
}
