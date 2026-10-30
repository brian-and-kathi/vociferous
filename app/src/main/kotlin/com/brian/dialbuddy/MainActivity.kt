package com.brian.dialbuddy

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.telecom.ContactLookup
import com.brian.dialbuddy.telecom.OutgoingAllowance
import com.brian.dialbuddy.ui.screens.DialScreen
import com.brian.dialbuddy.ui.theme.DialBuddyTheme
import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.viewmodel.DialCommand
import com.brian.dialbuddy.viewmodel.DialViewModel

class MainActivity : ComponentActivity() {
    private var onNewIntentHandler: ((Intent) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        onPermissionResult?.invoke(granted)
    }

    private var onPermissionResult: ((Map<String, Boolean>) -> Unit)? = null

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onRoleResult?.invoke()
        if (askNotificationsAfterRole) {
            askNotificationsAfterRole = false
            requestNotificationPermission()
        }
    }

    private var onRoleResult: (() -> Unit)? = null

    private val contactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        val number = ContactLookup.numberFromPick(this, uri) ?: return@registerForActivityResult
        onPickedNumber?.invoke(number)
    }

    private var onPickedNumber: ((String) -> Unit)? = null
    private var skipRolePrompt = false
    private var askNotificationsAfterRole = false

    override fun onStart() {
        super.onStart()
        Calls.mainVisible = true
        if (skipRolePrompt) {
            skipRolePrompt = false
            return
        }
        if (promptForDialerRole()) {
            askNotificationsAfterRole = true
        } else {
            requestNotificationPermission()
        }
    }

    override fun onStop() {
        Calls.mainVisible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: DialViewModel = viewModel()
            DialBuddyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Forest) {
                    DialHost(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onNewIntentHandler?.invoke(intent)
    }

    @Composable
    private fun DialHost(viewModel: DialViewModel) {
        val state by viewModel.state.collectAsState()
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(viewModel, lifecycleOwner) {
            onNewIntentHandler = viewModel::consumeTelIntent
            onPermissionResult = viewModel::onPermissionsReturned
            onRoleResult = viewModel::refreshCapabilities
            onPickedNumber = viewModel::setDialedNumber
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                onNewIntentHandler = null
                onPermissionResult = null
                onRoleResult = null
                onPickedNumber = null
            }
        }
        LaunchedEffect(viewModel) {
            viewModel.consumeTelIntent(intent)
            viewModel.commands.collect { command ->
                when (command) {
                    is DialCommand.PlaceCall -> placeCall(command.number)
                    DialCommand.RequestDialerRole -> requestDialerRole(viewModel)
                    is DialCommand.RequestPermissions -> permissionLauncher.launch(command.permissions)
                    DialCommand.BrowseContacts -> openContacts()
                    is DialCommand.ViewContact -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(command.uri)))
                    is DialCommand.InsertContact -> insertContact(command.number)
                    DialCommand.OpenNotificationHistory -> openNotificationHistory()
                    DialCommand.OpenNotificationAccess -> openNotificationAccess()
                }
            }
        }
        DialScreen(
            viewModel = viewModel,
            state = state,
        )
    }

    private fun promptForDialerRole(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return false
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return false
        skipRolePrompt = true
        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        return true
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun requestDialerRole(viewModel: DialViewModel) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            viewModel.refreshCapabilities()
            return
        }
        skipRolePrompt = true
        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    private fun insertContact(number: String) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.add_contact_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotificationHistory() {
        try {
            startActivity(Intent("android.settings.NOTIFICATION_HISTORY"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.notices_history_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.notices_access_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openContacts() {
        contactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
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
