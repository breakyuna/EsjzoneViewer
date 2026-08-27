package com.breakyuna.esjzone.novellibrary.novel

import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import org.jsoup.nodes.Element
import java.io.Serializable

data class NovelDescription(
    val components: List<Component>
) : Serializable

fun analyseDescription(element: Element): NovelDescription {
    return NovelDescription(
        analyseComponents(element)
    )
}
