package cc.ccwu.signalfeed.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String,
    val followed: Boolean = true,
    val muted: Boolean = false,
    val weight: Double = 1.0
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val homeUrl: String,
    val status: String = "OK"
)

@Entity(tableName = "topics")
data class TopicEntity(@PrimaryKey val id: String, val name: String, val weight: Double = 1.0)

@Entity(tableName = "posts", indices = [Index(value = ["publishedAt"]), Index(value = ["updatedAt"])])
data class PostEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val body: String,
    val publishedAt: Long,
    val updatedAt: Long,
    val importance: Int,
    val confidence: String,
    val breaking: Boolean,
    val originalUrl: String
)

@Entity(tableName = "post_sources", primaryKeys = ["postId", "sourceId", "originalUrl"])
data class PostSourceEntity(val postId: String, val sourceId: String, val originalUrl: String)

@Entity(tableName = "post_topics", primaryKeys = ["postId", "topicId"])
data class PostTopicEntity(val postId: String, val topicId: String)

@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val id: String = "feed", val cursor: String = "")

@Entity(tableName = "snapshots")
data class SnapshotEntity(@PrimaryKey val id: String, val payload: String, val updatedAt: Long)

@Dao
interface FeedDao {
    @Query("SELECT * FROM accounts ORDER BY name") fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM topics ORDER BY name") fun topics(): Flow<List<TopicEntity>>
    @Query("SELECT * FROM posts ORDER BY publishedAt DESC, id DESC") fun posts(): Flow<List<PostEntity>>
    @Query("SELECT * FROM sources") fun sources(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM post_sources") fun postSources(): Flow<List<PostSourceEntity>>
    @Query("SELECT * FROM post_topics") fun postTopics(): Flow<List<PostTopicEntity>>
    @Query("SELECT COUNT(*) FROM posts") suspend fun postCount(): Int
    @Query("SELECT cursor FROM sync_state WHERE id = 'feed'") suspend fun cursor(): String?
    @Query("SELECT * FROM snapshots WHERE id = :id") fun snapshot(id: String): Flow<SnapshotEntity?>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): AccountEntity?
    @Query("SELECT * FROM posts WHERE breaking = 1 ORDER BY publishedAt DESC LIMIT 20") suspend fun breakingPosts(): List<PostEntity>
    @Query("SELECT * FROM posts WHERE publishedAt > :since ORDER BY publishedAt DESC LIMIT 100") suspend fun recentPosts(since: Long): List<PostEntity>
    @Query("SELECT * FROM posts WHERE id = :id") suspend fun post(id: String): PostEntity?
    @Query("SELECT topicId FROM post_topics WHERE postId = :postId") suspend fun postTopicIds(postId: String): List<String>
    @Query("DELETE FROM post_sources WHERE postId LIKE 'demo-%'") suspend fun deleteDemoSources()
    @Query("DELETE FROM sources WHERE id LIKE 'demo-%'") suspend fun deleteDemoSourceRows()
    @Query("DELETE FROM post_topics WHERE postId LIKE 'demo-%'") suspend fun deleteDemoTopics()
    @Query("DELETE FROM posts WHERE id LIKE 'demo-%'") suspend fun deleteDemoPosts()
    @Upsert suspend fun upsertAccounts(items: List<AccountEntity>)
    @Upsert suspend fun upsertSources(items: List<SourceEntity>)
    @Upsert suspend fun upsertTopics(items: List<TopicEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTopics(items: List<TopicEntity>)
    @Upsert suspend fun upsertPosts(items: List<PostEntity>)
    @Upsert suspend fun upsertPostSources(items: List<PostSourceEntity>)
    @Upsert suspend fun upsertPostTopics(items: List<PostTopicEntity>)
    @Upsert suspend fun upsertCursor(item: SyncStateEntity)
    @Upsert suspend fun upsertSnapshot(item: SnapshotEntity)
    @Query("UPDATE accounts SET followed = :followed WHERE id = :id") suspend fun setFollowed(id: String, followed: Boolean)
    @Query("UPDATE accounts SET muted = :muted WHERE id = :id") suspend fun setMuted(id: String, muted: Boolean)
    @Query("UPDATE accounts SET weight = :weight WHERE id = :id") suspend fun setWeight(id: String, weight: Double)
    @Query("UPDATE topics SET weight = :weight WHERE id = :id") suspend fun setTopicWeight(id: String, weight: Double)
}

@Database(
    entities = [AccountEntity::class, SourceEntity::class, TopicEntity::class, PostEntity::class,
        PostSourceEntity::class, PostTopicEntity::class, SyncStateEntity::class, SnapshotEntity::class],
    version = 3,
    exportSchema = false
)
abstract class FeedDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    companion object {
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE topics ADD COLUMN weight REAL NOT NULL DEFAULT 1.0")
            }
        }
        @Volatile private var instance: FeedDatabase? = null
        fun get(context: Context): FeedDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FeedDatabase::class.java, "signal-feed.db")
                .addMigrations(migration2To3)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
