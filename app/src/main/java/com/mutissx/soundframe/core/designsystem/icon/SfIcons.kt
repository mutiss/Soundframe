package com.mutissx.soundframe.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-drawn line icons on a 24-unit grid, so the iconography is unmistakably SOUNDFRAME's own. */
object SfIcons {

    private fun icon(name: String, filled: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        arcTo(r, r, 0f, false, true, x + w, y + r)
        lineTo(x + w, y + h - r)
        arcTo(r, r, 0f, false, true, x + w - r, y + h)
        lineTo(x + r, y + h)
        arcTo(r, r, 0f, false, true, x, y + h - r)
        lineTo(x, y + r)
        arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    val Mic = icon("mic") {
        roundRect(9f, 3f, 6f, 11.5f, 3f)
        moveTo(5.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11f)
        moveTo(12f, 17.5f)
        lineTo(12f, 21f)
    }

    val Import = icon("import") {
        moveTo(12f, 3.5f); lineTo(12f, 14.5f)
        moveTo(7.5f, 10f); lineTo(12f, 14.5f); lineTo(16.5f, 10f)
        moveTo(4.5f, 15.5f); lineTo(4.5f, 18f)
        arcTo(2f, 2f, 0f, false, false, 6.5f, 20f)
        lineTo(17.5f, 20f)
        arcTo(2f, 2f, 0f, false, false, 19.5f, 18f)
        lineTo(19.5f, 15.5f)
    }

    val Studio = icon("studio") {
        circle(12f, 12f, 8.5f)
        moveTo(7.5f, 12f)
        curveTo(9f, 8.2f, 10.5f, 8.2f, 12f, 12f)
        curveTo(13.5f, 15.8f, 15f, 15.8f, 16.5f, 12f)
    }

    val Gallery = icon("gallery") {
        roundRect(4f, 4f, 6.5f, 6.5f, 1.8f)
        roundRect(13.5f, 4f, 6.5f, 6.5f, 1.8f)
        roundRect(4f, 13.5f, 6.5f, 6.5f, 1.8f)
        roundRect(13.5f, 13.5f, 6.5f, 6.5f, 1.8f)
    }

    val Settings = icon("settings") {
        moveTo(4f, 7.5f); lineTo(20f, 7.5f)
        moveTo(4f, 16.5f); lineTo(20f, 16.5f)
        circle(9f, 7.5f, 2.3f)
        circle(15f, 16.5f, 2.3f)
    }

    val Back = icon("back") {
        moveTo(14.5f, 5f); lineTo(7.5f, 12f); lineTo(14.5f, 19f)
    }

    val Close = icon("close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    private fun PathBuilder.heart() {
        moveTo(12f, 20f)
        curveTo(12f, 20f, 3.5f, 14.8f, 3.5f, 9.3f)
        arcTo(4.3f, 4.3f, 0f, false, true, 12f, 7.4f)
        arcTo(4.3f, 4.3f, 0f, false, true, 20.5f, 9.3f)
        curveTo(20.5f, 14.8f, 12f, 20f, 12f, 20f)
        close()
    }

    val Heart = icon("heart") { heart() }
    val HeartFilled = icon("heart_filled", filled = true) { heart() }

    val Share = icon("share") {
        moveTo(12f, 3.5f); lineTo(12f, 14f)
        moveTo(8f, 7.5f); lineTo(12f, 3.5f); lineTo(16f, 7.5f)
        moveTo(8.5f, 10.5f); lineTo(7f, 10.5f)
        arcTo(2f, 2f, 0f, false, false, 5f, 12.5f)
        lineTo(5f, 18.5f)
        arcTo(2f, 2f, 0f, false, false, 7f, 20.5f)
        lineTo(17f, 20.5f)
        arcTo(2f, 2f, 0f, false, false, 19f, 18.5f)
        lineTo(19f, 12.5f)
        arcTo(2f, 2f, 0f, false, false, 17f, 10.5f)
        lineTo(15.5f, 10.5f)
    }

    val Download = icon("download") {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
        moveTo(5f, 19.5f); lineTo(19f, 19.5f)
    }

    val Play = icon("play", filled = true) {
        moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close()
    }

    val Pause = icon("pause", filled = true) {
        roundRect(7f, 5.5f, 3f, 13f, 1f)
        roundRect(14f, 5.5f, 3f, 13f, 1f)
    }

    val Trash = icon("trash") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(9.5f, 4f); lineTo(14.5f, 4f)
        moveTo(6.5f, 7f); lineTo(7.5f, 19f)
        arcTo(1.5f, 1.5f, 0f, false, false, 9f, 20.5f)
        lineTo(15f, 20.5f)
        arcTo(1.5f, 1.5f, 0f, false, false, 16.5f, 19f)
        lineTo(17.5f, 7f)
    }

