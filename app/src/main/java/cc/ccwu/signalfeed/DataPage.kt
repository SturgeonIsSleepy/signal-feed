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
import java.util.Date
import java.util.Locale

private val DataInk: Color @Composable get() = LocalShellColors.current.text
private val DataSoft: Color @Composable get() = LocalShellColors.current.secondary
private val DataBlue: Color @Composable get() = LocalShellColors.current.accent

internal fun dataRowKey(section: String, id: String, index: Int) = "$section:$index:$id"
internal fun chartFraction(value: Double?, maximum: Double): Float =
    if (value == null || !value.isFinite() || !maximum.isFinite() || maximum <= 0) 0f
    else (value / maximum).coerceIn(0.0, 1.0).toFloat()

@Composable
internal fun DataPage(f1: F1Snapshot?, ai: AiSnapshot?, f1Message: String?, aiMessage: String?, now: Long,
    onF1Refresh: () -> Unit, onAiRefresh: () -> Unit, tabs: List<Int> = listOf(0, 1, 2)) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs.getOrElse(selectedTab) { tabs.first() }
    var teams by rememberSaveable { mutableStateOf(false) }
    var selectedDrivers by rememberSaveable(f1?.year) { mutableStateOf<List<String>?>(null) }
    var selectedTeams by rememberSaveable(f1?.year) { mutableStateOf<List<String>?>(null) }
    val calendarState = rememberLazyListState()
    val driverState = rememberLazyListState()
    val teamState = rememberLazyListState()
    val upcoming = f1?.races?.filter { it.startAt > now }?.minByOrNull { it.startAt } ?: f1?.next
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalShellColors.current.accentSurface, selectedLabelColor = DataBlue)
    LaunchedEffect(tab) { if (tab == 2) onAiRefresh() else onF1Refresh() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                UiText("数据", color = DataInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                UiText("赛程、积分与模型表现", color = DataSoft, fontSize = 12.sp)
            }
            IconButton(onClick = { if (tab == 2) onAiRefresh() else onF1Refresh() }) {
                Icon(FeedIcons.Refresh, "刷新当前数据", tint = DataInk)
            }
        }
        TabRow(selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex), containerColor = LocalShellColors.current.surface, contentColor = DataBlue) {
            tabs.map { listOf("赛历", "积分", "模型榜")[it] }.forEachIndexed { index, name ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, selectedContentColor = DataBlue,
                    unselectedContentColor = DataSoft, text = { UiText(name) })
            }
        }
        val message = if (tab == 2) aiMessage else f1Message
        message?.let { UiText(it, Modifier.fillMaxWidth().background(LocalShellColors.current.muted).padding(12.dp),
            color = DataSoft, fontSize = 12.sp) }
        if ((tab == 2 && ai == null) || (tab != 2 && f1 == null)) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                UiText("暂时没有数据", fontWeight = FontWeight.Bold)
                UiText("连接网络后刷新，已有数据会保留在本机。", color = DataSoft, fontSize = 13.sp)
                TextButton(onClick = { if (tab == 2) onAiRefresh() else onF1Refresh() }) { UiText("刷新") }
            }
        } else when (tab) {
            0 -> LazyColumn(state = calendarState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "calendar:next") {
                    upcoming?.let { race ->
                        Column(Modifier.padding(16.dp).fillMaxWidth().background(LocalShellColors.current.accentSurface, RoundedCornerShape(16.dp)).padding(18.dp)) {
                            val hours = ((race.startAt - now).coerceAtLeast(0) / 3_600_000)
                            UiText("${if (race.startAt > now) "下一场" else "最近一场"} · 第 ${race.round} 站", color = DataBlue, fontWeight = FontWeight.Bold)
                            UiText(rememberContentTranslation(race.name).body, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = DataInk)
                            val countdown = if (race.startAt <= now) "已开赛" else if (hours == 0L) "${(race.startAt - now) / 60_000} 分钟后" else "${hours / 24} 天 ${hours % 24} 小时后"
                            UiText("$countdown · ${race.locality.orEmpty()}", color = DataSoft,
                                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                            race.sessions.forEach { session ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    UiText(sessionName(session.name), Modifier.weight(1f), fontSize = 13.sp, color = DataInk)
                                    UiText(dataDate(session.startAt), fontSize = 13.sp, color = DataSoft)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                UiText("正赛", Modifier.weight(1f), fontSize = 13.sp, color = DataBlue, fontWeight = FontWeight.Bold)
                                UiText(dataDate(race.startAt), fontSize = 13.sp, color = DataBlue)
                            }
                        }
                    }
                    SectionTitle("${f1!!.year} 赛季赛历", "时间按手机所在时区显示")
                }
                itemsIndexed(f1!!.races, key = { index, race -> dataRowKey("race", race.round.toString(), index) }) { _, race ->
                    val next = race.round == upcoming?.round && race.startAt > now
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        UiText(race.round.toString().padStart(2, '0'), Modifier.background(if (next) DataBlue else LocalShellColors.current.muted, RoundedCornerShape(8.dp)).padding(10.dp),
                            color = if (next) Color.White else DataSoft, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            UiText(rememberContentTranslation(race.name).body, color = DataInk, fontSize = 15.sp, fontWeight = if (next) FontWeight.Bold else FontWeight.Medium)
                            UiText("${dataDate(race.startAt)} · ${if (next) "下一场" else if (race.startAt < now) "已开始" else "待举行"}",
                                color = if (next) DataBlue else DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    HorizontalDivider(color = LocalShellColors.current.line)
                }
                f1.lastResult?.let { result ->
                    item(key = "result:title") { SectionTitle("最近赛果", result.name) }
                    itemsIndexed(result.finishers, key = { index, row -> dataRowKey("result", row.position.toString(), index) }) { _, row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                            UiText("${row.position}", Modifier.width(36.dp), color = DataBlue, fontWeight = FontWeight.Bold)
                            Column { UiText(row.name, color = DataInk); UiText(row.team.orEmpty(), color = DataSoft, fontSize = 12.sp) }
                        }
                    }
                }
                item(key = "calendar:footer") { DataFooter(f1.attribution, f1.updatedAt) }
            }
            1 -> Column {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !teams, onClick = { teams = false }, label = { UiText("车手积分") }, colors = chipColors)
                    FilterChip(selected = teams, onClick = { teams = true }, label = { UiText("车队积分") }, colors = chipColors)
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
            UiText(title, Modifier.weight(1f).padding(end = 12.dp), color = DataInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            UiText(amount, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (subtitle.isNotBlank()) UiText(subtitle, color = DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(LocalShellColors.current.muted, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}
@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        UiText(title, color = DataInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        UiText(subtitle, color = DataSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
@Composable private fun DataFooter(source: String, updatedAt: Long) {
    UiText("$source · 更新于 ${dataDate(updatedAt)}\n已显示本次同步的数据", Modifier.fillMaxWidth().padding(18.dp), color = DataSoft, fontSize = 12.sp)
}
private fun sessionName(name: String): String = when (name) {
    "FirstPractice" -> "一练"; "SecondPractice" -> "二练"; "ThirdPractice" -> "三练"
    "Qualifying" -> "排位赛"; "Sprint" -> "冲刺赛"; "SprintQualifying", "SprintShootout" -> "冲刺排位"
    else -> name
}
@Composable private fun dataDate(epoch: Long) = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
    java.text.DateFormat.SHORT, Locale.forLanguageTag(LocalUiLanguage.current.code)).format(Date(epoch))
private fun number(value: Double): String = if (!value.isFinite()) "—" else if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.CHINA, "%.2f", value)
