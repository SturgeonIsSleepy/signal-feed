package cc.ccwu.signalfeed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ccwu.signalfeed.data.*
import java.util.Locale
import kotlin.math.*

private val ModelBlue = Color(0xFF1D9BF0)
private val ModelGray = Color(0xFF536471)
private fun decimal(value: Double) = String.format(Locale.CHINA, "%.2f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ModelLeaderboard(snapshot: AiSnapshot) {
    val cohort = remember(snapshot) { representativeModels(snapshot.models) }
    val dimensions = remember(cohort) { modelDimensions.filter { dimension -> cohort.any { dimension.value(it) != null } } }
    var dimensionId by rememberSaveable { mutableStateOf("score") }
    if (dimensions.isEmpty()) { Text("暂无可用模型指标，请刷新。", Modifier.padding(18.dp)); return }
    val dimension = dimensions.firstOrNull { it.id == dimensionId } ?: dimensions.first()
    val sorted = remember(cohort, dimension) { cohort.sortedBy { model -> dimension.value(model)?.let { if (dimension.lowerBetter) it else -it } ?: Double.POSITIVE_INFINITY } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = snapshot.models.firstOrNull { it.id == selectedId }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dimensions.forEach { row -> FilterChip(selected = dimension == row, onClick = { dimensionId = row.id }, label = { Text(row.label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEAF6FE), selectedLabelColor = ModelBlue)) }
        }
        key(dimensionId) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "method") {
                    Text("每个模型一条 · 点击查看雷达图与其他配置", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = ModelGray, fontSize = 12.sp)
                    Text("代表配置取综合分最高项；当前维度${if (dimension.lowerBetter) "越低越好" else "越高越好"}。比较范围为本次同步的 ${cohort.size} 个模型。",
                        Modifier.padding(horizontal = 18.dp), color = ModelGray, fontSize = 12.sp)
                    ModelBars(sorted.filter { dimension.value(it) != null }.take(10), dimension) { selectedId = it.id }
                }
                itemsIndexed(sorted, key = { index, model -> "ai:$index:${model.id}" }) { _, model ->
                    val values = cohort.mapNotNull { dimension.value(it) }
                    val value = dimension.value(model)
                    val rank = value?.let { dimensionRank(it, values, dimension.lowerBetter) }
                    val variants = snapshot.models.count { modelFamily(it) == modelFamily(model) }
                    Column(Modifier.fillMaxWidth().clickable { selectedId = model.id }.padding(18.dp)) {
                        Row {
                            Text(rank?.let { "#$it" } ?: "—", Modifier.width(42.dp), color = ModelBlue, fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(model.name.substringBefore(" ("), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(model.name.substringAfter(" (", "默认配置").removeSuffix(")") + " · ${model.creator.orEmpty()}", color = ModelGray, fontSize = 12.sp)
                            }
                            Text(value?.let(::decimal) ?: "未提供", color = ModelBlue, fontWeight = FontWeight.Bold)
                        }
                        Text("${dimension.unit} · $variants 种配置 · 多维对比 →", color = ModelGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    HorizontalDivider(color = Color(0xFFEFF3F4))
                }
                item(key = "source") {
                    Text("${snapshot.attribution} · 综合指数 v${snapshot.indexVersion ?: "—"}\n更新：${java.text.SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(java.util.Date(snapshot.updatedAt))}\n已显示本次同步的数据；免费来源未提供的指标不参与排名。",
                        Modifier.padding(18.dp), fontSize = 12.sp, color = ModelGray)
                }
            }
        }
    }
    if (selected != null) ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { selectedId = null }, containerColor = Color.White) {
        val comparison = cohort.filter { modelFamily(it) != modelFamily(selected) } + selected
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(18.dp)) {
            item {
                Text(selected.name.substringBefore(" ("), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(selected.name.substringAfter(" (", "默认配置").removeSuffix(")"), color = ModelGray, fontSize = 12.sp)
                Text("选择配置", color = ModelGray, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.models.filter { modelFamily(it) == modelFamily(selected) }.forEach { variant ->
                        FilterChip(variant.id == selected.id, { selectedId = variant.id }, label = { Text(variant.name.substringAfter(" (", "默认").removeSuffix(")").replace("Adaptive Reasoning, ", "").replace(", Default Fallback", "").replace(" Effort", "")) })
                    }
                }
                ModelRadar(selected, comparison)
                Text("蓝色：当前配置 · 灰色：榜单均值\n各轴按当前比较范围缩放，越外侧越优；价格和延迟反向。雷达半径不是原始分数。",
                    fontSize = 12.sp, color = ModelGray)
                Text("原始得分 / 与均值差异 / 排名", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 14.dp))
            }
            itemsIndexed(modelDimensions, key = { _, row -> row.id }) { _, row ->
                val values = comparison.mapNotNull { row.value(it) }
                val value = row.value(selected)
                val mean = values.takeIf { it.isNotEmpty() }?.average()
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text("${row.label} · ${row.unit}${if (row.lowerBetter) " · 越低越好" else ""}", fontWeight = FontWeight.SemiBold)
                    Text(if (value == null || mean == null) "来源未提供，未参与对比" else
                        "${decimal(value)}  ·  均值 ${decimal(mean)}  ·  差值 ${if (value >= mean) "+" else ""}${decimal(value - mean)}  ·  #${dimensionRank(value, values, row.lowerBetter)}/${values.size}",
                        color = ModelGray, fontSize = 12.sp)
                }
            }
            item { TextButton(onClick = { selectedId = null }) { Text("关闭") } }
        }
    }
}

