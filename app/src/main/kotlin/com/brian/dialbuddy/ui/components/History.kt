package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.CallKind
import com.brian.dialbuddy.domain.callDurationClock
import com.brian.dialbuddy.ui.theme.Danger
import coil.compose.AsyncImage
import com.brian.dialbuddy.ui.theme.HistoryBand
import com.brian.dialbuddy.ui.theme.HistoryBody
import com.brian.dialbuddy.ui.theme.HistorySent
import com.brian.dialbuddy.ui.theme.HistoryStroke
import com.brian.dialbuddy.ui.theme.HistoryInk
import com.brian.dialbuddy.ui.theme.HistoryMuted
import com.brian.dialbuddy.ui.theme.HistoryText
import com.brian.dialbuddy.ui.theme.HistoryWell
import com.brian.dialbuddy.ui.theme.LilacLight
import com.brian.dialbuddy.viewmodel.HistoryAccess
import com.brian.dialbuddy.viewmodel.HistoryRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CallsMenu(
    showRecognized: Boolean,
    showUnrecognized: Boolean,
    onRecognized: () -> Unit,
    onUnrecognized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showRecognized && !showUnrecognized) return
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.calls_menu)
    val itemColors = MenuDefaults.itemColors(textColor = HistoryText)
    Column(modifier) {
        Canvas(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(HistoryBody)
                .border(1.dp, HistoryStroke, CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = label }
                .padding(8.dp),
        ) {
            val stroke = 1.8.dp.toPx()
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawLine(
                    color = HistoryBand,
                    start = Offset(size.width * 0.12f, size.height * y),
                    end = Offset(size.width * 0.88f, size.height * y),
                    strokeWidth = stroke,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = HistoryBody,
        ) {
            if (showRecognized) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_recognized)) },
                    onClick = {
                        open = false
                        onRecognized()
                    },
                    colors = itemColors,
                )
            }
            if (showUnrecognized) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_unrecognized)) },
                    onClick = {
                        open = false
                        onUnrecognized()
                    },
                    colors = itemColors,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryPage(
    rows: List<HistoryRow>,
    recognized: Boolean,
    access: HistoryAccess,
    onCall: (String) -> Unit,
    onViewContact: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onBecomeDialer: () -> Unit,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var numberToAdd by remember { mutableStateOf<String?>(null) }
    numberToAdd?.let { pending ->
        AlertDialog(
            onDismissRequest = { numberToAdd = null },
            title = { Text(stringResource(R.string.add_contact_title)) },
            text = { Text(stringResource(R.string.add_contact_message, pending)) },
            confirmButton = {
                TextButton(onClick = {
                    numberToAdd = null
                    onAddContact(pending)
                }) { Text(stringResource(R.string.add_contact)) }
            },
            dismissButton = {
                TextButton(onClick = { numberToAdd = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    Column(modifier) {
        if (access != HistoryAccess.Ready && access != HistoryAccess.Loading) {
            val message = when (access) {
                HistoryAccess.NeedsRole -> stringResource(R.string.history_need_role)
                HistoryAccess.Denied -> stringResource(R.string.history_denied)
                else -> ""
            }
            if (message.isNotEmpty()) {
                Text(message, color = HistoryText, modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 8.dp))
            }
            if (access == HistoryAccess.NeedsRole) {
                Button(
                    onClick = onBecomeDialer,
                    colors = historyButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset).padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.become_dialer))
                }
            }
            if (access == HistoryAccess.NeedsPermission || access == HistoryAccess.Denied) {
                Button(
                    onClick = onAllow,
                    colors = historyButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset).padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.allow_call_log))
                }
            }
        } else if (access == HistoryAccess.Ready && rows.isEmpty()) {
            Text(
                stringResource(
                    if (recognized) R.string.history_empty_recognized else R.string.history_empty_unrecognized,
                ),
                color = HistoryMuted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 8.dp),
            )
        }
        val days = rows.groupBy { it.dayLabel }
        val listState = rememberChronologicalListState(
            itemCount = if (rows.isEmpty()) 0 else rows.size + days.size,
            newestKey = rows.lastOrNull()?.record?.id,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            days.forEach { (day, calls) ->
                stickyHeader(key = "day-$day") {
                    DaySection(day, HistoryInk)
                }
                items(calls, key = { "call-${it.record.id}" }) { row ->
                    HistoryRowView(row, onCall, onViewContact) { numberToAdd = it }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowView(
    row: HistoryRow,
    onCall: (String) -> Unit,
    onViewContact: (String) -> Unit,
    onOfferAdd: (String) -> Unit,
) {
    val placed = row.record.kind == CallKind.Outgoing
    val number = row.record.number
    val title = row.contactName
        ?: number.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.private_number)
    val missed = row.record.kind == CallKind.Missed ||
        row.record.kind == CallKind.Rejected ||
        row.record.kind == CallKind.Blocked
    val titleColor = if (placed) HistoryInk else HistoryText
    val muted = if (placed) HistoryInk else HistoryMuted
    val kindColor = when {
        missed -> Danger
        placed -> HistoryInk
        else -> HistoryBand
    }
    val duration = callDurationClock(row.record.durationSeconds)
    val detail = if (duration == null) kindLabel(row.record.kind) else "${kindLabel(row.record.kind)} · $duration"
    val bubble = if (placed) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    }
    val openPerson: (() -> Unit)? = when {
        row.contactName != null && number.isNotBlank() -> ({ onViewContact(number) })
        number.isNotBlank() -> ({ onOfferAdd(number) })
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 5.dp),
        horizontalArrangement = if (placed) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!placed) CallerMark(row, title, openPerson)
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 280.dp)
                .clip(bubble)
                .background(if (placed) HistorySent else HistoryWell)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (openPerson == null) Modifier else Modifier.clickable(onClick = openPerson),
            )
            Text(detail, color = kindColor, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            if (row.contactName != null && number.isNotBlank()) {
                Text(
                    number,
                    color = muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(timeLabel(row.record.dateMillis), color = muted, fontSize = 12.sp)
                val callLabel = stringResource(R.string.call)
                val callColor = if (number.isBlank()) muted else titleColor
                Text(
                    callLabel,
                    color = callColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(CircleShape)
                        .border(1.dp, callColor, CircleShape)
                        .clickable(enabled = number.isNotBlank()) { onCall(number) }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        if (placed) CallerMark(row, title, openPerson)
    }
}

@Composable
private fun CallerMark(row: HistoryRow, title: String, onClick: (() -> Unit)?) {
    val mark = row.contactName?.firstOrNull()?.uppercase() ?: stringResource(R.string.unknown_mark)
    var photoFailed by remember(row.photoUri) { mutableStateOf(false) }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(HistoryWell)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        val photo = row.photoUri
        if (photo != null && !photoFailed) {
            AsyncImage(
                model = photo,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true },
            )
        } else {
            Text(mark, color = LilacLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun kindLabel(kind: CallKind): String = when (kind) {
    CallKind.Incoming -> stringResource(R.string.call_incoming)
    CallKind.Outgoing -> stringResource(R.string.call_outgoing)
    CallKind.Missed -> stringResource(R.string.call_missed)
    CallKind.Rejected -> stringResource(R.string.call_rejected)
    CallKind.Blocked -> stringResource(R.string.call_blocked)
    CallKind.Voicemail -> stringResource(R.string.call_voicemail)
    CallKind.Other -> stringResource(R.string.call_other)
}

@Composable
private fun historyButtonColors() = ButtonDefaults.buttonColors(
    containerColor = HistoryBand,
    contentColor = HistoryInk,
)

private fun timeLabel(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("h:mm a").format(time)
}
