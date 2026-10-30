package com.workhub.app.training

data class BallMove(
    val key: String,
    val category: String,
    val label: String,
    val steps: String,
    val progression: String,
    val dynamic: Boolean = true
)

object BallMoves {
    val all = listOf(
        BallMove("mid_spot", "mid_range", "定点中投", "接近投篮点站稳 → 屈髋屈膝预备 → 连贯举球出手 → 保持随挥与平衡落地。", "同一点位动作稳定后再换点；不要用加快出手掩盖脚步问题。", false),
        BallMove("mid_catch", "mid_range", "移动接球中投", "从篮下或侧翼启动 → 跑向目标点 → 接球同时完成一二步或跳停 → 起跳投篮。单练时可自抛自接模拟传球。", "先慢速接球站稳，再增加跑动速度；左右方向分别练习。"),
        BallMove("mid_pullup", "mid_range", "运一步急停跳投", "三威胁启动 → 左或右运一步 → 降低重心制动 → 收球急停 → 垂直起跳出手。", "左右突破各一组；先控制落地，再逐步提高突破速度。"),
        BallMove("mid_cross", "mid_range", "变向接急停跳投", "正面运球推进 → 体前或胯下变向 → 再运一步创造空间 → 急停收球 → 投篮。", "先用单次体前变向建立连贯性，再加胯下或连续变向。"),
        BallMove("mid_hesitation", "mid_range", "犹豫步接急停", "低速推进 → 抬头与节奏停顿做犹豫步 → 降低重心加速一步 → 急停投篮。", "保持运球合法且不停球；动作稳定后加入节奏变化。"),
        BallMove("mid_pickroll", "mid_range", "挡拆后中投", "从侧翼启动 → 贴近掩护路线运球 → 蛇形或横移进入中距离 → 平衡急停出手。单练用标志物模拟掩护。", "先练无防守路线；有陪练时再加入绕掩护与换防判断。"),
        BallMove("three_spot", "three_point", "定点三分", "三分线外平衡站位 → 腿部连贯发力 → 举球出手 → 保持手型并平衡落地。", "动作变形时退回近距离，不靠甩臂强行增加距离。", false),
        BallMove("three_catch", "three_point", "接球三分", "传球到来前调整脚步 → 接球进入投篮准备位置 → 一二步或跳停 → 连贯出手。单练可自抛自接。", "先保证接球脚步一致，再逐步缩短准备时间。"),
        BallMove("three_relocate", "three_point", "转移跑位接球三分", "从底角向侧翼或侧翼向弧顶移动 → 跑到空位 → 接球刹停 → 三分出手。", "先练两点之间转移，再增加折返；有陪练时加入回传。"),
        BallMove("three_pullup", "three_point", "运球急停三分", "运球推进到三分线外 → 提前降低重心 → 制动收球 → 平衡起跳出手。", "先从中投急停建立动作，再延伸到三分距离。"),
        BallMove("three_side", "three_point", "侧撤步三分", "做突破威胁 → 横向运球移动一步 → 侧向收球调整 → 双脚稳定后投篮。", "控制横移幅度，先慢速练脚步；避免漂移或踩线。"),
        BallMove("three_stepback", "three_point", "后撤步三分", "向前运球施压 → 控制重心减速 → 合法收球后撤 → 稳定支撑 → 出手。", "适合已掌握急停的训练者；先低速小幅练习，脚步不稳时退回基础动作。"),
        BallMove("free_routine", "free_throw", "固定流程罚球", "站位 → 固定运球次数 → 呼吸与瞄准 → 连贯出手 → 保持随挥。", "每球使用相同流程，比较整组表现。", false),
        BallMove("free_interval", "free_throw", "间歇穿插罚球", "完成一组低强度移动 → 调整呼吸 → 回到罚球线 → 按固定流程完成罚球。", "不以力竭制造压力；能控制呼吸和手型后再增加轻度移动。"),
        BallMove("free_pressure", "free_throw", "情境压力罚球", "设定两罚情境 → 暂停观察篮筐 → 完整执行个人流程 → 记录两次结果。", "未达目标不追加惩罚性训练，用多组样本观察稳定性。", false),
        BallMove("finish_drive", "finishing", "突破上篮", "三威胁启动 → 一至两次运球 → 稳定收球与步点 → 选择对应手终结 → 留出落地区域。", "左右手分别练，先低速打板再提速。"),
        BallMove("finish_cross", "finishing", "变向突破终结", "正面接近 → 体前或胯下变向 → 向空侧加速 → 合法收球 → 上篮。", "先单变向，再增加节奏变化；不为追求速度牺牲控球。"),
        BallMove("finish_cut", "finishing", "空切接球终结", "观察空间 → 变速空切 → 移动中接球 → 调整步点 → 上篮。单练可自抛后跟进接球。", "有陪练时加入传球时机；避免把无人防守练习当成对抗成绩。"),
        BallMove("finish_runner", "finishing", "运球抛投", "运球进入近框区域 → 减速并调整步点 → 单脚或双脚起跳抛投 → 控制落地。", "先固定距离，再变化进入方向；以舒适弧线与柔和触球为主。"),
        BallMove("handle_stationary", "handling", "原地双手控球", "保持运动姿态 → 左右手分别控球 → 抬头观察 → 改变高低与节奏。", "先稳定不丢球，再提高频率。", false),
        BallMove("handle_cross", "handling", "行进间变向", "慢速推进 → 降低重心 → 体前、胯下或背后变向 → 向新方向加速。", "标志物间距由宽到窄，保持双手均衡。"),
        BallMove("handle_combo", "handling", "组合运球衔接", "体前变向 → 胯下换手 → 背后保护 → 加速两步并减速。", "先把两个动作连起来，再增加第三个动作和移动。"),
        BallMove("handle_pace", "handling", "变速与犹豫步", "低速控球 → 抬头犹豫 → 降重心加速 → 急停再启动，持续保持运球。", "先练速度差异，再用视线与肩部动作增加欺骗性。")
    )

    fun forCategory(category: String): List<BallMove> = all.filter { it.category == category }
    fun find(key: String): BallMove? = all.firstOrNull { it.key == key }
    fun label(key: String): String = find(key)?.label ?: "未标注动作"
}
