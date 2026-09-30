package cc.ccwu.signalfeed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ccwu.signalfeed.data.F1HistoryRound
import kotlin.math.ceil

internal fun pointSegments(history: List<F1HistoryRound>, id: String): List<List<Pair<Int, Double>>> {
    val segments = mutableListOf<MutableList<Pair<Int, Double>>>()
    history.sortedBy { it.round }.forEach { round ->
        val points = round.drivers.firstOrNull { it.id == id }?.points
        if (points != null && points.isFinite()) {
            if (segments.lastOrNull()?.lastOrNull()?.first != round.round - 1) segments.add(mutableListOf())
            segments.last().add(round.round to points)
        }
    }
    return segments
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PointsHistoryChart(history: List<F1HistoryRound>, selection: List<String>?, teams: Boolean,
    onSelection: (List<String>) -> Unit) {
    val palette = LocalShellColors.current
    val roster = remember(history) { history.sortedByDescending { it.round }.flatMap { it.drivers }
        .distinctBy { it.id }.sortedByDescending { it.points } }
    val visible = selection ?: roster.take(if (teams) 3 else 5).map { it.id }
    var choosing by remember { mutableStateOf(false) }
    val colors = remember(roster, palette.dark) { roster.map { it.id }.mapIndexed { index, id ->
        id to Color.hsv((210f + index * 137.508f) % 360f, .70f, if (palette.dark) .95f else .72f)
    }.toMap() }
    val noun = if (teams) "车队" else "车手"
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        UiText("逐站累计积分", Modifier.padding(horizontal = 18.dp), fontSize = 18.sp)
        UiText("每站赛后积分 · 包含冲刺赛计分 · 缺失数据不连线", Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            color = palette.secondary, fontSize = 12.sp)
        if (roster.isEmpty()) {
            UiText("历史积分尚未同步，请稍后刷新。", Modifier.padding(18.dp), color = palette.secondary)
        } else {
            TextButton(onClick = { choosing = true }, modifier = Modifier.padding(horizontal = 6.dp)) {
                UiText("选择$noun（${roster.count { it.id in visible }}/${roster.size}）")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roster.filter { it.id in visible }.forEach { row ->
                    InputChip(selected = true, colors = InputChipDefaults.inputChipColors(selectedContainerColor = palette.muted), onClick = { onSelection(visible - row.id) }, label = {
                        UiText("${row.name} ×", color = colors.getValue(row.id), fontSize = 12.sp)
                    })
                }
            }
            if (visible.none { id -> roster.any { it.id == id } }) {
                UiText("已隐藏全部$noun，点击上方选择即可显示。", Modifier.padding(18.dp), fontSize = 13.sp)
            }
            val lastRound = history.maxOf { it.round }
            val maximum = (ceil((history.flatMap { it.drivers }.maxOfOrNull { it.points } ?: 0.0) / 50) * 50).coerceAtLeast(50.0)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartWidth = maxOf(maxWidth, (lastRound * 32 + 58).dp)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(chartWidth).height(250.dp)) {
                        val left = 42.dp.toPx(); val top = 16.dp.toPx()
                        val bottom = size.height - 32.dp.toPx(); val right = size.width - 18.dp.toPx()
                        fun x(round: Int) = left + (right - left) * (round - 1) / maxOf(1, lastRound - 1)
                        fun y(points: Double) = bottom - ((bottom - top) * points / maximum).toFloat()
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.secondary.toArgb(); textSize = 10.sp.toPx()
                        }
                        for (tick in 0..4) {
                            val value = maximum * tick / 4
                            drawLine(palette.line, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText(value.toInt().toString(), 3.dp.toPx(), y(value) + 4.dp.toPx(), paint)
                        }
                        for (round in 1..lastRound) drawContext.canvas.nativeCanvas.drawText(round.toString(), x(round) - 4.dp.toPx(), bottom + 19.dp.toPx(), paint)
                        roster.filter { it.id in visible }.forEach { row ->
                            val color = colors.getValue(row.id)
                            pointSegments(history, row.id).forEach { segment ->
                                segment.zipWithNext().forEach { (a, b) ->
                                    drawLine(color, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 2.dp.toPx())
                                }
                                segment.forEach { (round, points) -> drawCircle(color, 3.dp.toPx(), Offset(x(round), y(points))) }
                            }
                        }
                    }
                }
            }
            UiText("横轴：比赛站次 · 可左右滑动 · 点击图例隐藏", Modifier.padding(horizontal = 18.dp), color = palette.secondary, fontSize = 12.sp)
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }, containerColor = Color.White) {
        UiText("显示哪些$noun", Modifier.padding(horizontal = 18.dp), fontSize = 20.sp)
        Row(Modifier.padding(horizontal = 6.dp)) {
            TextButton(onClick = { onSelection(roster.map { it.id }) }) { UiText("全选") }
            TextButton(onClick = { onSelection(emptyList()) }) { UiText("清空") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { choosing = false }) { UiText("完成") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(roster, key = { it.id }) { row ->
                val checked = row.id in visible
                Row(Modifier.fillMaxWidth().clickable { onSelection(if (checked) visible - row.id else visible + row.id) }
                    .padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = null)
                    Box(Modifier.padding(horizontal = 12.dp).size(10.dp).background(colors.getValue(row.id)))
                    UiText(row.name, Modifier.weight(1f))
                }
            }
        }
    }
}
