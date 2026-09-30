package cc.ccwu.signalfeed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun SourceSetupPanel(model: FeedViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            listOf("RSS / OPML", "聚合服务", "数据栏目").forEachIndexed { index, name ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { UiText(name) })
            }
        }
        when (tab) {
            0 -> SubscriptionPanel(model)
            1 -> PackPanel("source") { Notifications.configure(model.getApplication()); model.refresh() }
            else -> PackPanel("data")
        }
    }
}
