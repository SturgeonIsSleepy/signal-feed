package cc.ccwu.signalfeed

/** Only hide posts whose entire readable content consists of known filler. */
internal object LowInformationFilter {
    private val editorialSeries = Regex("^(?:习言道|学习时节|学习进行时|时政微观察|时政微纪录|每日一习话|跟着总书记|总书记的人民情怀|领航中国|思想的力量|大国外交最前线)")
    private val vague = Regex("温故(?:而)?知新|从温故到知新|谱写.{0,8}新篇章|擘画.{0,8}蓝图|凝心聚力|砥砺奋进|彰显.{0,8}担当|注入.{0,8}动能|携手共创|共绘.{0,8}画卷")
    private val concrete = Regex("\\d+(?:\\.\\d+)?\\s*(?:%|％|亿元|万元|亿美元|人|项|公里|吨|席)|签署|达成协议|正式生效|宣布|发布.{0,12}(?:政策|规定|数据)|取消|批准|发生|死亡|受伤|判处|逮捕")
    fun isEditorialOrTitleOnly(body: String, accountId: String): Boolean {
        if (accountId !in newMediaAccounts && accountId !in setOf("cn", "global")) return false
        val paragraphs = body.trim().split(Regex("\\n\\s*\\n"))
        val title = paragraphs.firstOrNull().orEmpty().trim()
        if (editorialSeries.containsMatchIn(title)) return true
        if (vague.containsMatchIn(title) && !concrete.containsMatchIn(body)) return true
        if (paragraphs.size < 2) return false
        fun normalize(value: String) = value.replace(Regex("^(?:中新网|新华社|中国新闻网|央视网).{0,45}?电\\s*(?:题[：:]\\s*)?"), "")
            .replace(Regex("^(?:题|标题)[：:]\\s*"), "").replace(nonWords, "").lowercase()
        val headline = normalize(title.substringAfterLast('|').substringAfterLast('｜'))
        val summary = normalize(paragraphs.drop(1).joinToString(" "))
        return headline.length >= 10 && summary.isNotEmpty() &&
            (summary == headline || (summary.length >= 10 && headline.contains(summary)))
    }
    private val links = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val markup = Regex("<[^>]+>")
    private val tags = Regex("[#＃][^#＃\\n]+[#＃]|(?<!\\w)[#＃][\\p{L}\\p{N}_]+")
    private val mentions = Regex("(?<!\\w)@[\\p{L}\\p{N}_-]+")
    private val nonWords = Regex("[^\\p{L}\\p{N}]+")
    private val chineseFiller = listOf(
        "欢迎在评论区留言讨论", "欢迎在评论区留言", "欢迎留言讨论", "欢迎转发分享",
        "点赞关注转发", "点赞收藏转发", "点赞评论转发", "点赞关注", "点赞收藏",
        "关注不迷路", "记得关注", "记得点赞", "转发微博", "分享图片", "分享视频",
        "点击查看详情", "点击查看全文", "点击链接查看", "点击下方链接", "详情见链接",
        "戳链接", "网页链接", "视频链接", "查看全文", "展开全文", "全文见评论区",
        "大家怎么看", "你们怎么看", "你怎么看", "你觉得呢", "你期待吗", "谁懂啊",
        "家人们", "小伙伴们", "一起来看看", "让我们拭目以待", "敬请期待", "值得期待",
        "拭目以待", "不容错过", "精彩继续", "持续关注", "更多精彩", "重磅来袭",
        "太精彩了", "太棒了", "太赞了", "绝绝子", "太绝了", "有被惊艳到", "一整个期待住了",
        "早上好", "下午好", "晚上好", "早安", "晚安", "周末愉快", "哈哈哈", "哈哈",
        "转发", "点赞", "收藏", "求关注", "求扩散"
    ).sortedByDescending { it.length }
    private val englishFiller = Regex(
        "\\b(?:like and (?:share|subscribe)|follow for more|stay tuned|coming soon|watch this space|" +
            "check (?:it|this) out|click (?:here|the link)|read more|link in (?:bio|comments)|" +
            "what do you think|thoughts|good morning|good night|happy weekend|" +
            "wow|amazing|awesome|incredible|exciting|lets go|let's go|lol|lmao|retweet|repost|" +
            "please (?:like|share|subscribe)|like|share|subscribe)\\b",
        RegexOption.IGNORE_CASE
    )

    fun shouldHide(body: String, accountId: String, originalUrl: String): Boolean {
        // A linked decision document can contain important information beyond its short caption.
        if (accountId == "fia" && originalUrl.substringBefore('?').endsWith(".pdf", true)) return false
        var text = readablePost(body, accountId).body
            .replace(markup, " ").replace(links, " ").replace(tags, " ").replace(mentions, " ")
        text = text.replace(englishFiller, " ")
        text = text.replace(nonWords, "").lowercase()
        chineseFiller.forEach { text = text.replace(it, "") }
        // Any remaining fact, name, number or unfamiliar phrase is retained.
        return text.isEmpty()
    }
}
