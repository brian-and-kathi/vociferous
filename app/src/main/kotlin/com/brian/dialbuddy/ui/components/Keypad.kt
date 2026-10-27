package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.brian.dialbuddy.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.ui.theme.Accent
import com.brian.dialbuddy.ui.theme.Digit
import com.brian.dialbuddy.ui.theme.DigitLit
import com.brian.dialbuddy.ui.theme.DigitRest
import com.brian.dialbuddy.ui.theme.DigitStroke
import com.brian.dialbuddy.ui.theme.DigitTint
import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.ui.theme.InkMuted
import kotlinx.coroutines.awaitCancellation

private data class KeyFace(
    val digit: String,
    val letters: String,
    val description: String,
    val longPlus: Boolean = false,
)

private val keys = listOf(
    KeyFace("1", "", "1"),
    KeyFace("2", "ABC", "2, A B C"),
    KeyFace("3", "DEF", "3, D E F"),
    KeyFace("4", "GHI", "4, G H I"),
    KeyFace("5", "JKL", "5, J K L"),
    KeyFace("6", "MNO", "6, M N O"),
    KeyFace("7", "PQRS", "7, P Q R S"),
    KeyFace("8", "TUV", "8, T U V"),
    KeyFace("9", "WXYZ", "9, W X Y Z"),
    KeyFace("*", "", "star"),
    KeyFace("0", "+", "0. Hold for plus", longPlus = true),
    KeyFace("#", "", "pound"),
)

private val toneKeys = keys.map { face ->
    if (face.digit == "0") face.copy(letters = "", description = "0", longPlus = false) else face
}

private val keySize = 62.dp
private val keyGap = 14.dp
private val clusterWidth = keySize * 3 + keyGap * 2

@Composable
fun KeypadPane(
    digits: String,
    contactName: String?,
    contactPhoto: String?,
    hint: String,
    backspaceLabel: String,
    callLabel: String,
    browseLabel: String,
    onDigit: (String) -> Unit,
    onPlus: () -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onBrowse: () -> Unit,
    stroke: Color = DigitStroke,
    accent: Color = Accent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (contactName != null) {
                DialContact(contactName, contactPhoto, stroke)
            }
        }
        DigitWindow(digits, hint, backspaceLabel, stroke, onBackspace)
        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(keyGap),
        ) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                    row.forEach { face ->
                        WireKey(
                            face = face,
                            stroke = stroke,
                            onClick = {
                                Dtmf.play(face.digit)
                                onDigit(face.digit)
                            },
                            onLongClick = if (face.longPlus) onPlus else null,
                        )
                    }
                }
            }
        }
        val calling = digits.isNotEmpty()
        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = 8.dp)
                .width(clusterWidth)
                .height(44.dp)
                .border(1.dp, accent, RoundedCornerShape(8.dp))
                .clickable(onClick = { if (calling) onCall() else onBrowse() }),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (calling) callLabel else browseLabel,
                color = accent,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
fun ToneKeypad(
    digits: String,
    hint: String,
    backspaceLabel: String,
    onDown: (String) -> Unit,
    onUp: () -> Unit,
    onBackspace: () -> Unit,
    stroke: Color = DigitStroke,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth())
        DigitWindow(digits, hint, backspaceLabel, stroke, onBackspace)
        Column(
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(keyGap),
        ) {
            toneKeys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                    row.forEach { face ->
                        WireKey(
                            face = face,
                            stroke = stroke,
                            onClick = {},
                            onLongClick = null,
                            onDown = { onDown(face.digit) },
                            onUp = onUp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitWindow(
    digits: String,
    hint: String,
    backspaceLabel: String,
    stroke: Color,
    onBackspace: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(clusterWidth)
            .height(52.dp)
            .border(1.dp, stroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = if (digits.isEmpty()) {
                buildAnnotatedString { append(hint) }
            } else {
                dialedText(digits)
            },
            color = if (digits.isEmpty()) InkMuted else Digit,
            fontWeight = if (digits.isEmpty()) FontWeight.Normal else FontWeight.Medium,
            fontSize = if (digits.isEmpty()) 14.sp else 26.sp,
            letterSpacing = if (digits.isEmpty()) 0.sp else 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(end = 28.dp),
        )
        Text(
            text = backspaceLabel,
            color = stroke,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onBackspace)
                .padding(4.dp),
        )
    }
}

@Composable
private fun DialContact(name: String, photo: String?, stroke: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(1.dp, stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    stringResource(R.string.unknown_mark),
                    color = Digit,
                    fontSize = 40.sp,
                )
            }
        }
        Text(
            text = name,
            color = Digit,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WireKey(
    face: KeyFace,
    stroke: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
) {
    val symbol = face.digit == "*" || face.digit == "#"
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = rememberUpdatedState(onDown)
    val up = rememberUpdatedState(onUp)
    if (onDown != null) {
        LaunchedEffect(pressed) {
            if (!pressed) return@LaunchedEffect
            down.value?.invoke()
            try {
                awaitCancellation()
            } finally {
                up.value?.invoke()
            }
        }
    }
    val press = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    }
    Box(
        modifier = Modifier
            .size(keySize)
            .clip(CircleShape)
            .background(
                when {
                    pressed -> DigitLit
                    symbol -> Color.Transparent
                    else -> DigitRest
                },
            )
            .border(1.dp, stroke, CircleShape)
            .semantics { contentDescription = face.description }
            .then(press),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = face.digit,
                color = when {
                    pressed -> Forest
                    symbol -> Digit
                    else -> DigitTint
                },
                fontSize = 22.sp,
            )
            Text(
                text = face.letters,
                color = if (pressed) Forest else InkMuted,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

private fun dialedText(digits: String) = buildAnnotatedString {
    digits.forEach { mark ->
        withStyle(SpanStyle(color = if (mark.isDigit()) DigitTint else Digit)) {
            append(mark)
        }
    }
}
