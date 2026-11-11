package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.BackupGroups
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.ReadingStat
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.network.features.StoredCloudView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Release builds run R8 full mode. Classes that Gson persists must keep stable
 * JSON names (@SerializedName on every field) and have a keep rule so R8 does
 * not strip their fields or make them abstract.
 */
class GsonPersistenceContractTest {
    private val gson = Gson()

    private val persisted = listOf(
        StoredCloudView::class.java,
        ReadingStat::class.java,
        SearchHistory::class.java,
        Bookmark::class.java,
        BackupGroups::class.java,
        Class.forName("com.breakyuna.esjzone.backup.BackupShelfBook"),
        Class.forName("com.breakyuna.esjzone.offline.NovelDownloadStore\$DirectoryHints")
    )

    @Test
    fun everyPersistedFieldHasItsOwnNameAsSerializedName() {
        persisted.forEach { type ->
            val fields = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            assertTrue(type.name, fields.isNotEmpty())
            fields.forEach { field ->
                val annotation = field.getAnnotation(SerializedName::class.java)
                assertNotNull("${type.name}.${field.name}", annotation)
                assertEquals("${type.name}.${field.name}", field.name, annotation.value)
            }
        }
    }

    @Test
    fun releaseRulesKeepEveryPersistedClass() {
        val rules = File("proguard-rules.pro").readLines().map(String::trim)
        persisted.forEach { type ->
            assertTrue(type.name, rules.any { it.startsWith("-keep class ${type.name} {") })
        }
    }

    @Test
    fun localBackupRowsExportedByOlderReleasesAreRead() {
        // Key names taken from the mapping.txt of a v0.2.5 release build.
        val reading = gson.fromJson("""[{"a":"2026-11-01","b":"id:12","c":"書名","d":600}]""",
            Array<ReadingStat>::class.java).single()
        assertEquals(ReadingStat("2026-11-01", "id:12", "書名", 600L), reading)
        val search = gson.fromJson("""[{"a":3,"b":"魔法","c":"2026-11-01 10:00"}]""",
            Array<SearchHistory>::class.java).single()
        assertEquals(SearchHistory(3, "魔法", "2026-11-01 10:00"), search)
        val groups = gson.fromJson("""{"a":["收藏"],"b":{"id:12":"收藏"}}""", BackupGroups::class.java)
        assertEquals(BackupGroups(listOf("收藏"), mapOf("id:12" to "收藏")), groups)
        val bookmark = LocalBackup.decodeBookmarks(JsonParser.parseString(
            """[{"a":"https://www.esjzone.cc/forum/12/1.html","b":"12","c":"書名","d":"第一章","e":100}]"""
        )).single()
        assertEquals("https://www.esjzone.cc/forum/12/1.html", bookmark.chapterUrl)
        assertEquals("12", bookmark.novelId)
        assertEquals("書名", bookmark.novelName)
        assertEquals("第一章", bookmark.chapterName)
        assertEquals(100L, bookmark.createdAt)
        assertNull(bookmark.anchor)
        assertTrue(bookmark.id.isNotBlank())
    }

    @Test
    fun localBackupRowsUseTheirFieldNames() {
        val reading = gson.toJsonTree(ReadingStat("2026-11-01", "id:12", "書名", 600L)).asJsonObject
        assertEquals(setOf("date", "bookKey", "bookName", "durationMs"), reading.keySet())
        val search = gson.toJsonTree(SearchHistory(3, "魔法", "t")).asJsonObject
        assertEquals(setOf("index", "keyword", "time"), search.keySet())
        val groups = gson.toJsonTree(BackupGroups(listOf("收藏"), emptyMap())).asJsonObject
        assertEquals(setOf("names", "members"), groups.keySet())
        val current = gson.toJsonTree(ReadingStat("2026-11-02", "id:34", "名", 1L))
        assertEquals(ReadingStat("2026-11-02", "id:34", "名", 1L), gson.fromJson(current, ReadingStat::class.java))
    }

    @Test
    fun downloadDirectoryHintsFromOlderReleasesAreRead() {
        val type = Class.forName("com.breakyuna.esjzone.offline.NovelDownloadStore\$DirectoryHints")
        val hints = gson.fromJson("""{"a":"/storage/root","b":{"書名":"id:12"}}""", type)
        val root = type.getDeclaredField("root").apply { isAccessible = true }.get(hints)
        val directories = type.getDeclaredField("directories").apply { isAccessible = true }.get(hints)
        assertEquals("/storage/root", root)
        assertEquals(mapOf("書名" to "id:12"), directories)
        assertEquals(setOf("root", "directories"), JsonParser.parseString(gson.toJson(hints)).asJsonObject.keySet())
    }
}
