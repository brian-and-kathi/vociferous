package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.brian.dialbuddy.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.domain.qualifiesForBulkDelete
import com.brian.dialbuddy.ui.theme.Accent
import com.brian.dialbuddy.ui.theme.Danger
import com.brian.dialbuddy.ui.theme.OnDanger
import com.brian.dialbuddy.ui.theme.DigitStroke
import com.brian.dialbuddy.ui.theme.IconWell
import com.brian.dialbuddy.ui.theme.Ink
import com.brian.dialbuddy.ui.theme.InkMuted

import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.ui.theme.SurfaceGreen
import com.brian.dialbuddy.ui.theme.TitleInk
import com.brian.dialbuddy.viewmodel.MailboxRow
import com.brian.dialbuddy.viewmodel.MailboxTab
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MailMenu(
    cards: List<MailboxTab>,
    onPick: (MailboxTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.voicemail_menu)
    val itemColors = MenuDefaults.itemColors(textColor = Ink)
    Column(modifier) {
        Canvas(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceGreen)
                .border(1.dp, DigitStroke, CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = label }
                .padding(8.dp),
        ) {
            val stroke = 1.8.dp.toPx()
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawLine(
                    color = Accent,
                    start = Offset(size.width * 0.12f, size.height * y),
                    end = Offset(size.width * 0.88f, size.height * y),
                    strokeWidth = stroke,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = SurfaceGreen,
        ) {
            cards.forEach { card ->
                val title = when (card) {
                    MailboxTab.Known -> stringResource(R.string.tab_known)
                    MailboxTab.Unknown -> stringResource(R.string.tab_unknown)
                    MailboxTab.Stats -> stringResource(R.string.tab_settings)
                }
                DropdownMenuItem(
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onPick(card)
                    },
                    colors = itemColors,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailboxPane(
    tab: MailboxTab,
    knownCount: Int,
    unknownCount: Int,
    rows: List<MailboxRow>,
    bulkCount: Int,
    thresholdSeconds: Int,
    statusText: String,
    showSamsungNote: Boolean,
    samsungNote: String,
    thresholdLabel: String,
    deleteLabel: String,
    deleteEnabled: Boolean,
    busy: Boolean,
    onTab: (MailboxTab) -> Unit,
    onThreshold: (Int) -> Unit,
    onDelete: () -> Unit,
    playingId: Long?,
    playbackFraction: Float,
    onPlay: (Long) -> Unit,
    onDeleteOne: (Long) -> Unit,
    onViewContact: (MailboxRow) -> Unit,
    stats: @Composable () -> Unit,
    showTabs: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<MailboxRow?>(null) }
    Column(modifier) {
        if (showTabs) Row(Modifier.fillMaxWidth().padding(horizontal = CardInset).padding(top = 12.dp)) {
            MailboxTabButton(stringResource(R.string.tab_known), knownCount, tab == MailboxTab.Known, Modifier.weight(1f)) {
                onTab(MailboxTab.Known)
            }
            MailboxTabButton(stringResource(R.string.tab_unknown), unknownCount, tab == MailboxTab.Unknown, Modifier.weight(1f)) {
                onTab(MailboxTab.Unknown)
            }
            MailboxTabButton(stringResource(R.string.tab_settings), null, tab == MailboxTab.Stats, Modifier.weight(1f)) {
                onTab(MailboxTab.Stats)
            }
        }
        if (tab == MailboxTab.Stats) {
            stats()
        } else {
            if (tab == MailboxTab.Unknown) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CardInset)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceGreen)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(thresholdLabel, color = InkMuted, fontSize = 13.sp)
                    Text(
                        "$thresholdSeconds seconds",
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                    Slider(
                        value = thresholdSeconds.toFloat(),
                        onValueChange = { onThreshold(it.toInt()) },
                        valueRange = 1f..20f,
                        steps = 18,
                        enabled = !busy,
                    )
                }
            }
            if (statusText.isNotBlank()) {
                Text(statusText, color = Ink, modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 8.dp))
            }
            if (showSamsungNote) {
                Text(samsungNote, color = InkMuted, modifier = Modifier.padding(horizontal = CardInset).padding(top = 8.dp))
            }
            val days = rows.groupBy { it.dayLabel }
            val listState = rememberChronologicalListState(
                itemCount = if (rows.isEmpty()) 0 else rows.size + days.size,
                newestKey = rows.lastOrNull()?.entry?.id,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                days.forEach { (day, messages) ->
                    stickyHeader(key = "day-$day") {
                        DaySection(day, TitleInk)
                    }
                    items(messages, key = { "msg-${it.entry.id}" }) { row ->
                        val marked = row.entry.qualifiesForBulkDelete(
                            thresholdSeconds,
                            recognized = row.contactName != null,
                        )
                        VoicemailRowView(
                            row = row,
                            marked = marked,
                            playing = playingId == row.entry.id,
                            progress = if (playingId == row.entry.id) playbackFraction else 0f,
                            onPlay = onPlay,
                            onDeleteOne = onDeleteOne,
                            onOpen = { selected = row },
                        )
                    }
                }
            }
            selected?.let { row ->
                VoicemailDetailDialog(
                    row = row,
                    playing = playingId == row.entry.id,
                    marked = row.entry.qualifiesForBulkDelete(
                        thresholdSeconds,
                        recognized = row.contactName != null,
                    ),
                    onPlay = { onPlay(row.entry.id) },
                    onDelete = {
                        selected = null
                        onDeleteOne(row.entry.id)
                    },
                    onClose = { selected = null },
                    onViewContact = {
                        selected = null
                        onViewContact(row)
                    },
                )
            }
            Button(
                onClick = onDelete,
                enabled = deleteEnabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
            ) {
                Text(deleteLabel)
            }
        }
    }
}

