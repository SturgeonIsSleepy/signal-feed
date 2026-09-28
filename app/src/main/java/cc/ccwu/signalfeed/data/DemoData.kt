package cc.ccwu.signalfeed.data

object DemoData {
    suspend fun seed(dao: FeedDao) {
        val accounts = listOf(
            AccountEntity("jiangnan", "江南官改", "@jiangnan"),
            AccountEntity("wuxing", "五星体育", "@wuxing_sports"),
            AccountEntity("f1", "Formula 1", "@F1"),
            AccountEntity("fia", "FIA", "@fia"),
            AccountEntity("openai", "ChatGPT 官方", "@OpenAI"),
            AccountEntity("radar", "ChatGPT 雷达", "@radar"),
            AccountEntity("cn", "国内热点", "@china_now"),
            AccountEntity("global", "全球热点", "@world_now")
        )
        val topics = listOf("F1", "AI", "玩机", "国内", "全球")
        val now = System.currentTimeMillis()
        val demo = listOf(
            Triple("f1", "演示数据｜赛前动态：车队准备进入下一站比赛周。接入真实来源后，这里会显示 F1 官方原文摘要。", "F1"),
            Triple("openai", "演示数据｜模型与产品更新会汇集在这里，包含 ChatGPT、Codex 的功能、额度和价格变动。", "AI"),
            Triple("jiangnan", "演示数据｜只显示江南烟雨断桥殇本人明确标注 Redmi K80 至尊版的官改包更新。", "玩机"),
            Triple("fia", "演示数据｜FIA 判罚、规则和正式决定会按发布时间进入信息流。", "F1"),
            Triple("wuxing", "演示数据｜五星体育的 F1 赛事内容会被筛选，其它运动项目不会出现。", "F1"),
            Triple("radar", "演示数据｜Tibo 等人的公开发言会与官方公告交叉核对，并标出可信度。", "AI"),
            Triple("cn", "演示数据｜两家以上媒体报道同一国内事件时，才生成一条聚合帖子。", "国内"),
            Triple("global", "演示数据｜全球热点会合并同题报道，并保留每个媒体的原文入口。", "全球")
        )
        dao.upsertAccounts(accounts)
        dao.upsertTopics(topics.map { TopicEntity(it, it) })
        dao.upsertSources(accounts.map { SourceEntity("demo-${it.id}", it.id, "演示来源", "", "DEMO") })
        dao.upsertPosts(demo.mapIndexed { index, (accountId, body, _) ->
            PostEntity("demo-$index", accountId, body, now - index * 45 * 60_000L,
                now - index * 45 * 60_000L, if (index == 0) 80 else 40,
                "DEMO", index == 0, "")
        })
        dao.upsertPostSources(demo.indices.map { PostSourceEntity("demo-$it", "demo-${demo[it].first}", "") })
        dao.upsertPostTopics(demo.mapIndexed { index, item -> PostTopicEntity("demo-$index", item.third) })
    }
}
