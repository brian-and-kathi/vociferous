package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPanelTest {
    @Test
    fun aSavedCallerCanBeSwipedOpen() {
        val panel = contactPanel(
            name = "Ada Lovelace",
            phones = listOf(ContactField("Mobile", "(555) 010-1000")),
            emails = listOf(ContactField("Home", "ada@example.com")),
            note = "Evening calls",
            noteLabel = "Notes",
            phoneLabel = "Phone",
            emailLabel = "Email",
        )
        assertTrue(contactSwipeEnabled(panel))
        assertEquals("Ada Lovelace", panel?.name)
        assertEquals(
            listOf(
                ContactField("Mobile", "(555) 010-1000"),
                ContactField("Home", "ada@example.com"),
                ContactField("Notes", "Evening calls"),
            ),
            panel?.fields,
        )
    }

    @Test
    fun noSavedContactLeavesTheCallScreenAlone() {
        assertFalse(contactSwipeEnabled(null))
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
        assertTrue(contactSwipeEnabled(panel))
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
        )
        assertEquals(
            listOf(
                ContactField("Mobile", "+1 (555) 010-1000"),
                ContactField("Other", "555-0199"),
                ContactField("Phone", "123"),
                ContactField("Home", "Ada@Example.com"),
            ),
            panel?.fields,
        )
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