@Composable
private fun MailboxTabButton(
    label: String,
    count: Int?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) Accent else InkMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = color, fontSize = 13.sp)
        if (count != null) {
            Text(count.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun VoicemailRowView(
    row: MailboxRow,
    marked: Boolean,
    playing: Boolean,
    progress: Float,
    onPlay: (Long) -> Unit,
    onDeleteOne: (Long) -> Unit,
    onOpen: () -> Unit,
) {
    val title = row.contactName ?: stringResource(R.string.unknown_caller)
    val duration = row.entry.durationSeconds
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(84.dp),
        ) {
            ProfileMark(
                marked = marked,
                onClick = { onDeleteOne(row.entry.id) },
            )
            Text(
                title,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onOpen),
            )
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playbackLabel = stringResource(if (playing) R.string.stop else R.string.play)
                Box(
                    Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(1.dp, Accent.copy(alpha = if (playing) 0.35f else 1f), CircleShape)
                            .clickable { onPlay(row.entry.id) }
                            .semantics { contentDescription = playbackLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playing) {
                            Box(Modifier.size(12.dp).background(Accent))
                        } else {
                            Text("▶", color = Accent)
                        }
                    }
                    if (playing) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 1.5.dp.toPx()
                            val inset = stroke / 2f
                            drawArc(
                                color = Accent,
                                startAngle = -90f,
                                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            }
            Text(
                timeLabel(row.entry.dateMillis),
                color = InkMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = if (duration == null) {
                    stringResource(R.string.duration_unknown)
                } else {
                    pluralStringResource(R.plurals.duration_seconds, duration, duration)
                },
                color = Accent,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ProfileMark(
    onClick: () -> Unit,
    marked: Boolean = false,
    size: Dp = 64.dp,
) {
    val label = stringResource(R.string.delete)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (marked) Danger else Color.Transparent)
            .border(if (marked) 0.dp else 2.dp, Danger, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = this.size.minDimension * 0.3f
            val stroke = this.size.minDimension * 0.08f
            val color = if (marked) OnDanger else Danger
            drawLine(
                color = color,
                start = Offset(inset, inset),
                end = Offset(this.size.width - inset, this.size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(this.size.width - inset, inset),
                end = Offset(inset, this.size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun VoicemailDetailDialog(
    row: MailboxRow,
    playing: Boolean,
    marked: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onViewContact: () -> Unit,
) {
    val known = row.contactName
    val duration = row.entry.durationSeconds
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileMark(
                    marked = marked,
                    onClick = onDelete,
                    size = 84.dp,
                )
                Text(
                    known ?: stringResource(R.string.unknown_caller),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .then(if (known != null) Modifier.clickable(onClick = onViewContact) else Modifier),
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(detailWhen(row.entry.dateMillis), color = Ink, textAlign = TextAlign.Center)
                Text(
                    text = if (duration == null) {
                        stringResource(R.string.duration_unknown)
                    } else {
                        pluralStringResource(R.plurals.duration_seconds, duration, duration)
                    },
                    color = Accent,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (known != null) {
                    val number = row.entry.number
                    if (!number.isNullOrBlank()) {
                        Text(number, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPlay) {
                Text(stringResource(if (playing) R.string.stop else R.string.play))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
fun SpeakerButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (on) R.string.speaker_on else R.string.speaker_off)
    val color = if (on) Accent else InkMuted
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Forest)
            .border(1.dp, color, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val speaker = Path().apply {
                moveTo(size.width * 0.05f, size.height * 0.38f)
                lineTo(size.width * 0.30f, size.height * 0.38f)
                lineTo(size.width * 0.52f, size.height * 0.16f)
                lineTo(size.width * 0.52f, size.height * 0.84f)
                lineTo(size.width * 0.30f, size.height * 0.62f)
                lineTo(size.width * 0.05f, size.height * 0.62f)
                close()
            }
            drawPath(speaker, color)
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            if (on) {
                drawArc(
                    color = color,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.40f, size.height * 0.30f),
                    size = Size(size.width * 0.26f, size.height * 0.40f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.46f, size.height * 0.16f),
                    size = Size(size.width * 0.42f, size.height * 0.68f),
                    style = stroke,
                )
            } else {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.62f, size.height * 0.28f),
                    end = Offset(size.width * 0.92f, size.height * 0.72f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun detailWhen(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy, h:mm a").format(time)
}

private fun timeLabel(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("h:mm a").format(time)
}

@Composable
fun StatTable(
    title: String,
    recognized: Long,
    unrecognized: Long,
    bulk: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(92.dp)) {
            Text(
                title,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                style = tightText,
            )
            Text(
                (recognized + unrecognized + bulk).toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                style = tightText.copy(fontFeatureSettings = "tnum", lineHeight = 32.sp),
            )
        }
        Column(Modifier.weight(1f)) {
            StatRow(recognized.toString(), stringResource(R.string.stat_recognized), color)
            StatRow(unrecognized.toString(), stringResource(R.string.stat_unrecognized), color)
            StatRow(bulk.toString(), stringResource(R.string.stat_bulk), color)
        }
    }
}

private val tightText = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeight = 16.sp,
)

@Composable
private fun StatRow(counter: String, label: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            counter,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.End,
            style = tightText.copy(fontFeatureSettings = "tnum", lineHeight = 18.sp),
            modifier = Modifier.width(40.dp),
        )
        Text(
            label,
            color = Ink,
            fontSize = 14.sp,
            style = tightText,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun StatsActions(
    isDialer: Boolean,
    showVoicemail: Boolean,
    showContacts: Boolean,
    becomeLabel: String,
    voicemailLabel: String,
    contactsLabel: String,
    onBecome: () -> Unit,
    onVoicemail: () -> Unit,
    onContacts: () -> Unit,
) {
    if (!isDialer) {
        Button(onClick = onBecome, modifier = Modifier.fillMaxWidth()) {
            Text(becomeLabel)
        }
    }
    if (showVoicemail) {
        Button(onClick = onVoicemail, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(voicemailLabel)
        }
    }
    if (showContacts) {
        TextButton(onClick = onContacts, modifier = Modifier.fillMaxWidth()) {
            Text(contactsLabel)
        }
    }
}
