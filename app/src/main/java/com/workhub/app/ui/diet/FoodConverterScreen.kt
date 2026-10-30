package com.workhub.app.ui.diet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.workhub.app.nutrition.*
import com.workhub.app.ui.ai.PlanDisclosure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodConverterScreen(nav: NavHostController, meal: String = "") {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var catalogue by remember { mutableStateOf<List<CompositionFood>>(emptyList()) }
    var customJson by rememberSaveable { mutableStateOf("[]") }
    val customFoods = remember(customJson) { FoodComposition.json.decodeFromString<List<CompositionFood>>(customJson) }
    val foods = catalogue + customFoods
    var loadError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var linkError by remember { mutableStateOf(false) }
    LaunchedEffect(loadAttempt) {
        loadError = false
        try { catalogue = FoodCatalogue.load(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loadError = true }
    }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var sourceId by rememberSaveable(meal) { mutableStateOf(if (meal.isBlank()) "169757" else "") }
    var targetId by rememberSaveable { mutableStateOf("170440") }
    var gramsText by rememberSaveable(meal) { mutableStateOf(if (meal.isBlank()) "100" else "") }
    var targetText by rememberSaveable { mutableStateOf("30") }
    var nutrientName by rememberSaveable { mutableStateOf(FoodNutrient.CARBS.name) }
    var picker by rememberSaveable { mutableIntStateOf(0) }
    var customFor by rememberSaveable { mutableIntStateOf(0) }
    var basketJson by rememberSaveable { mutableStateOf("[]") }
    val basket = remember(basketJson) { FoodComposition.json.decodeFromString<List<FoodPortion>>(basketJson) }
    val source = foods.firstOrNull { it.id == sourceId }
    val replacement = foods.firstOrNull { it.id == targetId }
    val nutrient = FoodNutrient.valueOf(nutrientName)
    val grams = FoodComposition.input(gramsText)
    val wanted = FoodComposition.input(targetText)
    val sourceNutrients = source?.let { f -> grams?.let { FoodComposition.forWeight(f, it) } }
    val targetGrams = source?.let { f -> wanted?.let { FoodComposition.gramsFor(f, nutrient, it) } }
    val equivalent = if (source != null && replacement != null && grams != null) FoodComposition.equivalentGrams(source, grams, replacement, nutrient) else null
    fun fmt(value: Double) = FoodComposition.number(value)
    fun copyResult(text: String) { clipboard.setText(AnnotatedString(text)) }
    fun openSource(food: CompositionFood) {
        runCatching { uri.openUri(food.sourceUrl) }.onFailure { linkError = true }
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("食物成分换算") }, windowInsets = WindowInsets(0, 0, 0, 0), navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScrollableTabRow(selectedTabIndex = mode, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("食物成分", "目标份量", "食材替换").forEachIndexed { index, label ->
                    Tab(selected = mode == index, onClick = { mode = index }, text = { Text(label, maxLines = 1) })
                }
            }
            if (meal.isNotBlank()) PlanDisclosure("本餐食材参考", meal) {
                Text(meal, style = MaterialTheme.typography.bodyMedium)
                Text("按下方条目的生熟状态选择食材，再输入实际可食重量。", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when (mode) { 0 -> "输入食物重量，查看成分，也可逐项加入本餐合计。"; 1 -> "选择一项营养目标，反算这种食物的份量。"; else -> "选择替换依据，对比替换前后的全部营养变化。" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                loadError -> { Text("食物数据加载失败"); OutlinedButton(onClick = { loadAttempt++ }) { Text("重试") } }
                catalogue.isEmpty() -> CircularProgressIndicator()
                source == null -> {
                    OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.fillMaxWidth()) { Text("选择本餐食材") }
                    Text("先确认食物与生熟状态，再输入克数。计划中的碗、个、ml 等份量不会自动当作克数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    FoodChoice(if (mode == 2) "原食材" else "选择食物", source) { picker = 1 }
                    Text("以可食部克数计算，液体也按 g 称重。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mode != 1) {
                        OutlinedTextField(gramsText, { gramsText = it }, label = { Text("食物重量（g）") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                            isError = gramsText.isNotBlank() && sourceNutrients == null,
                            supportingText = { if (gramsText.isNotBlank() && sourceNutrients == null) Text("请输入 0–10000 g 的有效数值") })
                    }
                    if (mode > 0) {
                        Text(if (mode == 1) "要达到的营养目标" else "按哪一项等量替换", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FoodNutrient.entries.forEach { n -> FilterChip(nutrient == n, { nutrientName = n.name }, label = { Text(n.label) }) }
                        }
                    }
                    when (mode) {
                        0 -> sourceNutrients?.let { amount ->
                            NutrientResult("${source.name} · ${fmt(grams!!)} g", amount)
                            Button(onClick = { basketJson = FoodComposition.json.encodeToString(basket + FoodPortion(source.id, grams!!)) },
                                enabled = grams!! > 0 && basket.size < 50, modifier = Modifier.fillMaxWidth()) { Text("加入本餐合计") }
                            if (basket.size >= 50) Text("本次合计最多 50 项，可移除已有条目后再加入。", style = MaterialTheme.typography.bodySmall)
                        }
                        1 -> {
                            OutlinedTextField(targetText, { targetText = it }, label = { Text("${nutrient.label}目标（${nutrient.unit}）") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), isError = targetText.isNotBlank() && wanted == null)
                            if (targetGrams != null) {
                                NutrientResult("约 ${fmt(targetGrams)} g ${source.name}", FoodComposition.forWeight(source, targetGrams)!!)
                                Text("仅对齐${nutrient.label}目标；其他营养素如上。这是一种食物的换算份量，不是整日配餐。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { copyResult("${nutrient.label} ${fmt(wanted!!)} ${nutrient.unit} ≈ ${source.name} ${fmt(targetGrams)} g（${source.state}）。\n${nutrientText(FoodComposition.forWeight(source, targetGrams)!!)}\n按所选食材数据估算；仅对齐${nutrient.label}。") }, modifier = Modifier.fillMaxWidth()) { Text("复制换算结果") }
                            } else if (targetText.isNotBlank()) Text("无法换算：请检查目标数值、食物是否含有该营养素，以及结果是否超过 10000 g。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        2 -> {
                            replacement?.let { to ->
                                FoodChoice("替换为", to) { picker = 2 }
                                if (equivalent != null && sourceNutrients != null) {
                                    val after = FoodComposition.forWeight(to, equivalent)!!
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("约 ${fmt(equivalent)} g ${to.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text("与原份量的${nutrient.label}相当", style = MaterialTheme.typography.bodySmall)
                                            NutrientComparison(sourceNutrients, after)
                                        }
                                    }
                                    Text("只对齐${nutrient.label}，并非营养完全等同。生熟条目各自计算，不代表烹调前后重量转换。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = { copyResult("${source.name} ${fmt(grams!!)} g（${source.state}）≈ ${to.name} ${fmt(equivalent)} g（${to.state}），仅按${nutrient.label}等量。\n替换后：${nutrientText(after)}\n按所选食材数据估算，不代表其他营养素相同。") }, modifier = Modifier.fillMaxWidth()) { Text("复制替换结果") }
                                } else Text("无法按${nutrient.label}替换：请检查重量、两种食物该项含量是否为 0，或换算份量是否超出范围。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (mode == 0 && basket.isNotEmpty()) {
                        val total = FoodComposition.total(basket, foods)
                        total?.let { NutrientResult("本餐合计 · ${basket.size} 项", it) }
                        basket.forEachIndexed { index, portion ->
                            val food = foods.first { it.id == portion.foodId }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${food.name} · ${fmt(portion.grams)} g", style = MaterialTheme.typography.bodyMedium)
                                    Text(food.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { basketJson = FoodComposition.json.encodeToString(basket.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Default.Close, "移除${food.name}") }
                            }
                        }
                        Text("本餐合计保留在本次换算中，未修改饮食计划或实际记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { copyResult(basket.joinToString("\n") { p -> val f = foods.first { it.id == p.foodId }; "${f.name} ${fmt(p.grams)} g（${f.state}）" } + "\n合计：${nutrientText(total!!)}\n按所选食材数据估算。") }, enabled = total != null, modifier = Modifier.fillMaxWidth()) { Text("复制本餐成分") }
                    }
                    PlanDisclosure("数据来源与计算口径", source.id + targetId) {
                        Text("USDA FoodData Central · SR Legacy 2018，内置 ${catalogue.size} 条常见食物记录。按每 100 g 可食部比例计算，热量使用数据库值。品牌、品种与烹调方式会影响结果，包装食品可录入自己的营养标签。", style = MaterialTheme.typography.bodySmall)
                        Text("当前：${source.sourceName}", style = MaterialTheme.typography.bodySmall)
                        if (!source.isCustom) TextButton(onClick = { openSource(source) }) { Text("查看当前食材原始数据 ↗") }
                        if (mode == 2 && replacement != null && !replacement.isCustom) TextButton(onClick = { openSource(replacement) }) { Text("查看替换食材原始数据 ↗") }
                        if (linkError) Text("暂时无法打开浏览器，请稍后重试。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (picker != 0) FoodPicker(foods, onDismiss = { picker = 0 }, onCustom = { customFor = picker; picker = 0 }, onSelect = { if (picker == 1) sourceId = it.id else targetId = it.id; picker = 0 })
    if (customFor != 0) FoodLabelDialog(onDismiss = { customFor = 0 }, onAdd = { food ->
        customJson = FoodComposition.json.encodeToString(customFoods + food)
        if (customFor == 2) targetId = food.id else sourceId = food.id
        customFor = 0
    })
}

@Composable
private fun FoodChoice(label: String, food: CompositionFood, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label · 点击更换", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(food.state, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FoodPicker(foods: List<CompositionFood>, onDismiss: () -> Unit, onCustom: () -> Unit, onSelect: (CompositionFood) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = foods.filter { query.isBlank() || "${it.name} ${it.state} ${it.group} ${it.sourceName}".contains(query.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择食物与生熟状态") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索食材，如米饭、鸡胸肉") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(filtered, key = { it.id }) { food ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(food) }.padding(vertical = 12.dp)) {
                        Text(food.name, style = MaterialTheme.typography.titleSmall)
                        Text(food.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (filtered.isEmpty()) item { Text("暂未收录，可按包装上每 100 g 的营养标签录入。", modifier = Modifier.padding(vertical = 12.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }, dismissButton = { TextButton(onClick = onCustom) { Text("按标签录入") } })
}

@Composable
private fun FoodLabelDialog(onDismiss: () -> Unit, onAdd: (CompositionFood) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var energy by rememberSaveable { mutableStateOf("") }
    var energyKj by rememberSaveable { mutableStateOf(true) }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    val values = listOf(energy, protein, carbs, fat).map { FoodComposition.input(it) }
    val nutrients = if (values.all { it != null }) FoodNutrients(FoodComposition.energyKcal(values[0]!!, energyKj)!!, values[1]!!, values[2]!!, values[3]!!) else null
    val valid = nutrients != null && FoodComposition.validLabel(name, nutrients)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("按每 100 g 标签录入") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("用于本次换算。请填每 100 g 可食部数值，不能直接填每份或每 100 ml 的数值。热量可选 kJ 或 kcal，自动换算。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("食物名称与状态") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(energyKj, { energyKj = true }, label = { Text("kJ") })
                FilterChip(!energyKj, { energyKj = false }, label = { Text("kcal") })
            }
            listOf(Triple("热量（${if (energyKj) "kJ" else "kcal"}）", energy, { v: String -> energy = v }), Triple("蛋白质（g）", protein, { v: String -> protein = v }), Triple("碳水（g）", carbs, { v: String -> carbs = v }), Triple("脂肪（g）", fat, { v: String -> fat = v })).forEach { (label, value, change) ->
                OutlinedTextField(value, change, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (nutrients != null && !valid) Text("请填写名称，并检查每 100 g 的数值：营养素不能为负或超过总重量，热量不超过 1000 kcal。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(onClick = { onAdd(CompositionFood("custom-${java.util.UUID.randomUUID()}", name.trim(), "按自填标签 · 每 100 g", "自定义", "用户输入的营养标签（本次换算）", nutrients!!)) }, enabled = valid) { Text("用于本次换算") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun nutrientText(n: FoodNutrients) = "热量 ${FoodComposition.number(n.calories)} kcal · 蛋白质 ${FoodComposition.number(n.protein)} g · 碳水 ${FoodComposition.number(n.carbs)} g · 脂肪 ${FoodComposition.number(n.fat)} g"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutrientResult(title: String, n: FoodNutrients) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(FoodNutrient.ENERGY, FoodNutrient.PROTEIN, FoodNutrient.CARBS, FoodNutrient.FAT).forEach { nutrient ->
                    Column {
                        Text("${FoodComposition.number(n.value(nutrient))} ${nutrient.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(nutrient.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("参考估算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NutrientComparison(before: FoodNutrients, after: FoodNutrients) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { listOf("成分", "原份量", "替换后", "变化").forEach { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) } }
        listOf(FoodNutrient.ENERGY, FoodNutrient.PROTEIN, FoodNutrient.CARBS, FoodNutrient.FAT).forEach { nutrient ->
            val difference = after.value(nutrient) - before.value(nutrient)
            Row {
                listOf("${nutrient.label}\n${nutrient.unit}", FoodComposition.number(before.value(nutrient)), FoodComposition.number(after.value(nutrient)), (if (difference >= 0.05) "+" else "") + FoodComposition.number(if (kotlin.math.abs(difference) < 0.05) 0.0 else difference)).forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
            }
        }
    }
}
