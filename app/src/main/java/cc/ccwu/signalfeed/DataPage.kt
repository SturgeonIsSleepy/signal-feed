package cc.ccwu.signalfeed

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ccwu.signalfeed.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DataInk = Color(0xFF0F1419)
private val DataSoft = Color(0xFF536471)
private val DataBlue = Color(0xFF1D9BF0)

internal fun dataRowKey(section: String, id: String, index: Int) = "$section:$index:$id"
internal fun chartFraction(value: Double?, maximum: Double): Float =
    if (value == null || !value.isFinite() || !maximum.isFinite() || maximum <= 0) 0f
    else (value / maximum).coerceIn(0.0, 1.0).toFloat()

@Composable
internal fun DataPage(f1: F1Snapshot?, ai: AiSnapshot?, f1Message: String?, aiMessage: String?, now: Long,
    onF1Refresh: () -> Unit, onAiRefresh: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var teams by rememberSaveable { mutableStateOf(false) }
    var selectedDrivers by rememberSaveable(f1?.year) { mutableStateOf<List<String>?>(null) }
    var selectedTeams by rememberSaveable(f1?.year) { mutableStateOf<List<String>?>(null) }
    val calendarState = rememberLazyListState()
    val driverState = rememberLazyListState()
    val teamState = rememberLazyListState()
    val upcoming = f1?.races?.filter { it.startAt > now }?.minByOrNull { it.startAt } ?: f1?.next
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEAF6FE), selectedLabelColor = DataBlue)
    LaunchedEffect(tab) { if (tab == 2) onAiRefresh() else onF1Refresh() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text("数据", color = DataInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("赛程、积分与模型表现", color = DataSoft, fontSize = 12.sp)
            }
            IconButton(onClick = { if (tab == 2) onAiRefresh() else onF1Refresh() }) {
                Icon(FeedIcons.Refresh, "刷新当前数据", tint = DataInk)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = DataBlue) {
            listOf("赛历", "积分", "模型榜").forEachIndexed { index, name ->
                Tab(selected = tab == index, onClick = { tab = index }, selectedContentColor = DataBlue,
                    unselectedContentColor = DataSoft, text = { Text(name) })
            }
        }
        val message = if (tab == 2) aiMessage else f1Message
        message?.let { Text(it, Modifier.fillMaxWidth().background(Color(0xFFFFF4E5)).padding(12.dp),
            color = DataSoft, fontSize = 12.sp) }
        if ((tab == 2 && ai == null) || (tab != 2 && f1 == null)) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂时没有数据", fontWeight = FontWeight.Bold)
                Text("连接网络后刷新，已有数据会保留在本机。", color = DataSoft, fontSize = 13.sp)
                TextButton(onClick = { if (tab == 2) onAiRefresh() else onF1Refresh() }) { Text("刷新") }
            }
        } else when (tab) {
            0 -> LazyColumn(state = calendarState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "calendar:next") {
                    upcoming?.let { race ->
                        Column(Modifier.padding(16.dp).fillMaxWidth().background(Color(0xFFEAF6FE), RoundedCornerShape(16.dp)).padding(18.dp)) {
                            val hours = ((race.startAt - now).coerceAtLeast(0) / 3_600_000)
                            Text("${if (race.startAt > now) "下一场" else "最近一场"} · 第 ${race.round} 站", color = DataBlue, fontWeight = FontWeight.Bold)
                            Text(race.name, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = DataInk)
                            val countdown = if (race.startAt <= now) "已开赛" else if (hours == 0L) "${(race.startAt - now) / 60_000} 分钟后" else "${hours / 24} 天 ${hours % 24} 小时后"
                            Text("$countdown · ${race.locality.orEmpty()}", color = DataSoft,
                                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                            race.sessions.forEach { session ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text(sessionName(session.name), Modifier.weight(1f), fontSize = 13.sp, color = DataInk)
                                    Text(dataDate(session.startAt), fontSize = 13.sp, color = DataSoft)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text("正赛", Modifier.weight(1f), fontSize = 13.sp, color = DataBlue, fontWeight = FontWeight.Bold)
                                Text(dataDate(race.startAt), fontSize = 13.sp, color = DataBlue)
                            }
                        }
                    }
                    SectionTitle("${f1!!.year} 赛季赛历", "时间按手机所在时区显示")
                }
                itemsIndexed(f1!!.races, key = { index, race -> dataRowKey("race", race.round.toString(), index) }) { _, race ->
                    val next = race.round == upcoming?.round && race.startAt > now
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(race.round.toString().padStart(2, '0'), Modifier.background(if (next) DataBlue else Color(0xFFF0F3F5), RoundedCornerShape(8.dp)).padding(10.dp),
                            color = if (next) Color.White else DataSoft, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            Text(race.name, color = DataInk, fontSize = 15.sp, fontWeight = if (next) FontWeight.Bold else FontWeight.Medium)
                            Text("${dataDate(race.startAt)} · ${if (next) "下一场" else if (race.startAt < now) "已开始" else "待举行"}",
                                color = if (next) DataBlue else DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEFF3F4))
                }
                f1.lastResult?.let { result ->
                    item(key = "result:title") { SectionTitle("最近赛果", result.name) }
                    itemsIndexed(result.finishers, key = { index, row -> dataRowKey("result", row.position.toString(), index) }) { _, row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text("${row.position}", Modifier.width(36.dp), color = DataBlue, fontWeight = FontWeight.Bold)
                            Column { Text(row.name, color = DataInk); Text(row.team.orEmpty(), color = DataSoft, fontSize = 12.sp) }
                        }
                    }
                }
                item(key = "calendar:footer") { DataFooter(f1.attribution, f1.updatedAt) }
            }
            1 -> Column {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !teams, onClick = { teams = false }, label = { Text("车手积分") }, colors = chipColors)
                    FilterChip(selected = teams, onClick = { teams = true }, label = { Text("车队积分") }, colors = chipColors)
                }
                val rows = if (teams) f1!!.constructors.map { F1Standing(it.position, it.name, null, it.points) } else f1!!.drivers
                val maximum = rows.maxOfOrNull { it.points } ?: 0.0
                key(teams) {
                    LazyColumn(state = if (teams) teamState else driverState, contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "standing:history") {
                            PointsHistoryChart(if (teams) f1.constructorHistory else f1.driverHistory,
                                if (teams) selectedTeams else selectedDrivers, teams) {
                                if (teams) selectedTeams = it else selectedDrivers = it
                            }
                        }
                        item(key = "standing:title") { SectionTitle(if (teams) "车队积分" else "车手积分", "条形长度表示积分，使用相同刻度") }
                        itemsIndexed(rows, key = { index, row -> dataRowKey(if (teams) "team" else "driver", row.name, index) }) { _, row ->
                            ChartRow("${row.position}. ${row.name}", row.team.orEmpty(), number(row.points) + " 分", chartFraction(row.points, maximum), DataBlue)
                        }
                        item(key = "standing:footer") { DataFooter(f1.attribution, f1.updatedAt) }
                    }
                }
            }
            else -> if (LocalFeatures.current.enabled(Feature.MODEL_CHARTS)) ModelLeaderboard(ai!!) else LegacyModelList(ai!!)
        }
    }
}

@Composable private fun ChartRow(title: String, subtitle: String, amount: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, Modifier.weight(1f).padding(end = 12.dp), color = DataInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(amount, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (subtitle.isNotBlank()) Text(subtitle, color = DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(Color(0xFFEDF1F4), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}
@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, color = DataInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
@Composable private fun DataFooter(source: String, updatedAt: Long) {
    Text("$source · 更新于 ${dataDate(updatedAt)}\n已显示本次同步的数据", Modifier.fillMaxWidth().padding(18.dp), color = DataSoft, fontSize = 12.sp)
}
private fun sessionName(name: String): String = when (name) {
    "FirstPractice" -> "一练"; "SecondPractice" -> "二练"; "ThirdPractice" -> "三练"
    "Qualifying" -> "排位赛"; "Sprint" -> "冲刺赛"; "SprintQualifying", "SprintShootout" -> "冲刺排位"
    else -> name
}
private fun dataDate(epoch: Long) = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(epoch))
private fun number(value: Double): String = if (!value.isFinite()) "—" else if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.CHINA, "%.2f", value)
