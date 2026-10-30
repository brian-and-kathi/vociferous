package com.brian.dialbuddy.domain

// Dialer is page 0. Ongoing calls keep their relative order, then incoming calls.
fun <T> orderedCallCards(calls: List<T>, isIncoming: (T) -> Boolean): List<T> =
    calls.filterNot(isIncoming) + calls.filter(isIncoming)

// Stay on the card the user was viewing when a call changes from incoming to ongoing.
// A missing call keeps the current page, clamped to the dialer-plus-calls range.
fun callPageAfterReorder(keys: List<String>, settledKey: String?, currentPage: Int): Int {
    val last = keys.size
    if (settledKey == null) return currentPage.coerceIn(0, last)
    val index = keys.indexOf(settledKey)
    return if (index >= 0) index + 1 else currentPage.coerceIn(0, last)
}
