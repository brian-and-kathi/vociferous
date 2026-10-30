package com.brian.dialbuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.telecom.ContactLookup
import com.brian.dialbuddy.telecom.OutgoingAllowance
import com.brian.dialbuddy.ui.screens.InCallScreen
import com.brian.dialbuddy.ui.theme.DialBuddyTheme

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by Calls.state.collectAsState()
            var digits by rememberSaveable { mutableStateOf("") }
            val pendingNumber = remember { mutableStateOf<String?>(null) }
            val pendingBrowse = remember { mutableStateOf(false) }
            val contactLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                val number = ContactLookup.numberFromPick(this, uri) ?: return@rememberLauncherForActivityResult
                val kept = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                if (kept.isNotBlank()) digits = kept
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { granted ->
                val number = pendingNumber.value
                pendingNumber.value = null
                if (number != null && granted[Manifest.permission.CALL_PHONE] == true) {
                    placeCall(number)
                } else if (number != null) {
                    Toast.makeText(this, R.string.call_permission_denied, Toast.LENGTH_LONG).show()
                }
                if (pendingBrowse.value) {
                    pendingBrowse.value = false
                    if (granted[Manifest.permission.READ_CONTACTS] == true) {
                        contactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
                    }
                }
            }
            LaunchedEffect(state.calls.isEmpty()) {
                if (state.calls.isEmpty()) finish()
            }
            DialBuddyTheme {
                InCallScreen(
                    state = state,
                    digits = digits,
                    onDigit = { digits += it },
                    onPlus = {
                        digits = if (digits.endsWith("0")) digits.dropLast(1) + "+" else "$digits+"
                    },
                    onBackspace = { digits = digits.dropLast(1) },
                    onCall = {
                        if (digits.isBlank()) return@InCallScreen
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingNumber.value = digits
                            permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                        } else {
                            placeCall(digits)
                        }
                    },
                    onBrowse = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            contactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
                        } else {
                            pendingBrowse.value = true
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                        }
                    },
                )
            }
        }
    }

    private fun placeCall(number: String) {
        OutgoingAllowance.permit(number)
        try {
            val telecom = getSystemService(TelecomManager::class.java)
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (_: SecurityException) {
            OutgoingAllowance.clear()
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show()
        }
    }
}
