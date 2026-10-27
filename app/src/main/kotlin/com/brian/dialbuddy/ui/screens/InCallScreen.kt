package com.brian.dialbuddy.ui.screens

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brian.dialbuddy.R
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.telecom.ContactLookup
import com.brian.dialbuddy.telecom.ContactMatch
import com.brian.dialbuddy.telecom.InCallState
import com.brian.dialbuddy.ui.components.CallGlyph
import com.brian.dialbuddy.ui.components.CallGlyphIcon
import com.brian.dialbuddy.ui.components.Dtmf
import com.brian.dialbuddy.ui.components.RaggedCard
import com.brian.dialbuddy.ui.components.ToneKeypad
import com.brian.dialbuddy.ui.theme.Danger
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
fun InCallScreen(state: InCallState) {
    val pager = rememberPagerState(initialPage = 1) { 2 }
    Box(
        Modifier
            .fillMaxSize()
            .background(Forest)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        RaggedCard(
            title = stringResource(
                if (pager.currentPage == 0) R.string.tone_keypad else R.string.state_call,
            ),
            band = PhoneBand,
            ink = PhoneInk,
            stroke = PhoneStroke,
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = callPageDots(pager.currentPage),
                    color = PhoneBand,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    if (page == 0) {
                        CallKeypad(state, Modifier.fillMaxSize())
                    } else {
                        InCallPane(state, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun CallKeypad(state: InCallState, modifier: Modifier = Modifier) {
    val callKey = state.calls.firstOrNull()?.key
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

private fun callPageDots(page: Int): String {
    return (0 until 2).joinToString(" ") { if (it == page) "●" else "○" }
}

private const val TONE_MS = 160L

@Composable
fun InCallPane(state: InCallState, modifier: Modifier = Modifier) {
    val primary = state.calls.firstOrNull()
    val number = primary?.number.orEmpty()
    val context = LocalContext.current
    var match by remember(number) { mutableStateOf<ContactMatch?>(null) }
    LaunchedEffect(number) {
        match = if (number.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { ContactLookup.find(context, number) }
        }
    }
    val title = match?.name?.takeIf { it.isNotBlank() }
        ?: number.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.unknown_caller)
    val ringing = primary?.state == Call.STATE_RINGING
    Column(
        modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Ink, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (match?.name != null && number.isNotBlank()) {
            Text(number, color = InkMuted, fontSize = 16.sp)
        }
        Text(
            callStateLabel(primary?.state),
            color = PhoneBand,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        val extra = (state.calls.size - 1).coerceAtLeast(0)
        if (extra > 0) {
            Text(
                pluralStringResource(R.plurals.more_calls, extra, extra),
                color = InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CallerPhoto(match?.photoUri)
        }
        if (primary != null && ringing) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                WireAction(stringResource(R.string.decline), Danger, CallGlyph.Decline) { Calls.decline(primary.key) }
                WireAction(stringResource(R.string.answer), PhoneBand, CallGlyph.Answer) { Calls.answer(primary.key) }
            }
        }
        if (primary != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteButton(
                    stringResource(R.string.bluetooth),
                    selected = state.bluetoothOn,
                    glyph = CallGlyph.Bluetooth,
                    modifier = Modifier.weight(1f),
                ) { Calls.switchToBluetooth() }
                RouteButton(
                    stringResource(if (state.speakerOn) R.string.speaker_on else R.string.speaker),
                    selected = state.speakerOn,
                    glyph = if (state.speakerOn) CallGlyph.SpeakerOn else CallGlyph.Speaker,
                    modifier = Modifier.weight(1f),
                ) { Calls.activateSpeaker() }
            }
        }
        if (primary != null && !ringing) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteButton(
                    stringResource(if (state.muted) R.string.unmute else R.string.mute),
                    selected = state.muted,
                    glyph = if (state.muted) CallGlyph.MicOff else CallGlyph.Mic,
                    modifier = Modifier.weight(1f),
                ) { Calls.toggleMute() }
                RouteButton(
                    stringResource(if (primary.state == Call.STATE_HOLDING) R.string.resume else R.string.hold),
                    selected = primary.state == Call.STATE_HOLDING,
                    glyph = if (primary.state == Call.STATE_HOLDING) CallGlyph.Resume else CallGlyph.Hold,
                    modifier = Modifier.weight(1f),
                ) { Calls.toggleHold(primary.key) }
            }
            WireAction(
                stringResource(R.string.hangup),
                Danger,
                CallGlyph.Decline,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
            ) { Calls.hangup(primary.key) }
        }
    }
}

@Composable
private fun CallerPhoto(photoUri: android.net.Uri?) {
    Box(
        Modifier
            .size(220.dp)
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
            Text(
                stringResource(R.string.unknown_mark),
                color = Digit,
                fontSize = 96.sp,
            )
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
    Call.STATE_RINGING -> stringResource(R.string.state_ringing)
    Call.STATE_DIALING, Call.STATE_CONNECTING -> stringResource(R.string.state_calling)
    Call.STATE_ACTIVE -> stringResource(R.string.state_active)
    Call.STATE_HOLDING -> stringResource(R.string.state_holding)
    Call.STATE_DISCONNECTING -> stringResource(R.string.state_ending)
    Call.STATE_DISCONNECTED -> stringResource(R.string.state_ended)
    else -> stringResource(R.string.state_call)
}
