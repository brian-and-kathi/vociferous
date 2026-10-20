package com.brian.dialbuddy.domain

// North American numbers only. A leading country code 1 is accepted.
// Anything shorter, longer, or outside that plan has no area code here.
fun areaCodeOf(number: String): String? {
    val head = number.substringBefore(',').substringBefore(';')
    val digits = head.filter { it in '0'..'9' }
    val national = when {
        digits.length == 11 && digits.startsWith("1") -> digits.substring(1)
        digits.length == 10 -> digits
        else -> return null
    }
    return normalizeAreaCode(national.substring(0, 3))
}

fun normalizeAreaCode(raw: String): String? {
    val code = raw.trim()
    if (code.length != 3 || code.any { it !in '0'..'9' }) return null
    if (code[0] !in '2'..'9') return null
    return code
}

fun parseAreaCodes(stored: String): List<String> =
    stored.split(',')
        .mapNotNull { part -> normalizeAreaCode(part) }
        .distinct()
        .sorted()

fun formatAreaCodes(codes: Collection<String>): String =
    codes.mapNotNull { normalizeAreaCode(it) }.distinct().sorted().joinToString(",")
