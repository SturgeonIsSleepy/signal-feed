package cc.ccwu.signalfeed

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ccwu.signalfeed.data.AccountEntity
import cc.ccwu.signalfeed.data.FeedRepository
import cc.ccwu.signalfeed.data.TopicEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: FeedViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        RuntimeModules.attach(this, intent.getBooleanExtra("safeMode", false))
        model = ViewModelProvider(this)[FeedViewModel::class.java]
        model.targetPostId.value = intent.getStringExtra("postId")
        Notifications.configure(this)
        if (Build.VERSION.SDK_INT >= 33 && ShellPacks.get(this).breakingRules().isNotEmpty() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { SignalFeedApp(model) }
    }
    override fun onDestroy() { RuntimeModules.detach(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.targetPostId.value = intent.getStringExtra("postId")
    }
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FeedRepository(application)
    val feed = repository.feed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topics = repository.topics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val f1 = repository.f1.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ai = repository.ai.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var syncing = androidx.compose.runtime.mutableStateOf(false)
        private set
    var message = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var f1Message = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var aiMessage = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    val targetPostId = androidx.compose.runtime.mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            repository.initialize()
            refresh()

        }
    }

    fun refresh() = viewModelScope.launch {
        if (syncing.value) return@launch
        syncing.value = true
        try {
            repository.refresh()
            message.value = null
        } catch (_: Exception) {
            message.value = "无法连接信息源。请检查手机网络或代理；有缓存时会继续显示。"
        } finally {
            syncing.value = false
        }
    }

    fun follow(account: AccountEntity, value: Boolean) = viewModelScope.launch { repository.setFollowed(account, value) }
    fun mute(account: AccountEntity, value: Boolean) = viewModelScope.launch { repository.setMuted(account, value) }
    fun weight(account: AccountEntity, value: Double) = viewModelScope.launch { repository.setWeight(account, value) }
    fun topicWeight(topic: TopicEntity, value: Double) = viewModelScope.launch { repository.setTopicWeight(topic, value) }
    fun loadF1() = viewModelScope.launch {
        runCatching { repository.refreshF1() }
            .onSuccess { f1Message.value = null }
            .onFailure { f1Message.value = "F1 更新失败。请检查手机网络或代理。" }
    }
    fun loadAi() = viewModelScope.launch {
        runCatching { repository.refreshAi() }
            .onSuccess { aiMessage.value = null }
            .onFailure { aiMessage.value = "AI 更新失败。请检查手机网络或代理。" }
    }
}
