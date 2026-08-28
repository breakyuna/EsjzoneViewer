package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.novellibrary.novel.Category
import org.jsoup.Jsoup

fun EsjzoneClient.getCategories(authorization: Authorization): List<Category> {
    val responseBody = getPage(authorization, EsjzoneUrls.Forum, PageCacheTtl.CATEGORIES)

    val document = Jsoup.parse(responseBody)

    val categories = mutableListOf<Category>()

    for (element in EsjzoneXPaths.Forum.Category.evaluate(document).elements) {
        val name = element.text()
        categories.add(
            Category(
                name,
                element.attr("href"),
                name.contains("r18", ignoreCase = true)
            )
        )
    }

    println("Categories: $categories")

    return categories.toList()
}
