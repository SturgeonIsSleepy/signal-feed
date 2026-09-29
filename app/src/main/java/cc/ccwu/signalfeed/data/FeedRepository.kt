package cc.ccwu.signalfeed.data

import android.content.Context
import cc.ccwu.signalfeed.ShellPacks
import cc.ccwu.signalfeed.Subscriptions
import cc.ccwu.signalfeed.RuntimeModules
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.squareup.moshi.Moshi

data class FeedPost(
    val post: PostEntity,
    val account: AccountEntity,
    val sourceLinks: List<PostSourceLink>,
    val topicIds: Set<String>
)
data class PostSourceLink(val name: String, val url: String, val status: String)

@JsonClass(generateAdapter = false)
data class SyncResponse(val posts: List<RemotePost>, val accounts: List<RemoteAccount>, val sources: List<RemoteSource>, val nextCursor: String)
@JsonClass(generateAdapter = false)
data class RemoteAccount(val id: String, val name: String, val handle: String)
@JsonClass(generateAdapter = false)
data class RemoteSource(val id: String, val accountId: String, val name: String, val homeUrl: String, val status: String)
@JsonClass(generateAdapter = false)
data class RemotePost(
    val id: String, val accountId: String, val body: String, val publishedAt: Long,
    val updatedAt: Long, val importance: Int, val confidence: String,
    val breaking: Boolean, val originalUrl: String,
    val topicIds: List<String>, val sources: List<RemotePostSource>
)
@JsonClass(generateAdapter = false)
data class RemotePostSource(val sourceId: String, val originalUrl: String)

interface FeedApi {
    @GET("v1/sync") suspend fun sync(@Query("cursor") cursor: String?): SyncResponse
    @GET("v1/f1") suspend fun f1(): F1Snapshot
    @GET("v1/ai") suspend fun ai(): AiSnapshot
}

data class F1Race(val round: Int, val name: String, val circuit: String?, val locality: String?, val startAt: Long, val sessions: List<F1Session>)
data class F1Session(val name: String, val startAt: Long)
data class F1Standing(val position: Int, val name: String, val team: String?, val points: Double)
data class F1Constructor(val position: Int, val name: String, val points: Double)
data class F1Finisher(val position: Int, val name: String, val team: String?)
data class F1Result(val round: Int, val name: String, val finishers: List<F1Finisher>)
data class F1HistoryDriver(val id: String, val name: String, val points: Double)
data class F1HistoryRound(val round: Int, val name: String, val drivers: List<F1HistoryDriver>)
data class F1Snapshot(val year: Int, val races: List<F1Race>, val next: F1Race?, val drivers: List<F1Standing>,
    val constructors: List<F1Constructor>, val lastResult: F1Result?, val attribution: String, val updatedAt: Long,
    val driverHistory: List<F1HistoryRound> = emptyList(),
    val constructorHistory: List<F1HistoryRound> = emptyList())
data class AiModel(val id: String, val name: String, val creator: String?, val rank: Int, val change: Int?,
    val score: Double, val inputPrice: Double?, val outputPrice: Double?, val speed: Double?, val evaluations: Map<String, Double> = emptyMap(), val latency: Double? = null)
data class AiSnapshot(val models: List<AiModel>, val indexVersion: Double?, val attribution: String, val updatedAt: Long)

