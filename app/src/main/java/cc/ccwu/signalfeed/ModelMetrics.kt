package cc.ccwu.signalfeed

import cc.ccwu.signalfeed.data.AiModel

internal fun modelFamily(model: AiModel) = model.creator.orEmpty().lowercase() + ":" + model.name.substringBefore(" (").trim().lowercase()
internal fun representativeModels(models: List<AiModel>): List<AiModel> = models.groupBy(::modelFamily).values
    .map { variants -> variants.maxBy { it.score } }.sortedByDescending { it.score }
internal data class ModelDimension(val id: String, val label: String, val unit: String = "分", val lowerBetter: Boolean = false) {
    fun value(model: AiModel): Double? = (when (id) {
        "score" -> model.score; "speed" -> model.speed; "input" -> model.inputPrice
        "output" -> model.outputPrice; "latency" -> model.latency
        else -> model.evaluations[id]
    })?.takeIf { it.isFinite() }
}
internal val modelDimensions = listOf(
    ModelDimension("score", "综合"),
    ModelDimension("artificial_analysis_coding_index", "编程"),
    ModelDimension("artificial_analysis_agentic_index", "智能体"),
    ModelDimension("artificial_analysis_math_index", "数学"),
    ModelDimension("artificial_analysis_multilingual_index", "多语言"),
    ModelDimension("speed", "速度", "token/s"),
    ModelDimension("latency", "首字延迟", "秒", true),
    ModelDimension("input", "输入价格", "$/百万 token", true),
    ModelDimension("output", "输出价格", "$/百万 token", true)
)
internal fun dimensionRank(value: Double, values: List<Double>, lowerBetter: Boolean): Int = 1 + values.count { if (lowerBetter) it < value else it > value }
internal fun radarScale(value: Double, values: List<Double>, lowerBetter: Boolean): Float {
    val low = values.minOrNull() ?: return 0f; val high = values.maxOrNull() ?: return 0f
    if (high == low) return .5f
    val result = ((value - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    return if (lowerBetter) 1f - result else result
}
