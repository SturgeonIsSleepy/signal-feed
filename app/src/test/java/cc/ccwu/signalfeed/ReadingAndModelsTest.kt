package cc.ccwu.signalfeed

import org.junit.Assert.*
import org.junit.Test
import cc.ccwu.signalfeed.data.AiModel

class ReadingAndModelsTest {
    @Test fun rollbackPreservesOriginalAndOtherFeatures() {
        val original = "五星体育 20:46 来自 微博网页版 #F1# 正文"
        assertEquals(ReadablePost(original, emptyList(), null), readablePost(original, "wuxing", false))
        val settings = FeatureSettings(mapOf(Feature.MODEL_CHARTS.key to false))
        assertFalse(settings.enabled(Feature.MODEL_CHARTS))
        assertTrue(settings.enabled(Feature.SUBSCRIPTIONS))
        assertTrue(settings.enabled(Feature.CLEAN_TEXT))
    }
    @Test fun separatesWeiboMetadataWithoutDeletingBodyPhrases() {
        val result = readablePost("五星体育 2026-09-26 20:46 来自 微博网页版 #F1# 正文来自现场，#赛车# 克拉什托弯。", "wuxing")
        assertEquals("正文来自现场， 克拉什托弯。", result.body)
        assertEquals("kimi上墙……", readablePost("五星体育 2026-09-25 20:20 来自 微博网页版 #F1# kimi上墙…… \u200b 1 0 0", "wuxing").body)
        assertEquals(listOf("F1", "赛车"), result.tags)
        assertTrue(result.metadata!!.contains("微博网页版"))
        val original = "标题\n\n内容来自微博网页版的讨论，保留此句。"
        assertEquals(original, readablePost(original, "wuxing").body)
        assertEquals("#tag# body", readablePost("#tag# body", "f1").body)
    }
    @Test fun xRoutingRequiresExactHost() {
        assertTrue(isXUrl("https://x.com/person/status/123"))
        assertTrue(isXUrl("https://twitter.com/person/status/123"))
        assertFalse(isXUrl("https://x.com.evil.test/person"))
    }
    private fun model(id: String, name: String, score: Double, creator: String = "Maker") = AiModel(id, name, creator, 1, null, score, 1.0, 2.0, 100.0)
    @Test fun reasoningVariantsShareOneEntryButVersionsAndMakersDoNot() {
        val models = listOf(model("a", "Model 1 (high)", 90.0), model("b", "Model 1 (low)", 70.0), model("c", "Model 2 (high)", 80.0), model("d", "Model 1 (high)", 60.0, "Other"))
        assertEquals(listOf("a", "c", "d"), representativeModels(models).map { it.id })
    }
    @Test fun rankingAndRadarRespectTiesCostsAndEqualValues() {
        assertEquals(1, dimensionRank(3.0, listOf(3.0, 3.0, 2.0), false))
        assertEquals(3, dimensionRank(2.0, listOf(3.0, 3.0, 2.0), false))
        assertEquals(1f, radarScale(0.0, listOf(0.0, 2.0), true))
        assertEquals(.5f, radarScale(0.0, listOf(0.0, 0.0), true))
        assertNull(modelDimensions.first { it.id.contains("coding") }.value(model("a", "A", 90.0)))
    }
}
