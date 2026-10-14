package com.breakyuna.esjzone.network

interface PageableRequester<T> {

    fun isCurrent(): Boolean = true

    fun fallbackFailure(): LoadFailureKind? = null

    fun pages(): Int

    fun more(): List<T>

    fun more(page: Int): List<T>

    fun end(): Boolean

}