package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 시연 파일에서 쓴 선 아이콘(24×24, 선 굵기 1.8). 색은 Icon의 tint로 바꾼다. */
object AppIcons {
    private fun line(name: String, vararg paths: String, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Calendar = line(
        "calendar",
        "M5.5 5h13a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z",
        "M3.5 10H20.5", "M8 3V7", "M16 3V7",
    )
    val Pencil = line("pencil", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Document = line("document", "M6 3h8l4 4v14H6z", "M14 3v4h4", "M9 12h6", "M9 16h6")
    val Gear = line(
        "gear",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
    val Mic = line(
        "mic",
        "M12 2a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0v-6a3 3 0 0 1 3-3z",
        "M5 10a7 7 0 0 0 14 0", "M12 17v4",
    )
    val Chat = line("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z", stroke = 2.2f)
    val Back = line("back", "M15 5L8 12L15 19", stroke = 2.6f)
    val Forward = line("forward", "M9 5L16 12L9 19", stroke = 2.6f)
    val Plus = line("plus", "M12 5v14", "M5 12h14", stroke = 2.8f)
    val PencilSmall = line("pencil-small", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4", stroke = 2.2f)
    val Trash = line("trash", "M4 7h16", "M9 7V4.5h6V7", "M6 7l1 13h10l1-13", stroke = 2.2f)
    val TrashOpen = line("trash-open", "M4 7h16", "M9 7V4.5h6V7", "M6 7l1 13h10l1-13", "M10 11v5", "M14 11v5", stroke = 1.45f)
    val ChevronDown = line("chevron-down", "M5 9l7 7l7-7", stroke = 2.6f)
    val CheckCircle = line("check-circle", "M22.6 12a10.6 10.6 0 1 1-21.2 0a10.6 10.6 0 1 1 21.2 0z", "M7 12.5l3.6 3.5L17 9", stroke = 1.9f)
    val Question = line("question", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .8-1 1.5v.7", "M12 17v.01", stroke = 2.4f)
    val PlusBold = line("plus-bold", "M12 5v14", "M5 12h14", stroke = 2.4f)
    val PencilBold = line("pencil-bold", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4", stroke = 2.4f)
    val Folder = line("folder", "M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z", stroke = 1.6f)
    val Phone = line("phone", "M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M11 18h2", stroke = 1.6f)
    val Warning = line("warning", "M12 3l9.5 17h-19z", "M12 10v4", "M12 17v.01", stroke = 1.8f)
    val TextSize = line("text-size", "M3 19L8 6l5 13", "M4.8 14.5h6.4", "M14 19l3.5-9l3.5 9", "M15.2 16h4.6")
    val SpeakerIcon = line("speaker", "M4 9h4l5-4v14l-5-4H4z", "M16.5 8.5a5 5 0 0 1 0 7", "M19 6a8.5 8.5 0 0 1 0 12")
    val AlarmClock = line("alarm", "M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 9v4l3 2", "M3 5.5L6 3", "M21 5.5L18 3")
    val Person = line("person", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M4 21a8 8 0 0 1 16 0")
    val Bell = line("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val Play = line("play", "M7 4l13 8l-13 8z", stroke = 2.2f)
}
