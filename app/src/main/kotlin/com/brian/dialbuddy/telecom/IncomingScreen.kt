package com.brian.dialbuddy.telecom

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.CallScreeningService
import com.brian.dialbuddy.domain.useUnknownRingtone

class IncomingScreen : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val outcome = try {
            decision(details)
        } catch (_: Exception) {
            Outcome(allow(), silenced = false)
        }
        respondToCall(details, outcome.response)
        if (outcome.silenced) UnknownRinger.syncAttached()
    }

    private fun decision(details: Call.Details): Outcome {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val contactsAllowed = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val inContacts = if (contactsAllowed && number.isNotBlank()) {
            try {
                ContactLookup.find(this, number) != null
            } catch (_: SecurityException) {
                UnknownRinger.forget(number)
                return Outcome(allow(), silenced = false)
            }
        } else {
            false
        }
        val silence = useUnknownRingtone(incoming, number, contactsAllowed, inContacts)
        if (silence) UnknownRinger.expect(number) else UnknownRinger.forget(number)
        return Outcome(
            response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(silence)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build(),
            silenced = silence,
        )
    }

    private fun allow(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    private data class Outcome(val response: CallResponse, val silenced: Boolean)
}
