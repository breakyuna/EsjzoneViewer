package com.breakyuna.esjzone.ui.page

/** Unknown records remain included and may be resumed; only confirmed R18 is excluded. */
internal fun historyRowIncluded(adultAllowed: Boolean, isAdult: Boolean?): Boolean =
    adultAllowed || isAdult != true

/** List cards and selection wait for verification when adult content is disabled. */
internal fun historyContentVisible(adultAllowed: Boolean, isAdult: Boolean?): Boolean =
    adultAllowed || isAdult == false
