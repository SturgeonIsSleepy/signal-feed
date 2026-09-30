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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.ccwu.signalfeed.data.AccountEntity
import cc.ccwu.signalfeed.data.FeedPost
import cc.ccwu.signalfeed.data.TopicEntity
import java.util.Date
import java.util.Locale

private val Ink: Color @Composable get() = LocalShellColors.current.text
private val Soft: Color @Composable get() = LocalShellColors.current.secondary
private val Line: Color @Composable get() = LocalShellColors.current.line
private val Blue: Color @Composable get() = LocalShellColors.current.accent
private val LocalTopics = staticCompositionLocalOf { listOf("全部") }

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
    val shell = remember { ShellPacks.get(context) }
    val configuredFilters by shell.filterPacks.collectAsState()
    val configuredBreaking by shell.breakingPacks.collectAsState()
    val configuredTheme by shell.theme.collectAsState()
    val optionStore = remember { ReaderOptionsStore.get(context) }
    val options by optionStore.options.collectAsState()
    val dark = options.appearance == "dark" || (options.appearance == "system" && isSystemInDarkTheme())
    val themeColors = remember(configuredTheme.toString(), dark) { colors(configuredTheme, dark) }
    val languages = remember(configuredTheme.toString()) { availableLanguages(configuredTheme) }
    val language = languages.firstOrNull { it.code == options.language } ?: Chinese
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
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
    val items = remember(feed, timeline, topic, now, topicRows, featureSettings, accountFilter, onlyBreaking, modStore.packs, configuredFilters, configuredBreaking) {
        val marked = feed.map { item -> item.copy(post = item.post.copy(breaking = ImportedRules.breaking(shell.breakingRules(), item.post.body, item.account.id, item.topicIds, item.post.importance))) }
        visibleFeed(marked, timeline, topic.takeUnless { it == "全部" }, now, topicRows.associate { it.id to it.weight }).filter {
            (featureSettings.enabled(Feature.SUBSCRIPTIONS) || !it.account.id.startsWith("sub:")) &&
            !ImportedRules.hide(configuredFilters, it.post.body, it.account.id, it.topicIds) &&
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
            homeListState.scrollToItem(index + if (model.message.value != null) 1 else 0)
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
    CompositionLocalProvider(LocalFeatures provides featureSettings, LocalFeedActions provides actions, LocalMods provides modStore,
        LocalShellColors provides themeColors, LocalTopics provides (listOf("全部") + topicRows.map { it.id }),
        LocalReaderOptions provides options, LocalUiLanguage provides language) {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(primary = Blue, onPrimary = Color.White, onSurface = Ink, onBackground = Ink,
        background = themeColors.background, surface = themeColors.surface, surfaceContainer = themeColors.surface,
        surfaceContainerHigh = themeColors.muted, surfaceContainerHighest = themeColors.muted,
        onSurfaceVariant = Soft, outlineVariant = Line, outline = Soft)) {
        Column(Modifier.fillMaxSize().background(themeColors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                if (documentUrl != null) PdfDocumentPage(documentUrl!!, onBack = { documentUrl = null },
                    onOriginal = { openOriginal(context, documentUrl!!) })
                else if (detail != null) FeedDetailPage(entries.firstOrNull { it.key == detail!!.key } ?: detail!!,
                    onBack = { detail = null }, onDocument = { documentUrl = it }, listState = detailListState)
                else when (page) {
                    0 -> if (moduleHome != null) androidx.compose.ui.viewinterop.AndroidView(factory = { moduleHome }, modifier = Modifier.fillMaxSize()) else Column {
                        if (accountFilter != null || onlyBreaking) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            UiText(if (onlyBreaking) "仅显示 Breaking" else "账号：" + accounts.firstOrNull { it.id == accountFilter }?.name.orEmpty(), Modifier.weight(1f), color = Blue)
                            TextButton(onClick = { accountFilter = null; onlyBreaking = false }) { UiText("清除筛选") }
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
                        ImportedDataPage(f1, ai, model.f1Message.value, model.aiMessage.value, now,
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
                            Icon(icon, uiText(label), Modifier.size(24.dp), tint = if (page == index) Blue else Soft)
                            Spacer(Modifier.height(3.dp))
                            UiText(label, fontSize = 12.sp, color = if (page == index) Blue else Soft,
                                fontWeight = if (page == index) FontWeight.SemiBold else FontWeight.Normal)
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
    val palette = LocalShellColors.current
    val options = LocalReaderOptions.current
    val density = LocalDensity.current
    val headerHeight = 152.dp
    val heightPx = with(density) { headerHeight.toPx() }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    val scroll = remember(listState, options.hideHeader, heightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (options.hideHeader && (listState.canScrollBackward || listState.canScrollForward)) {
                    headerOffset = (headerOffset + available.y).coerceIn(-heightPx, 0f)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!listState.canScrollBackward) headerOffset = 0f
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(timeline, topic, options.hideHeader) { headerOffset = 0f }
    val feedLayer = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(scroll)) {
        if (entries.isEmpty()) Box(Modifier.fillMaxSize().padding(top = headerHeight), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                UiText(if (syncing) "正在获取消息…" else "还没有消息", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                UiText(if (timeline == Timeline.FOLLOWING) "关注账号后，这里会按时间显示动态" else "在设置中导入信息源订阅文件",
                    color = Soft, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                message?.let { UiText(it, color = Soft, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp)) }
                if (message != null) TextButton(onClick = onRefresh, enabled = !syncing) { UiText("重试") }
                TextButton(onClick = { actions.settings() }) { UiText("添加信息源") }
            }
        } else LazyColumn(state = listState, contentPadding = PaddingValues(top = headerHeight, bottom = 16.dp),
            modifier = Modifier.fillMaxSize().drawWithContent {
                if (options.blur && android.os.Build.VERSION.SDK_INT >= 31) {
                    feedLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(feedLayer)
                } else drawContent()
            }) {
            if (message != null) item(key = "feed:message") {
                Row(Modifier.fillMaxWidth().background(palette.muted).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiText(message, Modifier.weight(1f), color = Soft, fontSize = 13.sp, lineHeight = 19.sp)
                    TextButton(onClick = onRefresh, enabled = !syncing) { UiText("重试") }
                }
            }
            items(entries, key = { it.key }) { entry ->
                if (entry.race != null) RaceRow(entry, onClick = { onOpenDetail(entry) })
                else PostRow(entry.posts.single(), onClick = { onOpenDetail(entry) },
                    onFollow = { onFollow(entry.posts.single()) }, onMute = { onMute(entry.posts.single()) })
                HorizontalDivider(color = Line)
            }
        }
        Box(Modifier.fillMaxWidth().height(headerHeight).offset { IntOffset(0, headerOffset.roundToInt()) }.clipToBounds()) {
            if (options.blur && android.os.Build.VERSION.SDK_INT >= 31 && entries.isNotEmpty()) {
                Canvas(Modifier.matchParentSize().graphicsLayer {
                    renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Clamp)
                }) { translate(top = -headerOffset) { drawLayer(feedLayer) } }
            }
            Column(Modifier.fillMaxSize().background(palette.surface.copy(alpha = if (options.blur && android.os.Build.VERSION.SDK_INT >= 31) .88f else 1f))) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    UiText("S", Modifier.clickable(enabled = interactive) { actions.settings() }.background(Blue, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    UiText("SignalFeed", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                    IconButton(onClick = onRefresh, enabled = !syncing) {
                        if (syncing) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                        else Icon(FeedIcons.Refresh, uiText("刷新信息流"), tint = Ink)
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    listOf(Timeline.FOR_YOU to "为你推荐", Timeline.FOLLOWING to "正在关注").forEach { (tab, label) ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { onTimeline(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            UiText(label, color = if (tab == timeline) Ink else Soft, fontSize = 15.sp,
                                fontWeight = if (tab == timeline) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(11.dp))
                            Box(Modifier.width(62.dp).height(3.dp).background(if (tab == timeline) Blue else Color.Transparent,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                        }
                    }
                }
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalTopics.current.forEach { label ->
                        val selected = label == topic
                        Box(Modifier.background(if (selected) Blue else palette.muted, RoundedCornerShape(18.dp))
                            .clickable { onTopic(label) }.padding(horizontal = 15.dp, vertical = 7.dp)) {
                            UiText(contentLabel(label), color = if (selected) Color.White else Soft, fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).clickable(enabled = interactive) { actions.account(item.account.id) }.background(avatarColor(item.account.id), CircleShape), contentAlignment = Alignment.Center) {
            UiText(item.account.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    UiText(item.account.name, modifier = Modifier.clickable(enabled = interactive) { actions.account(item.account.id) }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    UiText("${item.account.handle} · ${relativeTime(item.post.publishedAt)}", color = Soft, fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { menu = true }) { UiText("⋯", color = Soft, fontSize = 24.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = LocalShellColors.current.surface) {
                        DropdownMenuItem(text = { UiText("阅读详情") }, onClick = { menu = false; onClick() })
                        DropdownMenuItem(text = { UiText(if (item.account.followed) "取消关注" else "关注账号") }, onClick = { menu = false; onFollow() })
                        DropdownMenuItem(text = { UiText("屏蔽此账号") }, onClick = { menu = false; onMute() })
                        DropdownMenuItem(text = { UiText("分享消息") }, onClick = { menu = false; sharePost(context, item) })
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.topicIds.take(3).forEach { topic -> SmallBadge(topic, LocalShellColors.current.accentSurface, Blue) }
                if (item.topicIds.isEmpty()) SmallBadge("未分类", LocalShellColors.current.muted, Soft)
                if (item.post.breaking) SmallBadge("BREAKING", Blue.copy(alpha = .12f), Blue)
            }
            val readable = readablePost(item.post.body, item.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT))
            val content = rememberContentTranslation(readable.body)
            val heading = content.body.substringBefore("\n\n")
            Text(heading, color = Ink, fontSize = 17.sp, lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (heading != content.body) {
                Spacer(Modifier.height(6.dp))
                Text(content.body.substringAfter("\n\n"), color = Ink.copy(alpha = .85f), fontSize = 15.sp, lineHeight = 23.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallBadge(confidenceLabel(item.post.confidence), LocalShellColors.current.muted, Soft)
                if (content.translated) UiText("机器翻译", color = Soft, fontSize = 11.sp)
                else if (content.busy) UiText("正在翻译…", color = Soft, fontSize = 11.sp)
            }
            Spacer(Modifier.height(5.dp))
            UiText("阅读详情  →  ·  ${item.sourceLinks.size} 个来源", color = Blue, fontSize = 12.sp,
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
            UiText("F1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            UiText("F1 赛事动态 · ${relativeTime(posts.maxOf { it.post.publishedAt })}",
                color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(rememberContentTranslation(entry.race!!.name).body, color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
            UiText("${posts.size} 条消息 · ${posts.map { it.account.name }.distinct().joinToString("、")}",
                color = Soft, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            posts.distinctBy { it.account.id }.take(2).forEach { item ->
                val preview = rememberContentTranslation(readablePost(item.post.body, item.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT)).body).body.lineSequence().first()
                Text("${item.account.name} · $preview", color = Soft, fontSize = 14.sp,
                    lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge("赛事合集", LocalShellColors.current.accentSurface, Blue, onClick)
                val breaking = posts.count { it.post.breaking }
                if (breaking > 0) SmallBadge("$breaking 条 BREAKING", LocalShellColors.current.accentSurface, LocalShellColors.current.accent)
            }
            Spacer(Modifier.height(8.dp))
            UiText("查看全部详情 →", color = Blue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeedDetailPage(entry: FeedEntry, onBack: () -> Unit, onDocument: (String) -> Unit, listState: LazyListState) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf<List<String>>(emptyList()) }
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(FeedIcons.Back, uiText("返回信息流"), tint = Ink) }
            UiText(if (entry.race == null) "消息详情" else "赛事详情", color = Ink,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Line)
        LazyColumn(state = listState) {
            entry.race?.let { race ->
                item {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        UiText("第 ${race.round} 站 · F1", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(rememberContentTranslation(race.name).body, color = Ink, fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                        UiText("${entry.posts.size} 条消息 · 按发布时间排序", color = Soft, fontSize = 13.sp)
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
                        UiText(post.account.name + " · " + fullDateTime(post.post.publishedAt), color = Soft, fontSize = 12.sp)
                        Text(rememberContentTranslation(readablePost(post.post.body, post.account.id, LocalFeatures.current.enabled(Feature.CLEAN_TEXT)).body).body.substringBefore("\n"), color = Ink, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                        UiText(if (open) "收起详情 ↑" else "展开详情 ↓", color = Blue, fontSize = 13.sp,
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
                UiText(item.account.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                UiText(item.account.name, modifier = Modifier.clickable(enabled = interactive) { actions.account(item.account.id) }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                UiText("${item.account.handle} · ${fullDateTime(item.post.publishedAt)}", color = Soft, fontSize = 12.sp)
            }
            TextButton(onClick = { sharePost(context, item) }) { UiText("分享") }
        }
        Spacer(Modifier.height(14.dp))
        val cleanText = LocalFeatures.current.enabled(Feature.CLEAN_TEXT)
        val readable = remember(item.post.body, cleanText) { readablePost(item.post.body, item.account.id, cleanText) }
        readable.metadata?.let { UiText(it, color = Soft, fontSize = 12.sp) }
        if (readable.tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            readable.tags.forEach { tag -> SmallBadge("#$tag", LocalShellColors.current.accentSurface, Blue) {
                openOriginal(context, "https://s.weibo.com/weibo?q=" + Uri.encode("#$tag#"))
            } }
        }
        if (LocalFeatures.current.enabled(Feature.TRANSLATION)) TranslatableBody(readable.body, item.post.id, hasTitle = item.account.id != "wuxing")
        else SelectionContainer { Text(readable.body, color = Ink, fontSize = 16.sp, lineHeight = 27.sp) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.post.breaking) SmallBadge("BREAKING", LocalShellColors.current.accentSurface, LocalShellColors.current.accent)
            item.topicIds.firstOrNull()?.let { SmallBadge(it, LocalShellColors.current.accentSurface, Blue) }
            SmallBadge(confidenceLabel(item.post.confidence), LocalShellColors.current.muted, Soft)
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(16.dp))
        UiText("原始来源", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        item.sourceLinks.forEach { source ->
            val pdf = Uri.parse(source.url).path?.endsWith(".pdf", ignoreCase = true) == true
            UiText("${source.name}${if (source.status == "ERROR") " · 抓取异常" else ""}${if (pdf) " · 阅读完整文件 →" else " ↗"}",
                Modifier.fillMaxWidth().clickable(enabled = source.url.startsWith("https://")) {
                    if (pdf) onDocument(source.url) else openOriginal(context, source.url)
                }.padding(vertical = 9.dp), color = Blue, fontSize = 14.sp)
        }
        if (item.sourceLinks.isEmpty() && item.post.originalUrl.startsWith("https://")) {
            UiText("打开原文 ↗", Modifier.clickable { openOriginal(context, item.post.originalUrl) }
                .padding(vertical = 9.dp), color = Blue, fontSize = 14.sp)
        }
    }
}

@Composable private fun SmallBadge(text: String, background: Color, foreground: Color, onClick: (() -> Unit)? = null) {
    val actions = LocalFeedActions.current
    val topics = LocalTopics.current
    val interactive = LocalFeatures.current.enabled(Feature.LABEL_ACTIONS)
    var explain by remember { mutableStateOf(false) }
    UiText(if (text in topics || text.startsWith("#")) contentLabel(text) else text, Modifier.background(background, RoundedCornerShape(6.dp)).clickable(enabled = interactive) {
        when {
            onClick != null -> onClick()
            text in topics -> actions.topic(text)
            text.contains("BREAKING", ignoreCase = true) -> actions.breaking()
            else -> explain = true
        }
    }.padding(horizontal = 8.dp, vertical = 6.dp), color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { UiText(text) },
        text = { UiText(when (text) {
            "单一来源" -> "这是该媒体的独立报道，不代表已由其他媒体核实。原始链接保留在消息详情中。"
            "官方" -> "内容来自对应机构官方来源。可在消息详情查看原始链接。"
            "高可信" -> "来自已识别的公开媒体或已核对原始发言；不代表多家媒体独立证实。"
            "未确认" -> "尚未完成原始来源核验，请以来源说明为准。"
            "演示" -> "这是演示数据，不是真实新闻。"
            else -> "可在消息详情查看来源及完整说明。"
        }) }, confirmButton = { TextButton(onClick = { explain = false }) { UiText("知道了") } })
}

@Composable private fun Subheading(label: String) { UiText(label, Modifier.padding(18.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
@Composable internal fun fullDateTime(epoch: Long): String = java.text.DateFormat.getDateTimeInstance(
    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Locale.forLanguageTag(LocalUiLanguage.current.code)).format(Date(epoch))
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
    var showOptions by remember { mutableStateOf(false) }
    BackHandler(showMods || showOptions) { showMods = false; showOptions = false }
    if (showOptions) {
        Column { TextButton(onClick = { showOptions = false }) { UiText("‹ 返回设置") }; ReaderOptionsPanel() }
        return
    }
    if (showMods) {
        Column { TextButton(onClick = { showMods = false }) { UiText("‹ 返回设置") }; ModManager() }
        return
    }
    Column {
        UiText("设置", Modifier.padding(18.dp), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = Line)
        LazyColumn {
            item { ImportHub(model) }
            item { TextButton(onClick = { showOptions = true }) { UiText("语言与外观") } }
            item { TextButton(onClick = { showMods = true }) { UiText("Mod 管理：导入 ZIP") } }
            item {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            UiText("X 消息直接查看原文", fontWeight = FontWeight.Bold)
                            UiText("跳过消息详情，使用旧版信息来源的浏览器打开方式；关闭可回退", fontSize = 12.sp, color = Soft)
                        }
                        Switch(directX, onDirectX)
                    }
                    UiText("翻译器已内置，请在语言与外观中设置；无需导入翻译器文件", fontSize = 12.sp, color = Soft)
                }
            }
            item { Subheading("功能与回退") }
            items(Feature.entries, key = { "feature:" + it.key }) { feature ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onFeature(feature, !features.enabled(feature)) }.padding(end = 12.dp)) {
                        UiText(feature.title, fontWeight = FontWeight.SemiBold)
                        UiText(feature.description, color = Soft, fontSize = 12.sp)
                    }
                    Switch(features.enabled(feature), { onFeature(feature, it) })
                }
            }
            item { UiText("此页用于回退旧版交互。导入内容仍可从上方入口管理。", Modifier.padding(18.dp), color = Soft, fontSize = 12.sp) }
            item { Subheading("主题权重") }
            items(topics, key = { "topic-${it.id}" }) { topic ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    UiText("${topic.name} · ${"%.2f".format(topic.weight)}", color = Ink, fontSize = 14.sp)
                    Slider(value = topic.weight.toFloat(), onValueChange = { model.topicWeight(topic, it.toDouble()) },
                        valueRange = 0.5f..2f, steps = 5)
                }
                HorizontalDivider(color = Line)
            }
            item { Subheading("账号") }
            items(accounts, key = { it.id }) { account ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    UiText(account.name, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                    UiText(account.handle, color = Soft, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        UiText("关注", color = Soft, fontSize = 13.sp)
                        Switch(account.followed, { model.follow(account, it) }, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.width(12.dp))
                        UiText("屏蔽", color = Soft, fontSize = 13.sp)
                        Switch(account.muted, { model.mute(account, it) }, modifier = Modifier.padding(start = 8.dp))
                    }
                    UiText("权重 ${"%.2f".format(account.weight)}", color = Soft, fontSize = 13.sp)
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
@Composable private fun relativeTime(epoch: Long): String {
    val delta = (System.currentTimeMillis() - epoch).coerceAtLeast(0)
    val label = when {
        delta < 60_000 -> "刚刚"
        delta < 3_600_000 -> "${delta / 60_000}分"
        delta < 86_400_000 -> "${delta / 3_600_000}时"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.forLanguageTag(LocalUiLanguage.current.code)).format(Date(epoch))
    }
    return uiText(label)
}