@Composable private fun ModelBars(models: List<AiModel>, dimension: ModelDimension, onClick: (AiModel) -> Unit) {
    val max = models.mapNotNull { dimension.value(it) }.maxOrNull() ?: 0.0
    Column(Modifier.padding(vertical = 14.dp)) {
        Text("${dimension.label} · 前 ${models.size} · ${dimension.unit}", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            models.forEach { model ->
                val value = dimension.value(model) ?: 0.0
                Column(Modifier.width(86.dp).clickable { onClick(model) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(decimal(value), fontSize = 12.sp, color = ModelBlue)
                    Box(Modifier.height(140.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(34.dp).height((chartFraction(value, max) * 140).dp).background(ModelBlue))
                    }
                    Text(model.name.substringBefore(" ("), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable private fun ModelRadar(model: AiModel, cohort: List<AiModel>) {
    val axes = modelDimensions.filter { it.value(model) != null && cohort.mapNotNull(it::value).size >= 2 }
    if (axes.size < 3) { Text("可用维度不足 3 项，暂不绘制雷达图。", Modifier.padding(vertical = 24.dp)); return }
    var rankView by rememberSaveable(model.id) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!rankView, { rankView = false }, label = { Text("得分对比") })
        FilterChip(rankView, { rankView = true }, label = { Text("排名对比") })
    }
    Text(if (rankView) "排名换算为百分位：越外侧排名越靠前；并列同名次。" else "各维度与当前榜单均值对比。", color = ModelGray, fontSize = 12.sp)
    Canvas(Modifier.fillMaxWidth().height(310.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) * .32f
        fun point(index: Int, fraction: Float): Offset {
            val angle = -PI / 2 + 2 * PI * index / axes.size
            return Offset(center.x + cos(angle).toFloat() * radius * fraction, center.y + sin(angle).toFloat() * radius * fraction)
        }
        fun polygon(values: List<Float>): Path = Path().apply { values.forEachIndexed { index, value ->
            val p = point(index, value); if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }; close() }
        for (step in 1..4) drawPath(polygon(List(axes.size) { step / 4f }), Color(0xFFE2E8ED), style = Stroke(1.dp.toPx()))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.sp.toPx(); color = android.graphics.Color.rgb(83, 100, 113); textAlign = android.graphics.Paint.Align.CENTER
        }
        axes.forEachIndexed { index, row ->
            drawLine(Color(0xFFE2E8ED), center, point(index, 1f), 1.dp.toPx())
            val position = point(index, 1.24f)
            drawContext.canvas.nativeCanvas.drawText(row.label, position.x, position.y + 4.dp.toPx(), paint)
        }
        fun scaled(value: Double, values: List<Double>, row: ModelDimension): Float = if (rankView)
            1f - (dimensionRank(value, values, row.lowerBetter) - 1).toFloat() / maxOf(1, values.size - 1)
            else radarScale(value, values, row.lowerBetter)
        val mean = axes.map { row -> val values = cohort.mapNotNull(row::value)
            if (rankView) values.map { scaled(it, values, row) }.average().toFloat() else scaled(values.average(), values, row) }
        val own = axes.map { row -> scaled(row.value(model)!!, cohort.mapNotNull(row::value), row) }
        drawPath(polygon(mean), Color(0xFF8495A3).copy(alpha = .13f))
        drawPath(polygon(mean), Color(0xFF8495A3), style = Stroke(2.dp.toPx()))
        drawPath(polygon(own), ModelBlue.copy(alpha = .18f))
        drawPath(polygon(own), ModelBlue, style = Stroke(2.dp.toPx()))
        own.forEachIndexed { index, value -> drawCircle(ModelBlue, 3.dp.toPx(), point(index, value)) }
    }
}

@Composable internal fun LegacyModelList(snapshot: AiSnapshot) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    val dimension = listOf(modelDimensions[0], modelDimensions.first { it.id == "speed" }, modelDimensions.first { it.id == "input" })[metric]
    val rows = snapshot.models.sortedBy { dimension.value(it)?.let { value -> if (dimension.lowerBetter) value else -value } ?: Double.POSITIVE_INFINITY }
    Column {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("综合", "速度", "价格").forEachIndexed { index, label -> FilterChip(metric == index, { metric = index }, label = { Text(label) }) }
        }
        LazyColumn {
            item { Text("旧版配置列表", Modifier.padding(18.dp), color = ModelGray) }
            itemsIndexed(rows, key = { index, row -> "legacy:$index:${row.id}" }) { _, row ->
                Column(Modifier.padding(18.dp)) {
                    Text(row.name, fontWeight = FontWeight.Bold)
                    Text((dimension.value(row)?.let(::decimal) ?: "未提供") + " " + dimension.unit, color = ModelBlue)
                }
                HorizontalDivider(color = Color(0xFFEFF3F4))
            }
        }
    }
}
