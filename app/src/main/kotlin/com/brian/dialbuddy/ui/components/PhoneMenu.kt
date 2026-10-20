package com.brian.dialbuddy.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.ui.theme.InkMuted
import com.brian.dialbuddy.ui.theme.PhoneBand
import com.brian.dialbuddy.ui.theme.PhoneInk
import com.brian.dialbuddy.ui.theme.PhoneStroke

@Composable
fun PhoneMenu(
    version: String,
    onAreaCodes: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.phone_menu)
    val itemColors = MenuDefaults.itemColors(textColor = PhoneBand)
    Column(modifier) {
        Canvas(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PhoneInk)
                .border(1.dp, PhoneStroke, CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = label }
                .padding(8.dp),
        ) {
            val stroke = 1.8.dp.toPx()
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawLine(
                    color = PhoneBand,
                    start = Offset(size.width * 0.12f, size.height * y),
                    end = Offset(size.width * 0.88f, size.height * y),
                    strokeWidth = stroke,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = PhoneInk,
        ) {
            if (version.isNotBlank()) {
                Text(
                    text = version,
                    color = InkMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.area_codes)) },
                onClick = {
                    open = false
                    onAreaCodes()
                },
                colors = itemColors,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.check_for_updates)) },
                onClick = {
                    open = false
                    onCheckUpdates()
                },
                colors = itemColors,
            )
        }
    }
}

fun appVersionName(context: Context): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}
