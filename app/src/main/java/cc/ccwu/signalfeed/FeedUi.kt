package cc.ccwu.signalfeed

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.ccwu.signalfeed.data.AccountEntity
import cc.ccwu.signalfeed.data.FeedPost
import cc.ccwu.signalfeed.data.TopicEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF0F1419)
private val Soft = Color(0xFF536471)
private val Line = Color(0xFFEFF3F4)
private val Blue = Color(0xFF1D9BF0)
private val Topics = listOf("全部", "F1", "AI", "玩机", "国内", "全球")

@Composable
fun SignalFeedApp(model: FeedViewModel) {
    val feed by model.feed.collectAsStateWithLifecycle()
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val topicRows by model.topics.collectAsStateWithLifecycle()
    val f1 by model.f1.collectAsStateWithLifecycle()
    val ai by model.ai.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(0) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.FOR_YOU) }
    var topic by rememberSaveable { mutableStateOf("全部") }
    val homeListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dataStateHolder = rememberSaveableStateHolder()
    var detail by remember { mutableStateOf<FeedEntry?>(null) }
    var documentUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val moduleHome = remember(context) { (context as? android.app.Activity)?.let { RuntimeModules.home(it) } }
    val preferences = remember { context.getSharedPreferences("reading", Context.MODE_PRIVATE) }
    val modStore = remember { ModStore(context) }
    var featureSettings by remember { mutableStateOf(FeatureSettings(Feature.entries.associate { it.key to preferences.getBoolean(it.key, true) })) }
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyBreaking by rememberSaveable { mutableStateOf(false) }
    var directX by remember { mutableStateOf(preferences.getBoolean("direct_x", false)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    LaunchedEffect(model.targetPostId.value) {
        if (model.targetPostId.value != null) {
            page = 0; timeline = Timeline.FOR_YOU; topic = "全部"; accountFilter = null; onlyBreaking = false; detail = null; documentUrl = null
        }
    }
    val items = remember(feed, timeline, topic, now, topicRows, featureSettings, accountFilter, onlyBreaking, modStore.packs) {
        visibleFeed(feed, timeline, topic.takeUnless { it == "全部" }, now, topicRows.associate { it.id to it.weight }).filter {
            (featureSettings.enabled(Feature.SUBSCRIPTIONS) || !it.account.id.startsWith("sub:")) &&
            (featureSettings.enabled(Feature.NEW_SOURCES) || it.account.id !in newMediaAccounts) &&
            (!featureSettings.enabled(Feature.LOW_INFORMATION) || !LowInformationFilter.shouldHide(it.post.body, it.account.id, it.post.originalUrl)) &&
            (!featureSettings.enabled(Feature.EDITORIAL_FILTER) || !LowInformationFilter.isEditorialOrTitleOnly(it.post.body, it.account.id)) &&
            (!featureSettings.enabled(Feature.MODS) || !modStore.hides(it.post.body, it.account.id)) &&
            (accountFilter == null || it.account.id == accountFilter) && (!onlyBreaking || it.post.breaking)
        }
    }
    val entries = remember(items, f1) { collapseRacePosts(items, f1?.races.orEmpty()) }
    LaunchedEffect(model.targetPostId.value, entries) {
        val target = model.targetPostId.value ?: return@LaunchedEffect
        val index = entries.indexOfFirst { entry -> entry.posts.any { it.post.id == target } }
        if (index >= 0) {
            val entry = entries[index]
            homeListState.scrollToItem(index)
            detail = entry
            detailListState.scrollToItem(entry.posts.indexOfFirst { it.post.id == target } + if (entry.race == null) 0 else 1)
            model.targetPostId.value = null
        }
    }
    BackHandler(detail != null || documentUrl != null) {
        if (documentUrl != null) documentUrl = null else detail = null
    }
    val actions = FeedActions(
        topic = { topic = it; accountFilter = null; onlyBreaking = false; detail = null; page = 0; scope.launch { homeListState.scrollToItem(0) } },
        account = { accountFilter = it; topic = "全部"; onlyBreaking = false; detail = null; page = 0; scope.launch { homeListState.scrollToItem(0) } },
        breaking = { onlyBreaking = true; accountFilter = null; detail = null; page = 0; scope.launch { homeListState.scrollToItem(0) } },
        settings = { page = 2 }
    )
    CompositionLocalProvider(LocalFeatures provides featureSettings, LocalFeedActions provides actions, LocalMods provides modStore) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Color.White, surface = Color.White)) {
        Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                if (documentUrl != null) PdfDocumentPage(documentUrl!!, onBack = { documentUrl = null },
                    onOriginal = { openOriginal(context, documentUrl!!) })
                else if (detail != null) FeedDetailPage(entries.firstOrNull { it.key == detail!!.key } ?: detail!!,
                    onBack = { detail = null }, onDocument = { documentUrl = it }, listState = detailListState)
                else when (page) {
                    0 -> if (moduleHome != null) androidx.compose.ui.viewinterop.AndroidView(factory = { moduleHome }, modifier = Modifier.fillMaxSize()) else Column {
                        if (accountFilter != null || onlyBreaking) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (onlyBreaking) "仅显示 Breaking" else "账号：" + accounts.firstOrNull { it.id == accountFilter }?.name.orEmpty(), Modifier.weight(1f), color = Blue)
                            TextButton(onClick = { accountFilter = null; onlyBreaking = false }) { Text("清除筛选") }
                        }
                        FeedPage(entries, timeline, topic, model.syncing.value, model.message.value,
                        onTimeline = { timeline = it; scope.launch { homeListState.scrollToItem(0) } },
                        onTopic = { topic = it; accountFilter = null; onlyBreaking = false; scope.launch { homeListState.scrollToItem(0) } }, onRefresh = { model.refresh() },
                        onOpenDetail = { entry ->
                            val original = entry.posts.singleOrNull()?.post?.originalUrl
                            if (directX && original != null && isXUrl(original)) openOriginal(context, original)
                            else { detail = entry; scope.launch { detailListState.scrollToItem(0) } }
                        }, listState = homeListState,
                        onFollow = { model.follow(it.account, !it.account.followed) },
                        onMute = { model.mute(it.account, true) })
                    }
                    1 -> dataStateHolder.SaveableStateProvider("data") {
                        DataPage(f1, ai, model.f1Message.value, model.aiMessage.value, now,
                            onF1Refresh = { model.loadF1() }, onAiRefresh = { model.loadAi() })
                    }
                    else -> AccountsPage(accounts, topicRows, model, directX, { directX = it; preferences.edit().putBoolean("direct_x", it).apply() }, featureSettings) { feature, value ->
                        featureSettings = FeatureSettings(featureSettings.values + (feature.key to value))
                        preferences.edit().putBoolean(feature.key, value).apply()
                    }
                }
            }
            if (detail == null && documentUrl == null) {
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(FeedIcons.Home to "首页", FeedIcons.Race to "数据", FeedIcons.Settings to "设置").forEachIndexed { index, (icon, label) ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { page = index },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, label, Modifier.size(24.dp), tint = if (page == index) Blue else Soft)
                            Spacer(Modifier.height(3.dp))
                            Text(label, fontSize = 11.sp, color = if (page == index) Blue else Soft)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FeedPage(
    entries: List<FeedEntry>, timeline: Timeline, topic: String, syncing: Boolean, message: String?,
    onTimeline: (Timeline) -> Unit, onTopic: (String) -> Unit, onRefresh: () -> Unit,
    onOpenDetail: (FeedEntry) -> Unit, listState: LazyListState,
    onFollow: (FeedPost) -> Unit, onMute: (FeedPost) -> Unit
) {
    val actions = LocalFeedActions.current
    val interactive = LocalFeatures.current.enabled(Feature.LABEL_ACTIONS)
    Column {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("S", Modifier.clickable(enabled = interactive) { actions.settings() }.background(Ink, CircleShape).padding(horizontal = 11.dp, vertical = 5.dp),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("SignalFeed", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            IconButton(onClick = onRefresh, enabled = !syncing) {
                if (syncing) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                else Icon(FeedIcons.Refresh, "刷新信息流", tint = Ink)
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            listOf(Timeline.FOR_YOU to "For You", Timeline.FOLLOWING to "Following").forEach { (tab, label) ->
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onTimeline(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(label, color = if (tab == timeline) Ink else Soft,
                        fontSize = 15.sp, fontWeight = if (tab == timeline) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.width(62.dp).height(4.dp).background(if (tab == timeline) Blue else Color.Transparent,
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Topics.forEach { label ->
                val selected = label == topic
                Box(Modifier.background(if (selected) Ink else Color(0xFFF7F9F9), RoundedCornerShape(18.dp))
                    .clickable { onTopic(label) }.padding(horizontal = 15.dp, vertical = 7.dp)) {
                    Text(label, color = if (selected) Color.White else Ink, fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Line)
        if (message != null) Row(Modifier.fillMaxWidth().background(Color(0xFFF5F8FA)).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = Soft, fontSize = 12.sp, lineHeight = 18.sp)
            TextButton(onClick = onRefresh, enabled = !syncing) { Text("重试") }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (syncing) "正在获取消息…" else "暂时没有消息", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (timeline == Timeline.FOLLOWING) "关注账号后，这里会按时间显示动态" else "试试其他主题，或稍后刷新", color = Soft,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onRefresh, enabled = !syncing) { Text("刷新消息") }
            }
        } else LazyColumn(state = listState) {
            items(entries, key = { it.key }) { entry ->
                if (entry.race != null) RaceRow(entry, onClick = { onOpenDetail(entry) })
                else PostRow(entry.posts.single(), onClick = { onOpenDetail(entry) },
                    onFollow = { onFollow(entry.posts.single()) }, onMute = { onMute(entry.posts.single()) })
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun PostRow(item: FeedPost, onClick: () -> Unit, onFollow: () -> Unit, onMute: () -> Unit) {
    val context = LocalContext.current
    val actions = LocalFeedActions.current
    val interactive = LocalFeatures.current.enabled(Feature.LABEL_ACTIONS)
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).clickable(enabled = interactive) { actions.account(item.account.id) }.background(avatarColor(item.account.id), CircleShape), contentAlignment = Alignment.Center) {
            Text(item.account.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.account.name, modifier = Modifier.clickable(enabled = interactive) { actions.account(item.account.id) }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.account.handle} · ${relativeTime(item.post.publishedAt)}", color = Soft, fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Text("⋯", color = Soft, fontSize = 24.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("阅读详情") }, onClick = { menu = false; onClick() })
                        DropdownMenuItem(text = { Text(if (item.account.followed) "取消关注" else "关注账号") }, onClick = { menu = false; onFollow() })
                        DropdownMenuItem(text = { Text("屏蔽此账号") }, onClick = { menu = false; onMute() })
                        DropdownMenuItem(text = { Text("分享消息") }, onClick = { menu = false; sharePost(context, item) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val readable = readablePost(item.post.body, item.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT))
            val heading = readable.body.substringBefore("\n\n")
            Text(heading, color = Ink, fontSize = 16.sp, lineHeight = 23.sp,
                fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (heading != readable.body) {
                Spacer(Modifier.height(6.dp))
                Text(readable.body.substringAfter("\n\n"), color = Soft, fontSize = 14.sp, lineHeight = 21.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.post.breaking) SmallBadge("BREAKING", Color(0xFFFFE8E8), Color(0xFFB42318))
                item.topicIds.firstOrNull()?.let { SmallBadge(it, Color(0xFFE8F5FD), Blue) }
                SmallBadge(if (item.account.id in newMediaAccounts) "单一来源" else confidenceLabel(item.post.confidence), Color(0xFFF1F3F5), Soft)
            }
            Spacer(Modifier.height(9.dp))
            Text("阅读详情  →  ·  ${item.sourceLinks.size} 个来源", color = Blue, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 5.dp))
        }
    }
}

@Composable
private fun RaceRow(entry: FeedEntry, onClick: () -> Unit) {
    val posts = entry.posts
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).background(Color(0xFFD7172A), CircleShape), contentAlignment = Alignment.Center) {
            Text("F1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("F1 赛事动态 · ${relativeTime(posts.maxOf { it.post.publishedAt })}",
                color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(entry.race!!.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${posts.size} 条消息 · ${posts.map { it.account.name }.distinct().joinToString("、")}",
                color = Soft, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            posts.distinctBy { it.account.id }.take(2).forEach { item ->
                Text("${item.account.name} · ${readablePost(item.post.body, item.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT)).body.lineSequence().first()}", color = Soft, fontSize = 13.sp,
                    lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge("赛事合集", Color(0xFFE8F5FD), Blue, onClick)
                val breaking = posts.count { it.post.breaking }
                if (breaking > 0) SmallBadge("$breaking 条 BREAKING", Color(0xFFFFE8E8), Color(0xFFB42318))
            }
            Spacer(Modifier.height(8.dp))
            Text("查看全部详情 →", color = Blue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeedDetailPage(entry: FeedEntry, onBack: () -> Unit, onDocument: (String) -> Unit, listState: LazyListState) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf<List<String>>(emptyList()) }
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(FeedIcons.Back, "返回信息流", tint = Ink) }
            Text(if (entry.race == null) "消息详情" else "赛事详情", color = Ink,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Line)
        LazyColumn(state = listState) {
            entry.race?.let { race ->
                item {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("第 ${race.round} 站 · F1", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(race.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${entry.posts.size} 条消息 · 按发布时间排序", color = Soft, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = Line)
                }
            }
            items(entry.posts, key = { it.post.id }) { post ->
                if (entry.race != null) {
                    val open = post.post.id in expanded
                    Column(Modifier.fillMaxWidth().clickable {
                        expanded = if (open) expanded - post.post.id else expanded + post.post.id
                    }.padding(18.dp)) {
                        Text(post.account.name + " · " + fullDateTime(post.post.publishedAt), color = Soft, fontSize = 12.sp)
                        Text(readablePost(post.post.body, post.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT)).body.substringBefore("\n"), color = Ink, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                        Text(if (open) "收起详情 ↑" else "展开详情 ↓", color = Blue, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (open) DetailPost(post, onDocument)
                } else DetailPost(post, onDocument)
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun DetailPost(item: FeedPost, onDocument: (String) -> Unit) {
    val context = LocalContext.current
    val actions = LocalFeedActions.current
    val interactive = LocalFeatures.current.enabled(Feature.LABEL_ACTIONS)
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clickable(enabled = interactive) { actions.account(item.account.id) }.background(avatarColor(item.account.id), CircleShape), contentAlignment = Alignment.Center) {
                Text(item.account.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(item.account.name, modifier = Modifier.clickable(enabled = interactive) { actions.account(item.account.id) }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${item.account.handle} · ${fullDateTime(item.post.publishedAt)}", color = Soft, fontSize = 12.sp)
            }
            TextButton(onClick = { sharePost(context, item) }) { Text("分享") }
        }
        Spacer(Modifier.height(14.dp))
        val cleanText = LocalFeatures.current.enabled(Feature.CLEAN_TEXT)
        val readable = remember(item.post.body, cleanText) { readablePost(item.post.body, item.account.id, cleanText) }
        readable.metadata?.let { Text(it, color = Soft, fontSize = 12.sp) }
        if (readable.tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            readable.tags.forEach { tag -> SmallBadge("#$tag", Color(0xFFE8F5FD), Blue) {
                openOriginal(context, "https://s.weibo.com/weibo?q=" + Uri.encode("#$tag#"))
            } }
        }
        if (LocalFeatures.current.enabled(Feature.TRANSLATION)) TranslatableBody(readable.body, item.post.id, hasTitle = item.account.id != "wuxing")
        else SelectionContainer { Text(readable.body, color = Ink, fontSize = 16.sp, lineHeight = 27.sp) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.post.breaking) SmallBadge("BREAKING", Color(0xFFFFE8E8), Color(0xFFB42318))
            item.topicIds.firstOrNull()?.let { SmallBadge(it, Color(0xFFE8F5FD), Blue) }
            SmallBadge(if (item.account.id in newMediaAccounts) "单一来源" else confidenceLabel(item.post.confidence), Color(0xFFF1F3F5), Soft)
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(16.dp))
        Text("原始来源", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        item.sourceLinks.forEach { source ->
            val pdf = Uri.parse(source.url).path?.endsWith(".pdf", ignoreCase = true) == true
            Text("${source.name}${if (source.status == "ERROR") " · 抓取异常" else ""}${if (pdf) " · 阅读完整文件 →" else " ↗"}",
                Modifier.fillMaxWidth().clickable(enabled = source.url.startsWith("https://")) {
                    if (pdf) onDocument(source.url) else openOriginal(context, source.url)
                }.padding(vertical = 9.dp), color = Blue, fontSize = 14.sp)
        }
        if (item.sourceLinks.isEmpty() && item.post.originalUrl.startsWith("https://")) {
            Text("打开原文 ↗", Modifier.clickable { openOriginal(context, item.post.originalUrl) }
                .padding(vertical = 9.dp), color = Blue, fontSize = 14.sp)
        }
    }
}

@Composable private fun SmallBadge(text: String, background: Color, foreground: Color, onClick: (() -> Unit)? = null) {
    val actions = LocalFeedActions.current
    val interactive = LocalFeatures.current.enabled(Feature.LABEL_ACTIONS)
    var explain by remember { mutableStateOf(false) }
    Text(text, Modifier.background(background, RoundedCornerShape(4.dp)).clickable(enabled = interactive) {
        when {
            onClick != null -> onClick()
            text in Topics -> actions.topic(text)
            text.contains("BREAKING", ignoreCase = true) -> actions.breaking()
            else -> explain = true
        }
    }.padding(horizontal = 8.dp, vertical = 6.dp), color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text(text) },
        text = { Text(when (text) {
            "单一来源" -> "这是该媒体的独立报道，不代表已由其他媒体核实。原始链接保留在消息详情中。"
            "官方" -> "内容来自对应机构官方来源。可在消息详情查看原始链接。"
            "高可信" -> "来自已识别的公开媒体或已核对原始发言；不代表多家媒体独立证实。"
            "未确认" -> "尚未完成原始来源核验，请以来源说明为准。"
            "演示" -> "这是演示数据，不是真实新闻。"
            else -> "可在消息详情查看来源及完整说明。"
        }) }, confirmButton = { TextButton(onClick = { explain = false }) { Text("知道了") } })
}

@Composable private fun Subheading(label: String) { Text(label, Modifier.padding(18.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
private fun fullDateTime(epoch: Long): String = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(epoch))
private fun confidenceLabel(value: String): String = when (value) {
    "OFFICIAL" -> "官方"; "HIGH" -> "高可信"; "UNCONFIRMED" -> "未确认"; "DEMO" -> "演示"
    else -> "来源待核实"
}
@Composable private fun AccountsPage(accounts: List<AccountEntity>, topics: List<TopicEntity>, model: FeedViewModel, directX: Boolean, onDirectX: (Boolean) -> Unit, features: FeatureSettings, onFeature: (Feature, Boolean) -> Unit) {
    if (features.enabled(Feature.SETTINGS_LAYOUT)) {
        OrganizedSettings(accounts, topics, model, directX, onDirectX, features, onFeature)
        return
    }
    var showMods by remember { mutableStateOf(false) }
    BackHandler(showMods) { showMods = false }
    if (showMods) {
        Column { TextButton(onClick = { showMods = false }) { Text("‹ 返回设置") }; ModManager() }
        return
    }
    Column {
        Text("设置", Modifier.padding(18.dp), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = Line)
        LazyColumn {
            item { TextButton(onClick = { showMods = true }) { Text("Mod 管理：导入 ZIP") } }
            item {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("X 消息直接查看原文", fontWeight = FontWeight.Bold)
                            Text("跳过消息详情，使用旧版信息来源的浏览器打开方式；关闭可回退", fontSize = 12.sp, color = Soft)
                        }
                        Switch(directX, onDirectX)
                    }
                    Text("其他网页使用系统默认浏览器打开。详情可翻译为中文，优先使用云端免费额度；不可用时使用设备端翻译，首次需下载语言包。", fontSize = 12.sp, color = Soft)
                }
            }
            item { Subheading("功能与回退") }
            items(Feature.entries, key = { "feature:" + it.key }) { feature ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onFeature(feature, !features.enabled(feature)) }.padding(end = 12.dp)) {
                        Text(feature.title, fontWeight = FontWeight.SemiBold)
                        Text(feature.description, color = Soft, fontSize = 12.sp)
                    }
                    Switch(features.enabled(feature), { onFeature(feature, it) })
                }
            }
            item { Text("0.4 更新：修正 X 原文打开方式，新增媒体、标签操作与去除废话。各项可分别关闭，缓存保留。", Modifier.padding(18.dp), color = Soft, fontSize = 12.sp) }
            item { Subheading("主题权重") }
            items(topics, key = { "topic-${it.id}" }) { topic ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("${topic.name} · ${"%.2f".format(topic.weight)}", color = Ink, fontSize = 14.sp)
                    Slider(value = topic.weight.toFloat(), onValueChange = { model.topicWeight(topic, it.toDouble()) },
                        valueRange = 0.5f..2f, steps = 5)
                }
                HorizontalDivider(color = Line)
            }
            item { Subheading("账号") }
            items(accounts, key = { it.id }) { account ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(account.name, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                    Text(account.handle, color = Soft, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("关注", color = Soft, fontSize = 13.sp)
                        Switch(account.followed, { model.follow(account, it) }, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("屏蔽", color = Soft, fontSize = 13.sp)
                        Switch(account.muted, { model.mute(account, it) }, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text("权重 ${"%.2f".format(account.weight)}", color = Soft, fontSize = 13.sp)
                    Slider(value = account.weight.toFloat(), onValueChange = { model.weight(account, it.toDouble()) },
                        valueRange = 0.5f..2f, steps = 5)
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

private fun sharePost(context: Context, item: FeedPost) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${item.post.body.substringBefore("\n\n")}\n${item.post.originalUrl}")
    }
    context.startActivity(Intent.createChooser(intent, "分享消息"))
}
private fun avatarColor(id: String): Color = when (id) {
    "f1", "fia" -> Color(0xFFD7172A)
    "openai", "radar" -> Color(0xFF146C57)
    "jiangnan" -> Color(0xFF3975B8)
    else -> Color(0xFF566B78)
}
private fun relativeTime(epoch: Long): String {
    val delta = (System.currentTimeMillis() - epoch).coerceAtLeast(0)
    return when {
        delta < 60_000 -> "刚刚"
        delta < 3_600_000 -> "${delta / 60_000}分"
        delta < 86_400_000 -> "${delta / 3_600_000}时"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(epoch))
    }
}
