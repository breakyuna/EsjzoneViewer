package com.breakyuna.esjzone.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.jvm.Transient

/**
 * Keeps a Compose state linked while the app is running, but only writes its
 * plain value when a Voyager Screen is serialized by Android.
 */
class ChapterStateHolder(initialValue: Chapter? = null) : Serializable {
    private var savedValue: Chapter? = initialValue

    @Transient
    private var linkedState: MutableState<Chapter?>? = null

    constructor(source: MutableState<Chapter?>) : this(source.value) {
        linkedState = source
    }

    fun state(): MutableState<Chapter?> {
        linkedState?.let { return it }
        return mutableStateOf(savedValue).also { linkedState = it }
    }

    @Throws(IOException::class)
    private fun writeObject(output: ObjectOutputStream) {
        savedValue = linkedState?.value ?: savedValue
        output.defaultWriteObject()
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        linkedState = null
    }
}

/** See [ChapterStateHolder] for why the Compose state is transient. */
class BooleanStateHolder(initialValue: Boolean = false) : Serializable {
    private var savedValue: Boolean = initialValue

    @Transient
    private var linkedState: MutableState<Boolean>? = null

    constructor(source: MutableState<Boolean>) : this(source.value) {
        linkedState = source
    }

    fun state(): MutableState<Boolean> {
        linkedState?.let { return it }
        return mutableStateOf(savedValue).also { linkedState = it }
    }

    @Throws(IOException::class)
    private fun writeObject(output: ObjectOutputStream) {
        savedValue = linkedState?.value ?: savedValue
        output.defaultWriteObject()
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        linkedState = null
    }
}
