package cc.ccwu.signalfeed

import cc.ccwu.signalfeed.data.FeedPost
import cc.ccwu.signalfeed.data.F1Race
import kotlin.math.exp
import kotlin.math.abs

enum class Timeline { FOR_YOU, FOLLOWING }

data class FeedEntry(val key: String, val posts: List<FeedPost>, val race: F1Race? = null)

fun collapseRacePosts(items: List<FeedPost>, races: List<F1Race>): List<FeedEntry> {
    val grouped = mutableMapOf<Int, MutableList<FeedPost>>()
    val raceByPost = items.associate { item ->
        val race = if ("F1" in item.topicIds) races.filter { race ->
            item.post.publishedAt in (race.startAt - 4 * 86_400_000L)..(race.startAt + 2 * 86_400_000L)
        }.minByOrNull { abs(item.post.publishedAt - it.startAt) } else null
        val name = race?.name?.substringBefore(" Grand Prix")?.lowercase().orEmpty()
        val locality = race?.locality?.lowercase().orEmpty()
        val headline = item.post.body.lineSequence().first().lowercase()
        val related = item.account.id == "fia" || (name.length >= 4 && headline.contains(name)) ||
            (locality.length >= 4 && headline.contains(locality))
        item.post.id to race?.takeIf { related }
    }
    items.forEach { item -> raceByPost[item.post.id]?.let { grouped.getOrPut(it.round) { mutableListOf() }.add(item) } }
    val emitted = mutableSetOf<Int>()
    return items.mapNotNull { item ->
        val race = raceByPost[item.post.id]
        if (race == null) FeedEntry(item.post.id, listOf(item))
        else if (emitted.add(race.round)) FeedEntry("race-${race.round}", grouped.getValue(race.round)
            .sortedByDescending { it.post.publishedAt }, race)
        else null
    }
}

fun visibleFeed(
    items: List<FeedPost>, timeline: Timeline, topic: String?, nowMillis: Long,
    topicWeights: Map<String, Double> = emptyMap()
): List<FeedPost> {
    val visible = items.filter { item ->
        !item.account.muted && (topic == null || topic in item.topicIds) &&
            (timeline != Timeline.FOLLOWING || item.account.followed)
    }
    return when (timeline) {
        Timeline.FOLLOWING -> visible.sortedWith(compareByDescending<FeedPost> { it.post.publishedAt }.thenByDescending { it.post.id })
        Timeline.FOR_YOU -> visible.sortedWith(compareByDescending<FeedPost> { item ->
            val hours = ((nowMillis - item.post.publishedAt).coerceAtLeast(0) / 3_600_000.0)
            val topicBoost = if (topic != null) 10 else 0
            val topicWeight = item.topicIds.maxOfOrNull { topicWeights[it] ?: 1.0 } ?: 1.0
            (item.post.importance + topicBoost) * item.account.weight * topicWeight * exp(-hours / 24.0)
        }.thenByDescending { it.post.publishedAt }.thenByDescending { it.post.id })
    }
}
