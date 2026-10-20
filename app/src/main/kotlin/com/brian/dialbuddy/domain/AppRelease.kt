package com.brian.dialbuddy.domain

fun isNewerRelease(installed: String, published: String): Boolean {
    val current = releaseNumbers(installed)
    val latest = releaseNumbers(published)
    if (current.isEmpty() || latest.isEmpty()) return false
    val count = maxOf(current.size, latest.size)
    for (index in 0 until count) {
        val here = current.getOrElse(index) { 0 }
        val there = latest.getOrElse(index) { 0 }
        if (there != here) return there > here
    }
    return false
}

private fun releaseNumbers(name: String): List<Int> {
    val core = name.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("+")
        .substringBefore("-")
    if (core.isEmpty()) return emptyList()
    return core.split(".").map { part -> part.toIntOrNull() ?: return emptyList() }
}
