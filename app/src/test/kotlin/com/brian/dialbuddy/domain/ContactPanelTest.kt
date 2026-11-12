package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPanelTest {
    @Test
    fun aSavedCallerCanOpenTheSheetOnceTheCallIsConnected() {
        val panel = contactPanel(
            name = "Ada Lovelace",
            phones = listOf(ContactField("Mobile", "(555) 010-1000")),
            emails = listOf(ContactField("Home", "ada@example.com")),
            note = "Evening calls",
            noteLabel = "Notes",
            phoneLabel = "Phone",
            emailLabel = "Email",
        )
        assertTrue(contactSheetAvailable(ContactAccess.Allowed, panel, ringing = false))
        assertFalse(contactSheetAvailable(ContactAccess.Allowed, panel, ringing = true))
        assertEquals(ContactNotice.None, contactNotice(ContactAccess.Allowed, settled = true, hasPanel = true))
        assertEquals("Ada Lovelace", panel?.name)
        assertEquals(
            listOf(
                ContactField("Mobile", "(555) 010-1000"),
                ContactField("Home email", "ada@example.com"),
                ContactField("Notes", "Evening calls"),
            ),
            panel?.fields,
        )
    }

    @Test
    fun noSavedContactLeavesTheCallScreenAlone() {
        assertFalse(contactSheetAvailable(ContactAccess.Allowed, null, ringing = false))
        assertEquals(
            ContactNotice.NoContact,
            contactNotice(ContactAccess.Allowed, settled = true, hasPanel = false),
        )
        assertNull(
            contactPanel(
                name = "  ",
                phones = listOf(ContactField("Mobile", " ")),
                emails = emptyList(),
                note = "  ",
                noteLabel = "Notes",
                phoneLabel = "Phone",
                emailLabel = "Email",
            ),
        )
        assertNull(contactPanel(null, emptyList(), emptyList(), null, "Notes", "Phone", "Email"))
    }

    @Test
    fun aNameAloneIsEnoughToOpenThePanel() {
        val panel = contactPanel("Grace", emptyList(), emptyList(), null, "Notes", "Phone", "Email")
        assertTrue(contactSheetAvailable(ContactAccess.Allowed, panel, ringing = false))
        assertEquals("Grace", panel?.name)
        assertEquals(emptyList<ContactField>(), panel?.fields)
    }

    @Test
    fun duplicateNumbersAndEmailsCollapseToOneLine() {
        val panel = contactPanel(
            name = "Ada",
            phones = listOf(
                ContactField("Mobile", "+1 (555) 010-1000"),
                ContactField("Work", "5550101000"),
                ContactField("Other", "555-0199"),
                ContactField("", "123"),
            ),
            emails = listOf(
                ContactField("Home", "Ada@Example.com"),
                ContactField("Work", "ada@example.com"),
                ContactField(" ", "  "),
            ),
            note = null,
            noteLabel = "Notes",
            phoneLabel = "Phone",
            emailLabel = "Email",
            phonesMatch = { left, right ->
                val a = left.filter { it.isDigit() }
                val b = right.filter { it.isDigit() }
                a.isNotEmpty() && a == b
            },
        )
        assertEquals(
            listOf(
                ContactField("Mobile", "+1 (555) 010-1000"),
                ContactField("Work", "5550101000"),
                ContactField("Other", "555-0199"),
                ContactField("Phone", "123"),
                ContactField("Home email", "Ada@Example.com"),
            ),
            panel?.fields,
        )
    }

    @Test
    fun theFirstNumberWinsWhenTheMatcherCallsThemTheSame() {
        val panel = contactPanel(
            name = "Ada",
            phones = listOf(
                ContactField("Mobile", "+1 (555) 010-1000"),
                ContactField("Work", "5550101000"),
            ),
            emails = emptyList(),
            note = null,
            noteLabel = "Notes",
            phoneLabel = "Phone",
            emailLabel = "Email",
            phonesMatch = { _, _ -> true },
        )
        assertEquals(listOf(ContactField("Mobile", "+1 (555) 010-1000")), panel?.fields)
    }

    @Test
    fun aShortTailDoesNotCollapseADifferentNumber() {
        val panel = contactPanel(
            name = "Ada",
            phones = listOf(
                ContactField("Mobile", "5550100"),
                ContactField("Work", "9995550100"),
            ),
            emails = emptyList(),
            note = null,
            noteLabel = "Notes",
            phoneLabel = "Phone",
            emailLabel = "Email",
            phonesMatch = { left, right -> left.filter { it.isDigit() } == right.filter { it.isDigit() } },
        )
        assertEquals(
            listOf(
                ContactField("Mobile", "5550100"),
                ContactField("Work", "9995550100"),
            ),
            panel?.fields,
        )
    }

    @Test
    fun contactsAccessOffIsNotReportedAsMissingContactInfo() {
        assertEquals(
            ContactNotice.AccessOff,
            contactNotice(ContactAccess.Off, settled = true, hasPanel = false),
        )
        assertEquals(
            ContactNotice.None,
            contactNotice(ContactAccess.Off, settled = false, hasPanel = false),
        )
        assertEquals(
            ContactNotice.None,
            contactNotice(ContactAccess.Unknown, settled = false, hasPanel = false),
        )
        assertFalse(contactSheetAvailable(ContactAccess.Off, null, ringing = false))
        assertFalse(contactSheetAvailable(ContactAccess.Unknown, null, ringing = false))
    }

    @Test
    fun anEmailTypeIsNamedAsThatKindOfEmail() {
        assertEquals("Email", emailRowLabel("", "Email"))
        assertEquals("Email", emailRowLabel("Email", "Email"))
        assertEquals("Home email", emailRowLabel("Home", "Email"))
        assertEquals("Work email", emailRowLabel("Work", "Email"))
        assertEquals("Home email", emailRowLabel("Home email", "Email"))
    }

    @Test
    fun blankNotesCollapseAndRepeatedNotesStayOneField() {
        assertNull(mergedNote(listOf(" ", "")))
        assertEquals("Evening calls", mergedNote(listOf("  Evening calls  ", "", "Evening calls")))
        assertEquals(
            "Daytime\n\nAfter six",
            mergedNote(listOf("Daytime", "After six")),
        )
    }
}
