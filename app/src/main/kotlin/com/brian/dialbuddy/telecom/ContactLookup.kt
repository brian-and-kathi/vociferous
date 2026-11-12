package com.brian.dialbuddy.telecom

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.ContactField
import com.brian.dialbuddy.domain.mergedNote

data class ContactMatch(
    val name: String?,
    val photoUri: Uri?,
)

data class ContactProfile(
    val name: String?,
    val photoUri: Uri?,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
    val note: String?,
    val viewUri: Uri? = null,
)

object ContactLookup {
    fun findName(context: Context, number: String): String? = find(context, number)?.name

    fun numberFromPick(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(Phone.NUMBER)
            if (index < 0) return null
            return cursor.getString(index)
        }
        return null
    }

    fun viewUri(context: Context, number: String): Uri? {
        if (number.isBlank()) return null
        val lookup = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            lookup,
            arrayOf(PhoneLookup.CONTACT_ID, PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val idIndex = cursor.getColumnIndex(PhoneLookup.CONTACT_ID)
            val keyIndex = cursor.getColumnIndex(PhoneLookup.LOOKUP_KEY)
            if (idIndex < 0 || keyIndex < 0) return null
            val key = cursor.getString(keyIndex) ?: return null
            return ContactsContract.Contacts.getLookupUri(cursor.getLong(idIndex), key)
        }
        return null
    }

    fun find(context: Context, number: String): ContactMatch? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI, PhoneLookup.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME)
            val photoIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI)
            val thumbIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_THUMBNAIL_URI)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val photo = picture(cursor, photoIndex) ?: picture(cursor, thumbIndex)
            return ContactMatch(name, photo?.let(Uri::parse))
        }
        return null
    }

    fun profile(context: Context, number: String): ContactProfile? {
        if (number.isBlank()) return null
        val row = try {
            lookupRow(context, number)
        } catch (_: SecurityException) {
            return null
        } ?: return null
        val id = row.contactId
        val phones = if (id == null) {
            emptyList()
        } else {
            try {
                phones(context, id)
            } catch (_: SecurityException) {
                emptyList()
            }
        }
        val emails = if (id == null) {
            emptyList()
        } else {
            try {
                emails(context, id)
            } catch (_: SecurityException) {
                emptyList()
            }
        }
        val note = if (id == null) {
            null
        } else {
            try {
                notes(context, id)
            } catch (_: SecurityException) {
                null
            }
        }
        val matched = row.number?.let { value ->
            ContactField(row.numberLabel ?: context.getString(R.string.contact_phone), value)
        }
        return ContactProfile(
            name = row.name,
            photoUri = row.photo,
            phones = phones + listOfNotNull(matched),
            emails = emails,
            note = note,
            viewUri = row.viewUri,
        )
    }

    private data class LookupRow(
        val name: String?,
        val photo: Uri?,
        val contactId: Long?,
        val number: String?,
        val numberLabel: String?,
        val viewUri: Uri?,
    )

    private fun lookupRow(context: Context, number: String): LookupRow? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI,
                PhoneLookup.PHOTO_THUMBNAIL_URI,
                PhoneLookup.CONTACT_ID,
                PhoneLookup.LOOKUP_KEY,
                PhoneLookup.NUMBER,
                PhoneLookup.TYPE,
                PhoneLookup.LABEL,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = text(cursor, cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME))
            val photo = picture(cursor, cursor.getColumnIndex(PhoneLookup.PHOTO_URI))
                ?: picture(cursor, cursor.getColumnIndex(PhoneLookup.PHOTO_THUMBNAIL_URI))
            val idIndex = cursor.getColumnIndex(PhoneLookup.CONTACT_ID)
            val contactId = if (idIndex < 0 || cursor.isNull(idIndex)) null else cursor.getLong(idIndex)
            val lookupKey = text(cursor, cursor.getColumnIndex(PhoneLookup.LOOKUP_KEY))
            val viewUri = if (contactId != null && lookupKey != null) {
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            } else {
                null
            }
            val matched = text(cursor, cursor.getColumnIndex(PhoneLookup.NUMBER))
            val typeIndex = cursor.getColumnIndex(PhoneLookup.TYPE)
            val type = if (typeIndex < 0 || cursor.isNull(typeIndex)) Phone.TYPE_OTHER else cursor.getInt(typeIndex)
            val custom = text(cursor, cursor.getColumnIndex(PhoneLookup.LABEL))
            val label = Phone.getTypeLabel(context.resources, type, custom).toString().trim().ifEmpty { null }
            return LookupRow(name, photo?.let(Uri::parse), contactId, matched, label, viewUri)
        }
        return null
    }

    private fun phones(context: Context, contactId: Long): List<ContactField> =
        labeled(
            context,
            Phone.CONTENT_URI,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            contactId,
            Phone.TYPE_OTHER,
        ) { type, custom -> Phone.getTypeLabel(context.resources, type, custom) }

    private fun emails(context: Context, contactId: Long): List<ContactField> =
        labeled(
            context,
            Email.CONTENT_URI,
            Email.ADDRESS,
            Email.TYPE,
            Email.LABEL,
            contactId,
            Email.TYPE_OTHER,
        ) { type, custom -> Email.getTypeLabel(context.resources, type, custom) }

    private fun labeled(
        context: Context,
        uri: Uri,
        valueColumn: String,
        typeColumn: String,
        labelColumn: String,
        contactId: Long,
        missingType: Int,
        resolve: (Int, CharSequence?) -> CharSequence,
    ): List<ContactField> {
        val fields = ArrayList<ContactField>()
        context.contentResolver.query(
            uri,
            arrayOf(valueColumn, typeColumn, labelColumn),
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val valueIndex = cursor.getColumnIndex(valueColumn)
            val typeIndex = cursor.getColumnIndex(typeColumn)
            val labelIndex = cursor.getColumnIndex(labelColumn)
            while (cursor.moveToNext()) {
                val value = text(cursor, valueIndex) ?: continue
                val type = if (typeIndex < 0 || cursor.isNull(typeIndex)) missingType else cursor.getInt(typeIndex)
                val custom = text(cursor, labelIndex)
                val label = resolve(type, custom).toString().trim()
                fields += ContactField(label, value)
            }
        }
        return fields
    }

    private fun notes(context: Context, contactId: Long): String? {
        val parts = ArrayList<String>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(Note.NOTE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), Note.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(Note.NOTE)
            while (cursor.moveToNext()) {
                text(cursor, index)?.let { parts += it }
            }
        }
        return mergedNote(parts)
    }

    private fun text(cursor: Cursor, index: Int): String? {
        if (index < 0 || cursor.isNull(index)) return null
        return cursor.getString(index)?.takeIf { it.isNotBlank() }
    }

    private fun picture(cursor: Cursor, index: Int): String? {
        if (index < 0 || cursor.isNull(index)) return null
        return cursor.getString(index)?.takeIf { it.isNotBlank() }
    }
}
