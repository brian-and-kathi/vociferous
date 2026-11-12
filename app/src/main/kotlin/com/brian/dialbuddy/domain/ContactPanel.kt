package com.brian.dialbuddy.domain

import android.telephony.PhoneNumberUtils

data class ContactField(
    val label: String,
    val value: String,
)

data class ContactPanel(
    val name: String,
    val fields: List<ContactField>,
)

enum class ContactAccess { Unknown, Off, Allowed }

enum class ContactNotice { None, NoContact, AccessOff }

fun contactNotice(access: ContactAccess, settled: Boolean, hasPanel: Boolean): ContactNotice {
    if (!settled || access == ContactAccess.Unknown) return ContactNotice.None
    if (access == ContactAccess.Off) return ContactNotice.AccessOff
    if (!hasPanel) return ContactNotice.NoContact
    return ContactNotice.None
}

fun contactSheetAvailable(access: ContactAccess, panel: ContactPanel?, ringing: Boolean): Boolean =
    access == ContactAccess.Allowed && panel != null && !ringing

fun contactPanel(
    name: String?,
    phones: List<ContactField>,
    emails: List<ContactField>,
    note: String?,
    noteLabel: String,
    phoneLabel: String,
    emailLabel: String,
    phonesMatch: (String, String) -> Boolean = ::samePhone,
): ContactPanel? {
    val fields = buildList {
        addAll(distinctValues(phones, phoneLabel, phonesMatch))
        addAll(distinctValues(emails.map { it.copy(label = emailRowLabel(it.label, emailLabel)) }, emailLabel) { left, right ->
            left.trim().equals(right.trim(), ignoreCase = true)
        })
        val notes = mergedNote(listOfNotNull(note))
        if (notes != null) add(ContactField(noteLabel.trim(), notes))
    }
    val title = name?.trim().orEmpty()
    if (title.isEmpty() && fields.isEmpty()) return null
    return ContactPanel(title, fields)
}

fun emailRowLabel(typeLabel: String, emailWord: String): String {
    val word = emailWord.trim().ifEmpty { "Email" }
    val type = typeLabel.trim()
    if (type.isEmpty() || type.equals(word, ignoreCase = true)) return word
    val suffix = " ${word.lowercase()}"
    if (type.endsWith(suffix, ignoreCase = true)) {
        val head = type.dropLast(suffix.length).trim()
        return if (head.isEmpty()) word else "$head$suffix"
    }
    return "$type$suffix"
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

internal fun samePhone(left: String, right: String): Boolean = PhoneNumberUtils.compare(left, right)
