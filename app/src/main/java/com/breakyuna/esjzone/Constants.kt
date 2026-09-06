package com.breakyuna.esjzone

object Constants {

    val MAINTAINERS = listOf(
        "breakyuna"
    )

    val CONTRIBUTORS = listOf(
        "DeeChael",
        "breakyuna"
    )

    val OPEN_SOURCE_LIBRARIES = listOf(
        GithubRepo(
            "https://github.com/square/okhttp",
            "Square",
            "OkHttp",
            "Square’s meticulous HTTP client for the JVM, Android, and GraalVM."
        ),
        GithubRepo(
            "https://developer.android.com/jetpack/androidx/releases/navigation3",
            "AndroidX",
            "Navigation 3",
            "Compose-first type-safe navigation with saveable back stacks."
        ),
        GithubRepo(
            "https://github.com/coil-kt/coil",
            "Coil",
            "Coil",
            "Image loading for Android and Compose Multiplatform."
        ),
        GithubRepo(
            "https://github.com/google/gson",
            "Google",
            "Gson",
            "A Java serialization/deserialization library to convert Java Objects into JSON and back"
        ),
        GithubRepo(
            "https://github.com/jhy/jsoup",
            "Jonathan Hedley",
            "jsoup",
            "jsoup: the Java HTML parser, built for HTML editing, cleaning, scraping, and XSS safety."
        ),
        GithubRepo(
            "https://github.com/code4craft/xsoup",
            "Yihua Huang",
            "xsoup",
            "When jsoup meets XPath."
        ),
    )

}

data class GithubRepo(
    val url: String,
    val owner: String,
    val name: String,
    val description: String
)