class FeedRepository(context: Context) {
    private val subscriptions = Subscriptions.get(context)
    private val shell = ShellPacks.get(context)
    private val cursors = context.getSharedPreferences("source-cursors", Context.MODE_PRIVATE)
    private val dao = FeedDatabase.get(context).feedDao()
    private val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
    private fun api(address: String): FeedApi =
        Retrofit.Builder().baseUrl(address)
            .client(OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(RuntimeModules.url(chain.request().url.toString())).build()) }.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(FeedApi::class.java)

    val accounts: Flow<List<AccountEntity>> = dao.accounts()
    val topics: Flow<List<TopicEntity>> = dao.topics()
    val f1: Flow<F1Snapshot?> = dao.snapshot("f1").map {
        it?.let { row -> runCatching { moshi.adapter(F1Snapshot::class.java).fromJson(row.payload) }.getOrNull() }
    }
    val ai: Flow<AiSnapshot?> = dao.snapshot("ai").map {
        it?.let { row -> runCatching { moshi.adapter(AiSnapshot::class.java).fromJson(row.payload) }.getOrNull() }
    }
    private val rawFeed: Flow<List<FeedPost>> = combine(
        dao.posts(), dao.accounts(), dao.sources(), dao.postSources(), dao.postTopics()
    ) { posts, accounts, sources, links, topics ->
        val accountById = accounts.associateBy { it.id }
        val sourceById = sources.associateBy { it.id }
        val linksByPost = links.groupBy { it.postId }
        val topicsByPost = topics.groupBy { it.postId }
        posts.mapNotNull { post ->
            val account = accountById[post.accountId] ?: return@mapNotNull null
            FeedPost(post, account,
                linksByPost[post.id].orEmpty().mapNotNull { link ->
                    sourceById[link.sourceId]?.let { PostSourceLink(it.name, link.originalUrl, it.status) }
                }.distinct(),
                topicsByPost[post.id].orEmpty().map { it.topicId }.toSet())
        }
    }

    private val feedAdapter = moshi.adapter<List<FeedPost>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, FeedPost::class.java))
    val feed: Flow<List<FeedPost>> = combine(rawFeed, subscriptions.items, shell.sourcePacks) { rows, sources, packs ->
        val enabled = sources.filter { it.enabled }.map { it.id }.toSet()
        val selected = packs.flatMap { pack -> pack.optJSONArray("accounts")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty() }.toSet()
        val visible = rows.filter { if (it.account.id.startsWith("sub:")) it.account.id in enabled else it.account.id in selected }
        runCatching { feedAdapter.fromJson(RuntimeModules.transform(feedAdapter.toJson(visible))) ?: visible }.getOrDefault(visible)
    }

    suspend fun initialize() {
        dao.deleteDemoSources(); dao.deleteDemoSourceRows(); dao.deleteDemoTopics(); dao.deleteDemoPosts()
    }

    suspend fun refresh(): Boolean {
        subscriptions.refresh()
        val grouped = shell.sourcePacks.value.groupBy { it.getString("apiBaseUrl") }
        var changed = false
        var failed = false
        grouped.forEach { (address, packs) ->
            val selected = packs.flatMap { pack -> val array = pack.getJSONArray("accounts"); (0 until array.length()).map { array.getString(it) } }.toSet()
            try { changed = syncSource(address, selected) || changed } catch (_: Exception) { failed = true }
        }
        if (failed) error("部分聚合服务更新失败")
        return changed
    }

    private suspend fun syncSource(address: String, selected: Set<String>): Boolean {
        val remote = api(address)
        var cursor = cursors.getString(address, "").orEmpty()
        var changed = false
        repeat(10) {
            val page = remote.sync(cursor.ifEmpty { null })
            val posts = page.posts.filter { it.accountId in selected }
            posts.forEach { post ->
                require(dao.post(post.id)?.accountId?.let { it == post.accountId } ?: true) { "聚合服务帖子标识冲突" }
            }
            val known = knownAccountIds()
            dao.upsertAccounts(page.accounts.filter { it.id in selected }.map { fresh ->
                // Existing local preferences are preserved by the update query below; never replace them on sync.
                AccountEntity(fresh.id, fresh.name, fresh.handle)
            }.filter { item -> item.id !in known })
            dao.upsertSources(page.sources.filter { it.accountId in selected }.map { SourceEntity(it.id, it.accountId, it.name, it.homeUrl, it.status) })
            dao.insertTopics(posts.flatMap { it.topicIds }.distinct().map { TopicEntity(it, it) })
            dao.upsertPosts(posts.map { PostEntity(it.id, it.accountId, it.body, it.publishedAt,
                it.updatedAt, it.importance, it.confidence, it.breaking, it.originalUrl) })
            dao.upsertPostSources(posts.flatMap { post -> post.sources.map {
                PostSourceEntity(post.id, it.sourceId, it.originalUrl)
            } })
            dao.upsertPostTopics(posts.flatMap { post -> post.topicIds.map { PostTopicEntity(post.id, it) } })
            changed = changed || posts.isNotEmpty()
            val previous = cursor
            if (page.nextCursor != cursor) {
                cursor = page.nextCursor
                cursors.edit().putString(address, cursor).apply()
            }
            if (page.nextCursor == previous || page.posts.isEmpty()) return changed
        }
        return changed
    }

    suspend fun refreshF1() {
        val addresses = shell.sourcePacks.value.map { it.getString("apiBaseUrl") }.distinct()
        if (addresses.isEmpty()) return
        var value: F1Snapshot? = null
        for (address in addresses) {
            value = runCatching { api(address).f1() }.getOrNull()
            if (value != null) break
        }
        checkNotNull(value) { "没有可用的 F1 数据服务" }
        dao.upsertSnapshot(SnapshotEntity("f1", moshi.adapter(F1Snapshot::class.java).toJson(value), value.updatedAt))
    }
    suspend fun refreshAi() {
        val addresses = shell.sourcePacks.value.map { it.getString("apiBaseUrl") }.distinct()
        if (addresses.isEmpty()) return
        var value: AiSnapshot? = null
        for (address in addresses) {
            value = runCatching { api(address).ai() }.getOrNull()
            if (value != null) break
        }
        checkNotNull(value) { "没有可用的 AI 数据服务" }
        dao.upsertSnapshot(SnapshotEntity("ai", moshi.adapter(AiSnapshot::class.java).toJson(value), value.updatedAt))
    }

    private suspend fun knownAccountIds(): Set<String> = dao.accounts().first().map { it.id }.toSet()
    suspend fun setFollowed(account: AccountEntity, followed: Boolean) = dao.setFollowed(account.id, followed)
    suspend fun setMuted(account: AccountEntity, muted: Boolean) = dao.setMuted(account.id, muted)
    suspend fun setWeight(account: AccountEntity, weight: Double) = dao.setWeight(account.id, weight.coerceIn(0.5, 2.0))
    suspend fun setTopicWeight(topic: TopicEntity, weight: Double) = dao.setTopicWeight(topic.id, weight.coerceIn(0.5, 2.0))
}
