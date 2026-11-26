package com.ejzimmer.tokei.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejzimmer.tokei.data.BREAD_FRIDGE_MESSAGE

/** What a bread timer shows once the evening proof is done: no countdown,
 * just the instruction and the button that ends the bake. */
@Composable
fun BreadFridgePrompt(onPutInFridge: () -> Unit) {
    Text(BREAD_FRIDGE_MESSAGE, color = Face, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Button(
        onClick = onPutInFridge,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
    ) {
        Icon(CheckIcon, contentDescription = "It's in the fridge", modifier = Modifier.size(20.dp))
    }
}
