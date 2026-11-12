package com.brian.dialbuddy.domain

data class ContactField(
    val label: String,
    val value: String,
)

data class ContactPanel(
    val name: String,
    val fields: List<ContactField>,
)

fun contactSwipeEnabled(panel: ContactPanel?): Boolean = panel != null

fun contactPanel(
    name: String?,
    phones: List<ContactField>,
    emails: List<ContactField>,
    note: String?,
    noteLabel: String,
    phoneLabel: String,
    emailLabel: String,
): ContactPanel? {
    val fields = buildList {
        addAll(distinctValues(phones, phoneLabel, ::samePhone))
        addAll(distinctValues(emails, emailLabel) { left, right ->
            left.trim().equals(right.trim(), ignoreCase = true)
        })
        val notes = mergedNote(listOfNotNull(note))
        if (notes != null) add(ContactField(noteLabel.trim(), notes))
    }
    val title = name?.trim().orEmpty()
    if (title.isEmpty() && fields.isEmpty()) return null
    return ContactPanel(title, fields)
}

fun mergedNote(parts: List<String>): String? {
    val text = parts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")
    return text.ifEmpty { null }
}

private fun distinctValues(
    fields: List<ContactField>,
    fallback: String,
    same: (String, String) -> Boolean,
): List<ContactField> {
    val kept = ArrayList<ContactField>()
    for (field in fields) {
        val value = field.value.trim()
        if (value.isEmpty()) continue
        if (kept.any { same(it.value, value) }) continue
        kept += ContactField(field.label.trim().ifEmpty { fallback }, value)
    }
    return kept
}

internal fun samePhone(left: String, right: String): Boolean {
    val a = left.filter { it.isDigit() }
    val b = right.filter { it.isDigit() }
    if (a.isEmpty() || b.isEmpty()) return left.trim().equals(right.trim(), ignoreCase = true)
    if (a == b) return true
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    return shorter.length >= 7 && longer.endsWith(shorter)
}
