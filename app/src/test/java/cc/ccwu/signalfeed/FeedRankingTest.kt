package cc.ccwu.signalfeed

import cc.ccwu.signalfeed.data.AccountEntity
import cc.ccwu.signalfeed.data.FeedPost
import cc.ccwu.signalfeed.data.F1Race
import cc.ccwu.signalfeed.data.PostEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedRankingTest {
    private val now = 1_000_000_000L
    private fun post(id: String, hoursOld: Long, importance: Int, account: AccountEntity, topic: String = "F1", body: String = id) =
        FeedPost(PostEntity(id, account.id, body, now - hoursOld * 3_600_000L, now, importance,
            "OFFICIAL", false, "https://example.org/$id"), account, emptyList(), setOf(topic))

    @Test fun followingIgnoresImportanceAndOnlyShowsFollowedAccounts() {
        val followed = AccountEntity("a", "A", "@a")
        val unfollowed = AccountEntity("b", "B", "@b", followed = false)
        val rows = listOf(post("old", 2, 100, followed), post("new", 1, 10, followed),
            post("excluded", 0, 100, unfollowed))
        assertEquals(listOf("new", "old"), visibleFeed(rows, Timeline.FOLLOWING, null, now).map { it.post.id })
    }

    @Test fun forYouUsesWeightAndHidesMutedAccounts() {
        val favored = AccountEntity("a", "A", "@a", weight = 2.0)
        val normal = AccountEntity("b", "B", "@b")
        val muted = AccountEntity("c", "C", "@c", muted = true)
        val rows = listOf(post("favored", 1, 50, favored), post("normal", 1, 70, normal),
            post("muted", 0, 100, muted), post("ai", 0, 100, normal, "AI"))
        assertEquals(listOf("favored", "normal"), visibleFeed(rows, Timeline.FOR_YOU, "F1", now).map { it.post.id })
    }

    @Test fun forYouUsesTopicPreferenceWithoutChangingFollowingTimeOrder() {
        val account = AccountEntity("a", "A", "@a")
        val rows = listOf(post("f1", 2, 50, account), post("ai", 1, 50, account, "AI"))
        val weights = mapOf("F1" to 2.0, "AI" to 1.0)
        assertEquals(listOf("f1", "ai"), visibleFeed(rows, Timeline.FOR_YOU, null, now, weights).map { it.post.id })
        assertEquals(listOf("ai", "f1"), visibleFeed(rows, Timeline.FOLLOWING, null, now, weights).map { it.post.id })
    }

    @Test fun raceStoriesCollapseButUnrelatedF1NewsKeepsItsOwnCard() {
        val fia = AccountEntity("fia", "FIA", "@fia")
        val f1 = AccountEntity("f1", "F1", "@f1")
        val race = F1Race(15, "Azerbaijan Grand Prix", null, "Baku", now, emptyList())
        val rows = listOf(
            post("fia-decision", 1, 90, fia, body = "Doc 42 - Decision"),
            post("other-news", 2, 60, f1, body = "2027 team lineup"),
            post("baku-report", 3, 70, f1, body = "Qualifying in Baku")
        )
        val ordered = visibleFeed(rows, Timeline.FOLLOWING, null, now)
        val entries = collapseRacePosts(ordered, listOf(race))
        assertEquals(listOf("race-15", "other-news"), entries.map { it.key })
        assertEquals(listOf("fia-decision", "baku-report"), entries[0].posts.map { it.post.id })
    }
}
