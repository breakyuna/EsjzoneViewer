package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.features.ExperienceRecordException
import com.breakyuna.esjzone.network.features.experienceRecordUrl
import com.breakyuna.esjzone.network.features.hasProfileMarker
import com.breakyuna.esjzone.network.features.parseExperienceRecordPage
import com.breakyuna.esjzone.network.features.parseMemberReplies
import com.breakyuna.esjzone.network.features.parseSystemMessages
import com.breakyuna.esjzone.network.features.profileAvatarUrl
import com.breakyuna.esjzone.network.features.profileExperience
import com.breakyuna.esjzone.network.features.profileExperienceRequired
import com.breakyuna.esjzone.network.features.profileLevel
import com.breakyuna.esjzone.network.features.profileName
import com.breakyuna.esjzone.network.features.profileRegisterDate
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.forumLinkRoute
import com.breakyuna.esjzone.ui.page.ExperienceRecordsPage
import com.breakyuna.esjzone.ui.page.ExperienceRecordsState
import com.breakyuna.esjzone.ui.page.MemberRepliesPage
import com.breakyuna.esjzone.ui.page.SystemMessagesPage
import com.breakyuna.esjzone.ui.page.afterFailure
import com.breakyuna.esjzone.ui.page.appendPage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberCenterRecordsTest {
    private val base = "https://www.esjzone.cc"

    private fun page(body: String, path: String) = Jsoup.parse(
        "<html><head><title>ESJ Zone</title></head><body><section class=\"container\">" +
            "<div class=\"row member-info\">$body</div></section></body></html>",
        "$base$path"
    )

    // ---- profile ----

    private val profileHtml = """
        <div class="col-lg-4"><aside class="user-info-wrapper">
          <div class="user-cover"><div class="info-label" data-toggle="tooltip"
            data-original-title="F級 Lv3：16122/30000">16122 exp</div></div>
          <form class="user-info"><div class="user-data">
            <h4> breakyuna</h4><span>註冊日期 2026-08-07</span><span>F級 Lv3</span>
          </div></form>
        </aside></div>
    """

    private fun fixture(name: String) = Jsoup.parse(
        requireNotNull(javaClass.getResource("/esj/$name")) { "missing fixture: $name" }
            .openStream().bufferedReader().use { it.readText() },
        "https://example.test/"
    )

    @Test fun profileMetadata_parsesRegisterDateAndExperienceRequiredWithFallbacks() {
        val document = page(profileHtml, "/my/profile")
        assertEquals("2026-08-07", profileRegisterDate(document))
        assertEquals(16122, profileExperience(document))
        assertEquals(30000, profileExperienceRequired(document))

        val titleFallback = page(
            """<div class="user-cover"><div class="info-label" title="F級 Lv3：16,122/30,000">16122 exp</div></div>""",
            "/my/profile"
        )
        assertEquals(30000, profileExperienceRequired(titleFallback))

        val simplified = page("""<div class="user-data"><span>注册日期：2026-08-07</span></div>""", "/my/profile")
        assertEquals("2026-08-07", profileRegisterDate(simplified))

        val missing = page(
            """<div class="user-data"><h4>a</h4><span>F級 Lv3</span><span>註冊日期 </span></div>
               <div class="user-cover"><div class="info-label">16122 exp</div></div>""",
            "/my/profile"
        )
        assertNull(profileRegisterDate(missing))
        assertNull(profileExperienceRequired(missing))
    }

    @Test fun profileFixtures_preserveNameAvatarExperienceLevelAndAuthMarker() {
        val profile = fixture("profile.html")
        assertEquals("Fixture Profile User", profileName(profile))
        assertEquals("/uploads/fixture-profile.jpg", profileAvatarUrl(profile))
        assertEquals(7264, profileExperience(profile))
        assertEquals("F級 Lv2", profileLevel(profile))

        val authorized = fixture("authorized.html")
        assertTrue(hasProfileMarker(authorized))
        assertTrue(
            PageResponsePolicy.validate(
                statusCode = 200,
                body = authorized.html(),
                requestedUrl = "https://example.test/my/profile",
                kind = PageKind.ACCOUNT
            ).trusted
        )

        val guest = Jsoup.parse("<html><body><main>Guest</main></body></html>")
        assertEquals("User", profileName(guest))
        assertEquals("", profileAvatarUrl(guest))
        assertFalse(hasProfileMarker(guest))
    }

    // ---- /my/sys ----

    @Test fun systemMessages_parseTitlesTimesAndEmptyTables() {
        val document = page("""
            <div class="col-lg-8"><div class="table-responsive mb-0"><table class="table"><tbody>
              <tr><td><h5 class="product-title">等級 F級 Lv2 升級為 F級 Lv3</h5>
                <div class="book-ep"><span>2026-09-25 10:45:15</span></div></td></tr>
              <tr><td><h5 class="product-title">第二條</h5></td></tr>
            </tbody></table></div></div>
        """, "/my/sys")
        val messages = parseSystemMessages(document)
        assertEquals(2, messages.size)
        assertEquals("等級 F級 Lv2 升級為 F級 Lv3", messages[0].title)
        assertEquals("2026-09-25 10:45:15", messages[0].time)
        assertNull(messages[1].time)

        val empty = page("""
            <div class="col-lg-8"><div class="table-responsive mb-0"><table class="table"><tbody>
              <tr><td>目前尚無任何資料</td></tr>
            </tbody></table></div></div>
        """, "/my/sys")
        assertTrue(parseSystemMessages(empty).isEmpty())
        assertTrue(parseSystemMessages(page("", "/my/sys")).isEmpty())
    }

    // ---- /my/reply ----

    private fun replyRow(id: Int, content: String, extra: String = """
           <div class="book-ep">主旨：62.族谱</div>
           <div class="book-update">回覆人：<a href="/my/profile?uid=12" target="_blank">someone</a></div>
           <div class="book-update">回覆日期：2026-10-30 20:34:11</div>""") = """
        <tr><td><div class="product-item"><div class="product-info">
           <h5 class="product-title"><a href="/forum/1747244006/427380.html?#comment-$id" target="_blank">$content</a></h5>
           $extra
        </div></div></td></tr>"""

    private fun replySection(page: Int, rows: String, hidden: Boolean = false) = """
        <section class="reply-section reply-page-$page" style="${if (hidden) "display: none;" else ""}">
          <div class="table-responsive mb-0"><table class="table"><tbody>$rows</tbody></table></div>
        </section>"""

    @Test fun replies_parseAllFieldsAndMultiPageOrder() {
        val singleDoc = page("""
            <div class="col-lg-8"><div id="page-selection-top"><ul class="pagination bootpag">
              <li data-lp="1" class="active"><a href="javascript:void(0);">1</a></li></ul></div>
            ${replySection(1, replyRow(3379459, "确实，我看到上面一堆人还在纠结这点事都笑了…"))}
            </div>
        """, "/my/reply")
        val reply = parseMemberReplies(singleDoc).single()
        assertEquals("确实，我看到上面一堆人还在纠结这点事都笑了…", reply.content)
        assertEquals("$base/forum/1747244006/427380.html?#comment-3379459", reply.url)
        assertEquals("62.族谱", reply.subject)
        assertEquals("someone", reply.replierName)
        assertEquals("$base/my/profile?uid=12", reply.replierUrl)
        assertEquals("2026-10-30 20:34:11", reply.date)
        assertEquals("/detail/1747244006.html", reply.novelDetailPath)

        val multiPage = page(
            replySection(1, replyRow(1, "a") + replyRow(2, "b")) +
                replySection(2, replyRow(3, "c"), hidden = true) +
                replySection(3, replyRow(4, "d"), hidden = true),
            "/my/reply"
        )
        assertEquals(listOf("a", "b", "c", "d"), parseMemberReplies(multiPage).map { it.content })
    }

    @Test fun replies_handleMissingMetadataEmptySectionsAndShortForumIds() {
        val missingMeta = parseMemberReplies(page(replySection(1, replyRow(9, "only text", extra = "")), "/my/reply")).single()
        assertNull(missingMeta.subject)
        assertNull(missingMeta.replierName)
        assertNull(missingMeta.date)

        assertTrue(parseMemberReplies(page(replySection(1, ""), "/my/reply")).isEmpty())
        assertTrue(parseMemberReplies(page("", "/my/reply")).isEmpty())

        val shortBoard = page(replySection(1, """
            <tr><td><div class="product-item"><div class="product-info">
              <h5 class="product-title"><a href="/forum/12/3456.html">board reply</a></h5>
            </div></div></td></tr>"""), "/my/reply")
        assertNull(parseMemberReplies(shortBoard).single().novelDetailPath)
    }

    // ---- /inc/mem_record_data.php ----

    @Test fun recordUrlAndTotals_matchBootstrapTableContract() {
        assertEquals(
            "$base/inc/mem_record_data.php?search=type%3A3&order=desc&offset=30&limit=30",
            experienceRecordUrl(base, ExperienceRecordType.COMMENT, 30)
        )
        assertEquals(0, parseExperienceRecordPage("""{"status":200,"total":"0","rows":[]}""").total)
        assertEquals(45, parseExperienceRecordPage("""{"status":200,"total":45,"rows":[]}""").total)
        assertEquals(0, parseExperienceRecordPage("""{"status":"200","total":"x","rows":[]}""").total)
    }

    @Test fun recordStatusOtherThan200Fails() {
        assertThrows(ExperienceRecordException::class.java) {
            parseExperienceRecordPage("""{"status":301,"total":0,"rows":[]}""")
        }
        assertThrows(ExperienceRecordException::class.java) {
            parseExperienceRecordPage("""{"total":0,"rows":[]}""")
        }
        assertThrows(ExperienceRecordException::class.java) {
            parseExperienceRecordPage("<html><body>login</body></html>")
        }
    }

    @Test fun recordRowsAreParsedLeniently() {
        val page = parseExperienceRecordPage("""
            {"status":200,"total":"3","rows":[
              {"cdate":"2026-10-30 20:34:11","exp":"+5","non":"<a href='/forum/1/2.html'>評論</a>","id":7},
              {"cdate":"2026-10-29","exp":3,"unknown":null,"blank":""},
              ["2026-10-28","1","note","extra"],
              {}
            ]}
        """)
        assertEquals(3, page.total)
        assertEquals(3, page.rows.size)
        assertEquals(ExperienceRecord("2026-10-30 20:34:11", "+5", "評論", listOf("id: 7")), page.rows[0])
        assertEquals(ExperienceRecord("2026-10-29", "3", null, emptyList()), page.rows[1])
        assertEquals(ExperienceRecord("2026-10-28", "1", "note", listOf("extra")), page.rows[2])
    }

    @Test fun recordPagingAndFailedRefresh_manageVisibleRowsAndEndState() {
        val rows = List(30) { ExperienceRecord("t$it", "1", null) }
        val first = ExperienceRecordsState(type = ExperienceRecordType.COMMENT).appendPage(rows, total = 45)
        assertTrue(first.canLoadMore)
        val second = first.appendPage(rows.take(15), total = 45)
        assertEquals(45, second.rows.size)
        assertFalse(second.canLoadMore)
        assertFalse(ExperienceRecordsState().appendPage(emptyList(), total = 0).canLoadMore)
        assertFalse(ExperienceRecordsState().appendPage(rows.take(5), total = 100).canLoadMore)

        val failed = first.copy(loading = true, refreshing = true).afterFailure(LoadFailureKind.NETWORK, refresh = true)
        assertEquals(rows, failed.rows)
        assertTrue(failed.loaded)
        assertTrue(failed.refreshFailed)
        assertFalse(failed.refreshing)
        assertEquals(ExperienceRecordType.COMMENT, failed.type)
        val refreshed = ExperienceRecordsState(type = ExperienceRecordType.COMMENT).appendPage(rows.take(2), total = 2)
        assertEquals(2, refreshed.rows.size)
        assertFalse(refreshed.refreshFailed)
        assertFalse(first.afterFailure(LoadFailureKind.NETWORK, refresh = false).refreshFailed)
    }

    // ---- links ----

    @Test fun memberCenterLinksOpenNativePages() {
        assertEquals(LegacyRoute.Static(SystemMessagesPage.key), forumLinkRoute("$base/my/sys".toHttpUrl()))
        assertEquals(LegacyRoute.Static(MemberRepliesPage.key), forumLinkRoute("$base/my/reply".toHttpUrl()))
        assertEquals(LegacyRoute.Static(ExperienceRecordsPage.key), forumLinkRoute("$base/my/record".toHttpUrl()))
    }
}
