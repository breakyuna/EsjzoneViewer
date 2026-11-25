package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphTest {
    @Test
    fun standaloneNestedAndMultipleImagesAreExtracted() {
        val anchorWrapped = analyseComponents(Jsoup.parse(
            "<div><p><a href=\"https://example.com/view\"><img src=\"https://example.com/test.jpg\" alt=\"pic\"></a></p></div>"
        ).testResolve())
        assertEquals(1, anchorWrapped.size)
        assertEquals("https://example.com/test.jpg", (anchorWrapped.single() as ImageComponent).url)

        val direct = analyseComponents(Jsoup.parse(
            "<div><img src=\"https://example.com/direct.jpg\"></div>"
        ).testResolve())
        assertEquals(1, direct.size)
        assertEquals("https://example.com/direct.jpg", (direct.single() as ImageComponent).url)

        val multiple = analyseComponents(Jsoup.parse(
            "<div><p><img src=\"https://example.com/1.jpg\"><img src=\"https://example.com/2.jpg\"></p></div>"
        ).testResolve())
        assertEquals(listOf("https://example.com/1.jpg", "https://example.com/2.jpg"),
            multiple.map { (it as ImageComponent).url })
    }

    @Test
    fun mixedTextAndImage_preservesBothInOrder() {
        val element = Jsoup.parse("<div><p>前缀文字<img src=\"https://example.com/mid.jpg\">后缀文字</p></div>").testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertEquals("前缀文字", (components[0] as TextComponent).text)
        assertEquals("https://example.com/mid.jpg", (components[1] as ImageComponent).url)
        assertEquals("后缀文字", (components[2] as TextComponent).text)
    }

    @Test
    fun sectionWithNestedParagraphs_extractsAllParagraphsSeparately() {
        val element = Jsoup.parse(
            "<div><section><p>段落一</p><p>段落二</p><p>段落三</p></section></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertTrue(components.all { it is TextComponent })
        assertEquals("段落一", (components[0] as TextComponent).text)
        assertEquals("段落二", (components[1] as TextComponent).text)
        assertEquals("段落三", (components[2] as TextComponent).text)
    }

    @Test
    fun nestedContainersAndLooseText_preservesAllParagraphsInOrder() {
        val element = Jsoup.parse(
            "<div><p><br></p><section><p>前文</p><div><p>嵌套内容</p></div><p>后文</p></section></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(4, components.size)
        assertEquals("\n", (components[0] as TextComponent).text)
        assertEquals("前文", (components[1] as TextComponent).text)
        assertEquals("嵌套内容", (components[2] as TextComponent).text)
        assertEquals("后文", (components[3] as TextComponent).text)
    }

    @Test
    fun looseTextAlongsideBlockElements_isPreserved() {
        val element = Jsoup.parse(
            "<div>提示信息<br>请注意<p>第一章</p><p>第二章</p></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertTrue((components[0] as TextComponent).text.contains("提示信息"))
        assertEquals("第一章", (components[1] as TextComponent).text)
        assertEquals("第二章", (components[2] as TextComponent).text)
    }
}

private fun Document.testResolve(): Element {
    return this.getElementsByTag("html")[0].getElementsByTag("body")[0].getElementsByTag("div")[0]
}