    val Edit = icon("edit") {
        moveTo(4.5f, 19.5f); lineTo(5f, 15.8f); lineTo(15.3f, 5.5f)
        arcTo(2.1f, 2.1f, 0f, false, true, 18.5f, 8.7f)
        lineTo(8.2f, 19f); close()
    }

    val Sparkle = icon("sparkle") {
        moveTo(12f, 3f)
        curveTo(12.7f, 8.4f, 15.6f, 11.3f, 21f, 12f)
        curveTo(15.6f, 12.7f, 12.7f, 15.6f, 12f, 21f)
        curveTo(11.3f, 15.6f, 8.4f, 12.7f, 3f, 12f)
        curveTo(8.4f, 11.3f, 11.3f, 8.4f, 12f, 3f)
        close()
    }

    val Expand = icon("expand") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }

    val Check = icon("check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
    }

    val Undo = icon("undo") {
        moveTo(9f, 6.5f); lineTo(4.5f, 11f); lineTo(9f, 15.5f)
        moveTo(4.5f, 11f); lineTo(14.5f, 11f)
        arcTo(4.5f, 4.5f, 0f, false, true, 14.5f, 20f)
        lineTo(10.5f, 20f)
    }

    val Shield = icon("shield") {
        moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11.5f)
        curveTo(19.5f, 16f, 16.3f, 19.6f, 12f, 21f)
        curveTo(7.7f, 19.6f, 4.5f, 16f, 4.5f, 11.5f)
        lineTo(4.5f, 6f); close()
        moveTo(8.8f, 12f); lineTo(11f, 14.2f); lineTo(15.3f, 9.8f)
    }

    val Bookmark = icon("bookmark") {
        moveTo(6.5f, 4f); lineTo(17.5f, 4f); lineTo(17.5f, 20.5f); lineTo(12f, 16.5f); lineTo(6.5f, 20.5f); close()
    }

    val Video = icon("video") {
        roundRect(3f, 6f, 13f, 12f, 2.5f)
        moveTo(16f, 10.5f); lineTo(21f, 7.5f); lineTo(21f, 16.5f); lineTo(16f, 13.5f)
    }

    val Image = icon("image") {
        roundRect(3.5f, 3.5f, 17f, 17f, 3f)
        circle(9f, 9f, 1.6f)
        moveTo(4.5f, 17.5f); lineTo(10f, 12.5f); lineTo(13f, 15.5f); lineTo(15.5f, 13f); lineTo(20f, 17f)
    }

    val Sort = icon("sort") {
        moveTo(7.5f, 4f); lineTo(7.5f, 20f)
        moveTo(4.5f, 7f); lineTo(7.5f, 4f); lineTo(10.5f, 7f)
        moveTo(16.5f, 20f); lineTo(16.5f, 4f)
        moveTo(13.5f, 17f); lineTo(16.5f, 20f); lineTo(19.5f, 17f)
    }

    val Sound = icon("sound") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15.5f, 9f)
        arcTo(4f, 4f, 0f, false, true, 15.5f, 15f)
        moveTo(18f, 6.5f)
        arcTo(7.5f, 7.5f, 0f, false, true, 18f, 17.5f)
    }

    val Motion = icon("motion") {
        moveTo(3.5f, 12f)
        curveTo(6f, 5f, 8.5f, 5f, 11f, 12f)
        curveTo(13.5f, 19f, 16f, 19f, 18.5f, 12f)
        moveTo(18.5f, 12f); lineTo(20.5f, 12f)
    }
}
