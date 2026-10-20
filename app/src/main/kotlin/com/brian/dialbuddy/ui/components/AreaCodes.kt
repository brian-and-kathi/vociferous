package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.normalizeAreaCode
import com.brian.dialbuddy.ui.theme.Danger
import com.brian.dialbuddy.ui.theme.DigitTint
import com.brian.dialbuddy.ui.theme.DigitWindow
import com.brian.dialbuddy.ui.theme.InkMuted
import com.brian.dialbuddy.ui.theme.PhoneBand
import com.brian.dialbuddy.ui.theme.PhoneInk
import com.brian.dialbuddy.ui.theme.PhoneStroke
import com.brian.dialbuddy.ui.theme.SurfaceGreen

@Composable
fun AreaCodesPage(
    codes: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val code = normalizeAreaCode(draft)
    val canAdd = code != null && code !in codes
    fun add() {
        val ready = code ?: return
        if (ready in codes) return
        onAdd(ready)
        draft = ""
    }
    Column(modifier) {
        Text(
            text = stringResource(R.string.area_codes_body),
            color = InkMuted,
            fontSize = 14.sp,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { next ->
                    draft = next.filter { it in '0'..'9' }.take(3)
                },
                textStyle = TextStyle(color = DigitTint, fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
                singleLine = true,
                cursorBrush = SolidColor(PhoneBand),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.area_code_hint),
                                color = InkMuted,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DigitWindow)
                    .border(1.dp, PhoneStroke, RoundedCornerShape(12.dp)),
            )
            Button(
                onClick = { add() },
                enabled = canAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneBand,
                    contentColor = PhoneInk,
                    disabledContainerColor = PhoneStroke,
                    disabledContentColor = PhoneInk,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 8.dp).height(48.dp),
            ) {
                Text(stringResource(R.string.area_code_add))
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            if (codes.isEmpty()) {
                Text(
                    text = stringResource(R.string.area_codes_empty),
                    color = InkMuted,
                    fontSize = 14.sp,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(codes, key = { it }) { saved ->
                        val removeLabel = stringResource(R.string.area_code_remove)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceGreen)
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = saved,
                                color = PhoneBand,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onRemove(saved) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                                modifier = Modifier.semantics { contentDescription = "$removeLabel $saved" },
                            ) {
                                Text(removeLabel)
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.close), color = PhoneBand)
        }
    }
}
