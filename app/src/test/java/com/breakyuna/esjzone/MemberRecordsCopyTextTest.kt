package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.MemberReply
import com.breakyuna.esjzone.novellibrary.user.SystemMessage
import com.breakyuna.esjzone.novellibrary.user.copyText
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberRecordsCopyTextTest {

    @Test
    fun systemMessageJoinsTitleAndTime() {
        assertEquals(
            "等級 F級 Lv2 升級為 F級 Lv3\n2026-09-25 10:45:15",
            SystemMessage("等級 F級 Lv2 升級為 F級 Lv3", "2026-09-25 10:45:15").copyText()
        )
    }

    @Test
    fun systemMessageSkipsBlankTime() {
        assertEquals("系統維護通知", SystemMessage("系統維護通知", "  ").copyText())
        assertEquals("", SystemMessage("  ", null).copyText())
    }

    @Test
    fun replyJoinsBodyAndMetadataButOmitsUrl() {
        val reply = MemberReply(
            content = "謝謝分享",
            url = "/forum/1747244006/427380.html?#comment-3379459",
            subject = "62.族谱",
            replierName = "alice",
            replierUrl = "/my/profile?uid=1",
            date = "2026-10-30 20:34:11"
        )
        assertEquals("謝謝分享\n62.族谱\nalice\n2026-10-30 20:34:11", reply.copyText())
    }

    @Test
    fun replySkipsBlankContentAndKeepsMetadata() {
        val reply = MemberReply(
            content = "  ",
            url = "/forum/1/2.html",
            subject = "主旨",
            replierName = null,
            replierUrl = null,
            date = "2026-10-30 20:34:11"
        )
        assertEquals("主旨\n2026-10-30 20:34:11", reply.copyText())
    }

    @Test
    fun experienceRecordKeepsNoteExpTimeAndExtras() {
        val record = ExperienceRecord(
            time = "2026-09-01 12:00:00",
            exp = "+12",
            note = "發表評論",
            extras = listOf("評論", "  ", "bonus")
        )
        assertEquals("發表評論\n+12\n2026-09-01 12:00:00\n評論\nbonus", record.copyText())
    }

    @Test
    fun experienceRecordDoesNotRepeatTimeWhenItIsThePrimaryLine() {
        val record = ExperienceRecord(
            time = "2026-09-01 12:00:00",
            exp = "3",
            note = null
        )
        assertEquals("2026-09-01 12:00:00\n3", record.copyText())
    }

    @Test
    fun emptyExperienceRecordCopiesNothing() {
        assertEquals("", ExperienceRecord(time = null, exp = null, note = null).copyText())
    }
}
