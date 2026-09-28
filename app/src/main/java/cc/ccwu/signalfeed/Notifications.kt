package cc.ccwu.signalfeed

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.ccwu.signalfeed.data.FeedDatabase
import cc.ccwu.signalfeed.data.FeedRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

object Notifications {
    private const val channel = "breaking"
    fun configure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, "Breaking", NotificationManager.IMPORTANCE_HIGH))
        if (BuildConfig.FCM_APP_ID.isNotBlank() && BuildConfig.FCM_API_KEY.isNotBlank() &&
            BuildConfig.FCM_PROJECT_ID.isNotBlank() && BuildConfig.FCM_SENDER_ID.isNotBlank()) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder().setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY).setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID).build()
                FirebaseApp.initializeApp(context, options)
            }
            FirebaseMessaging.getInstance().subscribeToTopic(channel)
        }
        if (!BuildConfig.API_BASE_URL.contains("example.invalid")) {
            val work = PeriodicWorkRequestBuilder<BreakingPollWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("breaking-poll", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }

    suspend fun show(context: Context, postId: String, accountId: String, body: String) {
        val account = FeedDatabase.get(context).feedDao().account(accountId) ?: return
        if (!account.followed || account.muted) return
        val preferences = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
        val seen = preferences.getStringSet("seen", emptySet()).orEmpty()
        if (postId in seen) return
        val intent = Intent(context, MainActivity::class.java).putExtra("postId", postId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, postId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${account.name} · Breaking")
            .setContentText(body.take(180)).setStyle(NotificationCompat.BigTextStyle().bigText(body.take(180)))
            .setAutoCancel(true).setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try {
            NotificationManagerCompat.from(context).notify(postId.hashCode(), notification)
            val next = (seen + postId).toList().takeLast(100).toSet()
            preferences.edit().putStringSet("seen", next).apply()
        }
        catch (_: SecurityException) { }
    }
}

class BreakingMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (FirebaseApp.getApps(this).isNotEmpty()) FirebaseMessaging.getInstance().subscribeToTopic("breaking")
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val postId = message.data["postId"] ?: return
        val accountId = message.data["accountId"] ?: return
        val body = message.data["body"] ?: return
        kotlinx.coroutines.runBlocking { Notifications.show(this@BreakingMessagingService, postId, accountId, body) }
    }
}

class BreakingPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val repository = FeedRepository(applicationContext)
            repository.refresh()
            val dao = FeedDatabase.get(applicationContext).feedDao()
            val preferences = applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
            val initialized = preferences.getBoolean("initialized", false)
            val recent = dao.breakingPosts().filter { it.publishedAt >= System.currentTimeMillis() - 24 * 60 * 60_000 }
            if (!initialized) {
                preferences.edit().putStringSet("seen", recent.map { it.id }.toSet()).putBoolean("initialized", true).apply()
            } else recent.forEach { Notifications.show(applicationContext, it.id, it.accountId, it.body) }
            return Result.success()
        } catch (_: Exception) { return Result.retry() }
    }
}
