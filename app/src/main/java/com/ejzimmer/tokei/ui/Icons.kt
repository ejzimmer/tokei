package com.ejzimmer.tokei.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The handful of glyphs the buttons need, drawn here rather than pulled in
// from material-icons-extended -- that artifact carries a few thousand
// vectors to get four of them. Every path is on the standard 24x24 icon
// grid, and the fill is a placeholder: Icon() tints them at the call site.

/** Circular arrow -- the reset buttons. */
val ResetIcon = iconVector(
    "reset",
    "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.58 8 8 8c3.73 0 6.84-2.55 " +
        "7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 " +
        "4.22 1.78L13 11h7V4l-2.35 2.35z",
)

/** Triangle -- start/resume. */
val StartIcon = iconVector("start", "M8 5v14l11-7z")

/** Two bars -- pause, and the work timer's stop, which is the same act. */
val PauseIcon = iconVector("pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")

/** A beamed pair of notes -- previewing a timer's tone. Three paths: the
 * stems and their beam as one outline, then a head hung off each stem. */
val MusicNotesIcon = iconVector(
    "musicNotes",
    "M9 17V5l11-2v12h-2V5.4l-7 1.2V17z",
    "M3.9 17a3 2.4 0 1 0 6 0a3 2.4 0 1 0 -6 0z",
    "M12.9 15a3 2.4 0 1 0 6 0a3 2.4 0 1 0 -6 0z",
)

private fun iconVector(name: String, vararg pathData: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    pathData.forEach { builder.addPath(addPathNodes(it), fill = SolidColor(Color.White)) }
    return builder.build()
}
