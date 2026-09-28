package cc.ccwu.signalfeed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object FeedIcons {
    val Home = icon("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 14f)
        lineTo(14f, 14f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f)
    }
    val Race = icon("Race") {
        moveTo(5f, 21f); lineTo(5f, 3f); lineTo(20f, 3f); lineTo(17f, 8f)
        lineTo(20f, 13f); lineTo(5f, 13f)
    }
    val Ai = icon("Ai") {
        moveTo(12f, 3f); lineTo(15f, 9f); lineTo(21f, 12f); lineTo(15f, 15f)
        lineTo(12f, 21f); lineTo(9f, 15f); lineTo(3f, 12f); lineTo(9f, 9f); close()
    }
    val Settings = icon("Settings") {
        moveTo(4f, 7f); lineTo(9f, 7f); moveTo(15f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(13f, 17f); moveTo(19f, 17f); lineTo(20f, 17f)
        moveTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 10f); lineTo(9f, 10f); close()
        moveTo(13f, 14f); lineTo(19f, 14f); lineTo(19f, 20f); lineTo(13f, 20f); close()
    }
    val Back = icon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
    val Refresh = icon("Refresh") {
        moveTo(20f, 5f); lineTo(20f, 10f); lineTo(15f, 10f)
        moveTo(20f, 10f); curveTo(18f, 3f, 9f, 2f, 5f, 8f)
        curveTo(0f, 16f, 11f, 25f, 19f, 17f)
    }
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
}
