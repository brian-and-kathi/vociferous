package com.brian.dialbuddy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.ContactPanel
import com.brian.dialbuddy.domain.callPageAfterReorder
import com.brian.dialbuddy.domain.contactPanel
import com.brian.dialbuddy.domain.contactSwipeEnabled
import com.brian.dialbuddy.domain.orderedCallCards
import com.brian.dialbuddy.telecom.CallLine
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.telecom.ContactLookup
import com.brian.dialbuddy.telecom.ContactMatch
import com.brian.dialbuddy.telecom.ContactProfile
import com.brian.dialbuddy.telecom.InCallState
import com.brian.dialbuddy.ui.components.CallGlyph
import com.brian.dialbuddy.ui.components.CallGlyphIcon
import com.brian.dialbuddy.ui.components.CardPager
import com.brian.dialbuddy.ui.components.appVersionName
import com.brian.dialbuddy.ui.components.Dtmf
import com.brian.dialbuddy.ui.components.KeypadPane
import com.brian.dialbuddy.ui.components.RaggedCard
import com.brian.dialbuddy.ui.components.ToneKeypad
import com.brian.dialbuddy.ui.theme.Accent
import com.brian.dialbuddy.ui.theme.Danger
import com.brian.dialbuddy.ui.theme.OnAccent
import com.brian.dialbuddy.ui.theme.OnDanger
import com.brian.dialbuddy.ui.theme.Digit
import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.ui.theme.PhoneBand
import com.brian.dialbuddy.ui.theme.PhoneInk
import com.brian.dialbuddy.ui.theme.PhoneStroke
import com.brian.dialbuddy.ui.theme.Ink
import com.brian.dialbuddy.ui.theme.InkMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InCallScreen(
    state: InCallState,
    digits: String,
    onDigit: (String) -> Unit,
    onPlus: () -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onBrowse: () -> Unit,
) {
    val calls = orderedCallCards(state.calls) { it.isIncoming() }
    val pager = rememberPagerState(initialPage = calls.size) { (calls.size + 1).coerceAtLeast(1) }
    var seen by remember { mutableStateOf(emptySet<String>()) }
    var settledCallKey by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var match by remember(digits) { mutableStateOf<ContactMatch?>(null) }
    LaunchedEffect(digits) {
        val longEnough = digits.count { it.isDigit() } > 4
        val allowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        match = if (!longEnough || !allowed) {
            null
        } else {
            withContext(Dispatchers.IO) { ContactLookup.find(context, digits) }
        }
    }
    LaunchedEffect(pager.settledPage) {
        settledCallKey = calls.getOrNull(pager.settledPage - 1)?.key
    }
    LaunchedEffect(calls) {
        val keys = calls.map { it.key }
        val added = keys.filter { it !in seen }
        seen = keys.toSet()
        if (added.isNotEmpty()) {
            val target = keys.indexOf(added.last()) + 1
            if (pager.currentPage != target) pager.animateScrollToPage(target)
        } else {
            val target = callPageAfterReorder(keys, settledCallKey, pager.currentPage)
            if (pager.currentPage != target) pager.scrollToPage(target)
        }
    }
    WatchIncomingSilence(pager.settledPage, calls) { page -> page - 1 }
    Box(
        Modifier
            .fillMaxSize()
            .background(Forest)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (calls.isEmpty()) return@Box
        val contactName = match?.name?.takeIf { it.isNotBlank() }
        Column(Modifier.fillMaxSize()) {
            Text(
                text = pageDots(pager.currentPage, calls.size + 1),
                color = PhoneBand,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            CardPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val line = calls.getOrNull(page - 1)
                RaggedCard(
                    seed = page,
                    title = when {
                        line == null -> null
                        line.isIncoming() -> stringResource(R.string.incoming_call)
                        else -> stringResource(R.string.active_call)
                    },
                    band = PhoneBand,
                    ink = PhoneInk,
                    stroke = PhoneStroke,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (line == null) {
                        KeypadPane(
                            digits = digits,
                            contactName = contactName,
                            contactPhoto = if (contactName == null) null else match?.photoUri?.toString(),
                            hint = stringResource(R.string.keypad_hint),
                            backspaceLabel = stringResource(R.string.backspace),
                            callLabel = stringResource(R.string.call),
                            browseLabel = stringResource(R.string.browse),
                            onDigit = onDigit,
                            onPlus = onPlus,
                            onBackspace = onBackspace,
                            onCall = onCall,
                            onBrowse = onBrowse,
                            stroke = PhoneStroke,
                            accent = PhoneBand,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CallCard(line, state, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun WatchIncomingSilence(
    settledPage: Int,
    calls: List<CallLine>,
    callIndex: (Int) -> Int,
) {
    var settledRinging by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settledPage, calls) {
        val line = calls.getOrNull(callIndex(settledPage))
        val ringingKey = if (line != null && line.isIncoming()) line.key else null
        val previous = settledRinging
        if (previous != null && previous != ringingKey) {
            Calls.silenceRing(previous, stopAudible = ringingKey == null)
        }
        settledRinging = ringingKey
    }
}

@Composable
fun CallCard(line: CallLine, state: InCallState, modifier: Modifier = Modifier) {
    key(line.key) {
        val lookup = rememberCallerLookup(line.number)
        val profile = lookup.profile
        val panel = contactPanel(
            name = profile?.name,
            phones = profile?.phones.orEmpty(),
            emails = profile?.emails.orEmpty(),
            note = profile?.note,
            noteLabel = stringResource(R.string.contact_notes),
            phoneLabel = stringResource(R.string.contact_phone),
            emailLabel = stringResource(R.string.contact_email),
        )
        CallPages(panel, profile?.photoUri, modifier) {
            if (line.isIncoming()) {
                IncomingCall(line, profile, lookup.settled, Modifier.fillMaxSize())
            } else {
                ActiveCall(line, state, profile, lookup.settled, Modifier.fillMaxSize())
            }
        }
    }
}

private data class CallerLookup(val profile: ContactProfile?, val settled: Boolean)

@Composable
private fun rememberCallerLookup(number: String): CallerLookup {
    val context = LocalContext.current
    var profile by remember(number) { mutableStateOf<ContactProfile?>(null) }
    var settled by remember(number) { mutableStateOf(number.isBlank()) }
    LaunchedEffect(number) {
        profile = if (number.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { ContactLookup.profile(context, number) }
        }
        settled = true
    }
    return CallerLookup(profile, settled)
}

@Composable
private fun CallPages(
    panel: ContactPanel?,
    photoUri: android.net.Uri?,
    modifier: Modifier,
    call: @Composable () -> Unit,
) {
    if (panel == null || !contactSwipeEnabled(panel)) {
        Box(modifier) { call() }
        return
    }
    val pager = rememberPagerState { 2 }
    HorizontalPager(
        state = pager,
        modifier = modifier,
        beyondViewportPageCount = 1,
        userScrollEnabled = true,
    ) { page ->
        if (page == 0) {
            Box(Modifier.fillMaxSize()) { call() }
        } else {
            ContactInfo(panel, photoUri, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ContactInfo(
    panel: ContactPanel,
    photoUri: android.net.Uri?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }
    val initial = panel.name.firstOrNull()?.uppercase() ?: stringResource(R.string.unknown_mark)
    Column(modifier.padding(horizontal = 8.dp)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallerPhoto(photoUri, initial, size = 64.dp, letter = 28.sp)
                if (panel.name.isNotBlank()) {
                    Text(
                        panel.name,
                        color = Ink,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                }
            }
            panel.fields.forEach { field ->
                if (field.label.isNotBlank()) {
                    Text(
                        field.label,
                        color = InkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Text(field.value, color = Ink, fontSize = 16.sp)
            }
        }
        if (version.isNotBlank()) {
            Text(
                version,
                color = InkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActiveCall(
    line: CallLine,
    state: InCallState,
    profile: ContactProfile?,
    settled: Boolean,
    modifier: Modifier,
) {
    var tonesOpen by remember { mutableStateOf(false) }
    if (!tonesOpen) {
        ActiveControls(line, state, profile, settled, modifier) { tonesOpen = true }
        return
    }
    Column(modifier) {
        CallKeypad(line.key, Modifier.weight(1f).fillMaxWidth())
        TextAction(
            stringResource(R.string.close),
            Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { tonesOpen = false }
    }
}

@Composable
fun CallKeypad(callKey: String?, modifier: Modifier = Modifier) {
    var tones by remember(callKey) { mutableStateOf("") }
    val toneGeneration = remember(callKey) { intArrayOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(callKey) {
        onDispose {
            toneGeneration[0] += 1
            if (callKey != null) Calls.stopTone(callKey)
        }
    }
    ToneKeypad(
        digits = tones,
        hint = stringResource(R.string.tone_hint),
        backspaceLabel = stringResource(R.string.backspace),
        onDown = { digit ->
            toneGeneration[0] += 1
            tones += digit
            val sent = callKey != null && Calls.startTone(callKey, digit)
            if (!sent) Dtmf.play(digit)
        },
        onUp = {
            val generation = toneGeneration[0]
            val key = callKey
            scope.launch {
                delay(TONE_MS)
                if (generation == toneGeneration[0] && key != null) Calls.stopTone(key)
            }
        },
        onBackspace = { tones = tones.dropLast(1) },
        stroke = PhoneStroke,
        modifier = modifier,
    )
}

@Composable
private fun IncomingCall(
    line: CallLine,
    profile: ContactProfile?,
    settled: Boolean,
    modifier: Modifier,
) {
    val number = line.number
    val name = profile?.name?.takeIf { it.isNotBlank() }
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (profile != null) {
            val initial = name?.firstOrNull()?.uppercase() ?: stringResource(R.string.unknown_mark)
            CallerPhoto(profile.photoUri, initial)
            Text(
                name ?: number.ifBlank { stringResource(R.string.unknown) },
                color = Ink,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (!name.isNullOrBlank() && number.isNotBlank()) {
                Text(number, color = InkMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        } else {
            Text(
                stringResource(R.string.unknown),
                color = Ink,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (number.isNotBlank()) {
                Text(number, color = InkMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            if (settled && number.isNotBlank()) {
                Text(
                    stringResource(R.string.contact_none),
                    color = InkMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingButton(Accent, OnAccent, stringResource(R.string.call_yes)) {
                Calls.answer(line.key)
            }
            RingButton(Danger, OnDanger, stringResource(R.string.call_no)) {
                Calls.decline(line.key)
            }
        }
    }
}

@Composable
private fun RingButton(
    fill: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .requiredSize(112.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ActiveControls(
    line: CallLine,
    state: InCallState,
    profile: ContactProfile?,
    settled: Boolean,
    modifier: Modifier,
    onKeypad: () -> Unit,
) {
    CallerBody(line, profile, settled, modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RouteButton(
                stringResource(R.string.speaker),
                selected = state.speakerOn,
                glyph = if (state.speakerOn) CallGlyph.SpeakerOn else CallGlyph.Speaker,
                modifier = Modifier.weight(1f),
            ) { Calls.activateSpeaker() }
            RouteButton(
                stringResource(R.string.bluetooth),
                selected = state.bluetoothOn,
                glyph = CallGlyph.Bluetooth,
                modifier = Modifier.weight(1f),
            ) { Calls.switchToBluetooth() }
            RouteButton(
                stringResource(if (state.muted) R.string.unmute else R.string.mute),
                selected = state.muted,
                glyph = if (state.muted) CallGlyph.MicOff else CallGlyph.Mic,
                modifier = Modifier.weight(1f),
            ) { Calls.toggleMute() }
        }
        TextAction(
            stringResource(R.string.tone_keypad),
            Modifier.fillMaxWidth().padding(top = 12.dp),
            onKeypad,
        )
        WireAction(
            stringResource(R.string.hangup),
            Danger,
            CallGlyph.Decline,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
        ) { Calls.hangup(line.key) }
    }
}

@Composable
private fun CallerBody(
    line: CallLine,
    profile: ContactProfile?,
    settled: Boolean,
    modifier: Modifier,
    actions: @Composable () -> Unit,
) {
    val number = line.number
    val recognized = profile != null
    val name = profile?.name?.takeIf { it.isNotBlank() }
    val title = name
        ?: number.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.unknown_caller)
    Column(
        modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Ink, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (name != null && number.isNotBlank()) {
            Text(number, color = InkMuted, fontSize = 16.sp)
        }
        Text(
            callStateLabel(line.state),
            color = PhoneBand,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (settled && profile == null && number.isNotBlank()) {
            Text(
                stringResource(R.string.contact_none),
                color = InkMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (recognized) {
                val initial = name?.firstOrNull()?.uppercase() ?: stringResource(R.string.unknown_mark)
                CallerPhoto(profile?.photoUri, initial)
            }
        }
        actions()
    }
}

@Composable
private fun CallerPhoto(
    photoUri: android.net.Uri?,
    initial: String,
    size: androidx.compose.ui.unit.Dp = 220.dp,
    letter: androidx.compose.ui.unit.TextUnit = 96.sp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, PhoneStroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(initial, color = Digit, fontSize = letter)
        }
    }
}

@Composable
private fun RouteButton(
    label: String,
    selected: Boolean,
    glyph: CallGlyph,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) PhoneBand else PhoneStroke
    val ink = if (selected) PhoneBand else Ink
    Box(
        modifier
            .height(58.dp)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallGlyphIcon(glyph, ink, Modifier.size(18.dp))
            Text(
                label,
                color = ink,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TextAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .border(1.dp, PhoneStroke, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ink, fontSize = 16.sp)
    }
}

@Composable
private fun WireAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    glyph: CallGlyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(48.dp)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallGlyphIcon(glyph, color, Modifier.size(18.dp))
            Text(label, color = color, fontSize = 16.sp)
        }
    }
}

@Composable
private fun callStateLabel(state: Int?): String = when (state) {
    Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> stringResource(R.string.state_ringing)
    Call.STATE_DIALING, Call.STATE_CONNECTING -> stringResource(R.string.state_calling)
    Call.STATE_ACTIVE -> stringResource(R.string.state_active)
    Call.STATE_HOLDING -> stringResource(R.string.state_holding)
    Call.STATE_DISCONNECTING -> stringResource(R.string.state_ending)
    Call.STATE_DISCONNECTED -> stringResource(R.string.state_ended)
    else -> stringResource(R.string.state_call)
}

private fun pageDots(page: Int, count: Int): String {
    return (0 until count).joinToString(" ") { if (it == page) "●" else "○" }
}

private const val TONE_MS = 160L
