package com.ejzimmer.tokei.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun pad2(n: Int): String = n.toString().padStart(2, '0')

fun formatHms(hours: Int, minutes: Int, seconds: Int): String =
    "${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}"

/**
 * Three independent 2-digit boxes that shift left as you type -- 00 -> 01 ->
 * 11 -- the same "microwave keypad" entry the web version used. Each
 * onDigit/onBackspace call reports the raw keystroke; the ViewModel owns the
 * actual shift-and-carry math so the same rules apply everywhere.
 */
@Composable
fun EditableDurationFields(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onDigit: (DurationField, Int) -> Unit,
    onBackspace: (DurationField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        DigitShiftField(pad2(hours), DurationField.HOURS, onDigit, onBackspace)
        ClockColon()
        DigitShiftField(pad2(minutes), DurationField.MINUTES, onDigit, onBackspace)
        ClockColon()
        DigitShiftField(pad2(seconds), DurationField.SECONDS, onDigit, onBackspace)
    }
}

/** Same keypad entry as [EditableDurationFields], minus the hours box --
 * for a pomodoro's work/rest durations, which never need to run in hours
 * and need to stay narrow enough to sit two-up. */
@Composable
fun EditableDurationFieldsMmSs(
    minutes: Int,
    seconds: Int,
    onDigit: (DurationField, Int) -> Unit,
    onBackspace: (DurationField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        DigitShiftField(pad2(minutes), DurationField.MINUTES, onDigit, onBackspace, boxWidth = 52.dp, fontSize = 24.sp)
        ClockColon(fontSize = 24.sp)
        DigitShiftField(pad2(seconds), DurationField.SECONDS, onDigit, onBackspace, boxWidth = 52.dp, fontSize = 24.sp)
    }
}

@Composable
private fun ClockColon(fontSize: TextUnit = 32.sp) {
    Text(
        ":",
        color = FaceDim,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DigitShiftField(
    value: String,
    field: DurationField,
    onDigit: (DurationField, Int) -> Unit,
    onBackspace: (DurationField) -> Unit,
    boxWidth: Dp = 72.dp,
    fontSize: TextUnit = 32.sp,
) {
    // A plain Material TextField reserves a lot of internal padding for a
    // normal form field, which left almost no room for two bold 32sp digits
    // at a sane width. BasicTextField has none of that chrome, so the box
    // can stay compact and the digits still have room to breathe.
    //
    // This is a shift-register keypad, not free-form text: the box always
    // shows exactly the two digits the ViewModel holds, with the cursor at
    // the end. Anything the user types is a keystroke to report, never text
    // to keep -- echoing it back left the digit inserted mid-string when
    // they tapped into the middle of "05", and the shift logic below then
    // read the OLD trailing digit as "what was typed".
    //
    // [revision] bumps on every keystroke so a fresh value reaches the field
    // even when the keystroke leaves the ViewModel's value alone (typing 0
    // into "00", or 1 into "11"). Without it nothing recomposes, and the
    // third digit the user typed stays on screen.
    var revision by remember { mutableIntStateOf(0) }
    val fieldValue = remember(value, revision) {
        TextFieldValue(text = value, selection = TextRange(value.length))
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val digits = newValue.text.filter { it.isDigit() }
            when {
                digits.length > 2 -> digits.last().digitToIntOrNull()?.let { onDigit(field, it) }
                digits.length < 2 -> onBackspace(field)
                // same length (e.g. no-op edit) -- ignore
            }
            revision++
        },
        modifier = Modifier.width(boxWidth),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Face,
        ),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { innerTextField ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    innerTextField()
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(FaceDim),
                )
            }
        },
    )
}

@Composable
fun ReadOnlyDuration(hours: Int, minutes: Int, seconds: Int, accent: Boolean, modifier: Modifier = Modifier) {
    Text(
        formatHms(hours, minutes, seconds),
        modifier = modifier,
        color = if (accent) Accent else Face,
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** mm:ss only, smaller -- sized to sit two-up in a pomodoro's side-by-side
 * work/rest display rather than take a full card width. */
@Composable
fun ReadOnlyDurationMmSs(minutes: Int, seconds: Int, accent: Boolean, modifier: Modifier = Modifier) {
    Text(
        "${pad2(minutes)}:${pad2(seconds)}",
        modifier = modifier,
        color = if (accent) Accent else Face,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
}
