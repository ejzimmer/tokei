package com.ejzimmer.tokei.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

@Composable
private fun ClockColon() {
    Text(
        ":",
        color = FaceDim,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DigitShiftField(
    value: String,
    field: DurationField,
    onDigit: (DurationField, Int) -> Unit,
    onBackspace: (DurationField) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = { newValue ->
            val digits = newValue.filter { it.isDigit() }
            when {
                digits.length > 2 -> digits.last().digitToIntOrNull()?.let { onDigit(field, it) }
                digits.length < 2 -> onBackspace(field)
                // same length (e.g. no-op edit) -- ignore
            }
        },
        modifier = Modifier.width(64.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Face,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Accent,
            unfocusedIndicatorColor = FaceDim,
        ),
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
