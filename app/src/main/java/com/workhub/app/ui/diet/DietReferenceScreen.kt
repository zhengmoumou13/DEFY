package com.workhub.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.nutrition.*
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.ui.dashboard.MetricCard
import com.workhub.app.ui.dashboard.PageHeading
import com.workhub.app.ui.dashboard.SectionHeading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietReferenceScreen(repo: AppRepository, settingsRepo: SettingsRepository, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val bodies by repo.allBodyRecords.collectAsStateWithLifecycle(emptyList())
    var loading by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var sex by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableIntStateOf(-1) }
    var weight by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(false) }
    var reviewed by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!loading) return@LaunchedEffect
        val settings = settingsRepo.settings.first()
        val p = settings.dietPlan
        sex = p.sex; activity = p.activity; weight = p.weightKg.ifBlank { settings.weightKg }
        carbs = p.carbsPerKg; protein = p.proteinPerKg; fat = p.fatPerKg; enabled = p.enabled; reviewed = p.reviewedOn
        loading = false
    }
    fun preset(newSex: String = sex, newActivity: Int = activity) {
        sex = newSex; activity = newActivity
        TanDietPlan.factors(sex, activity)?.let { carbs = it.carbs.toString(); protein = it.protein.toString(); fat = it.fat.toString() }
        message = null
    }
    val draft = DietPlanPreferences(enabled, sex, activity, weight, carbs, protein, fat, reviewed)
    val target = TanDietPlan.target(draft)
    val reviewDate = runCatching { LocalDate.parse(reviewed).plusDays(10) }.getOrNull()
    fun save(openPlan: Boolean) {
        saving = true; message = null
        scope.launch {
            try {
                val plan = draft.copy(enabled = if (openPlan) true else enabled, reviewedOn = reviewed.ifBlank { LocalDate.now().toString() })
                settingsRepo.saveDietPlan(plan)
                enabled = plan.enabled; reviewed = plan.reviewedOn
                message = if (plan.enabled) "已保存。下次生成每日或长期饮食时会使用这些目标。" else "已保存，AI 饮食使用通用方案。"
                if (openPlan) nav.navigate("diet")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "保存失败，请检查参数后重试" }
            finally { saving = false }
        }
    }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("谭成义减脂饮食") }, windowInsets = WindowInsets(0,0,0,0), navigationIcon = {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PageHeading("NUTRITION METHOD", "从适合你的份量开始", "${TanDietPlan.version}\n根据原视频整理，非官方产品。") }
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("先选运动量，再算每天吃多少", style = MaterialTheme.typography.titleMedium)
                    Text("视频按性别、每周运动时长和体重设置营养起点。以下数值是参考摄入，不代表已经形成热量缺口。", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { runCatching { uri.openUri(TanDietPlan.source) }.onFailure { message = "无法打开浏览器，请稍后重试" } }) { Text("查看谭成义原视频 ↗") }
                }
            } }
            item { SectionHeading("计算条件") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(sex == "male", { preset(newSex = "male") }, enabled = !loading && !saving, label = { Text("男性参数") })
                FilterChip(sex == "female", { preset(newSex = "female") }, enabled = !loading && !saving, label = { Text("女性参数") })
            } }
            item { OutlinedTextField(weight, { weight = it; message = null }, label = { Text("计算基准体重（kg）") }, supportingText = { Text("输入 30–300 kg；复盘时再更新，不随单日波动自动改变。") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !loading && !saving, modifier = Modifier.fillMaxWidth()) }
            bodies.firstOrNull { it.weightKg != null }?.let { record -> item {
                TextButton(onClick = { weight = record.weightKg.toString() }, enabled = !saving) { Text("使用最近记录：${record.weightKg} kg（${record.date}）") }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TanDietPlan.activityLabels.forEachIndexed { i, label ->
                    FilterChip(activity == i, { preset(newActivity = i) }, enabled = !loading && !saving, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
                }
                Text("按实际运动时长选择；久坐不运动或运动量超出表格范围时，不直接套用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { SectionHeading("每公斤体重的营养系数", "恢复原表") { preset() } }
            item { Text("单位 g/kg。切换性别或运动量会载入原表，之后可按复盘结果手动调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("碳水", carbs, { s: String -> carbs = s }), Triple("蛋白质", protein, { s: String -> protein = s }), Triple("脂肪", fat, { s: String -> fat = s })).forEach { (label, value, change) ->
                    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = !loading && !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            } }
            if (target != null) {
                item { SectionHeading("每日参考总量") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("${target.carbs.roundToInt()} g", "碳水", Modifier.weight(1f), compact = true)
                    MetricCard("${target.protein.roundToInt()} g", "蛋白质", Modifier.weight(1f), compact = true)
                    MetricCard("${target.fat.roundToInt()} g", "脂肪", Modifier.weight(1f), compact = true)
                } }
                item { Text("约 ${target.calories} kcal · 按碳水和蛋白质 4 kcal/g、脂肪 9 kcal/g 折算。营养克数不等于食物重量。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            } else item { Text("请选择性别、运动量并填写有效数值。系数范围：碳水 1–6、蛋白质 0.8–2.5、脂肪 0.5–1.5 g/kg。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { SectionHeading("落实到三餐") }
            item { OutlinedButton(onClick = { nav.navigate("food_converter") }, modifier = Modifier.fillMaxWidth()) { Text("把营养克数换成食物份量") } }
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("早餐 · 燕麦、鸡蛋，搭配水果或少量种子")
                    Text("午餐 · 米饭、鱼或禽肉，搭配蔬菜")
                    Text("晚餐 · 薯类、牛肉或等价蛋白来源，搭配蔬菜")
                    Text("份量由你的营养目标和偏好进一步分配。油、坚果、水果以及食材中的全部营养素均需计入；注意生重与熟重。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            item { SectionHeading("每 10 天，回看一次") }
            item { Text(if (reviewDate == null) "保存后开始一个 10 天观察周期。记录体重趋势、训练状态和饥饿感，再决定是否调整。" else {
                val remaining = ChronoUnit.DAYS.between(LocalDate.now(), reviewDate)
                if (remaining > 0) "下次复盘 $reviewDate · 还剩 $remaining 天。结合连续记录判断，不按某一天的体重增减改计划。" else "本轮已到复盘时间。查看身体记录、训练状态与饮食执行，再更新基准体重或系数。"
            }, style = MaterialTheme.typography.bodyMedium) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { nav.navigate("body") }) { Text("查看身体记录") }
                TextButton(onClick = { reviewed = LocalDate.now().toString(); message = "复盘日期已更新，保存后开始下一个 10 天周期。" }, enabled = !saving) { Text("已完成本轮复盘") }
            } }
            item { Text("这是面向成年训练者的参考方案。未成年人、孕期、疾病或进食障碍等情况需要个体化营养建议。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("用于 AI 饮食计划", style = MaterialTheme.typography.titleSmall); Text("保存后影响新生成的每日与长期计划", style = MaterialTheme.typography.bodySmall) }
                Switch(enabled, { enabled = it }, enabled = !loading && !saving && (target != null || enabled))
            } }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item { Button(onClick = { save(true) }, enabled = target != null && !loading && !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "启用并制定饮食计划") } }
            item { OutlinedButton(onClick = { save(false) }, enabled = !loading && !saving && (!enabled || target != null), modifier = Modifier.fillMaxWidth()) { Text("仅保存设置") } }
        }
    }
}
