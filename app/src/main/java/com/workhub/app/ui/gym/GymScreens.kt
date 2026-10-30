package com.workhub.app.ui.gym

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.*
import com.workhub.app.gym.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
private fun catalogue(): List<GymExercise>? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<List<GymExercise>?>(null) }
    LaunchedEffect(Unit) { data = GymCatalogue.load(context) }
    return data
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GymPage(title: String, nav: NavHostController, onBack: () -> Unit = { nav.popBackStack() }, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }) }, content = content)
}

@Composable
fun GymScreen(repo: AppRepository, nav: NavHostController) {
    val exercises = catalogue()
    val templates by repo.db.gymDao().observeTemplates().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.db.gymDao().observeSessions().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var starterPicker by remember { mutableStateOf(false) }
    var seeds by remember { mutableStateOf(emptyList<GymRoutine>()) }
    var detail by remember { mutableStateOf<GymExercise?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    GymPage("力量训练", nav) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("我的模板", "动作库", "训练记录").forEachIndexed { i, label -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) }) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            when (tab) {
                1 -> if (exercises == null) CircularProgressIndicator(Modifier.padding(24.dp)) else ExerciseBrowser(exercises) { detail = it }
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sessions.isEmpty()) item { Text("还没有训练记录。从模板开始，完成的每一组都会自动保存。") }
                    items(sessions, key = { it.id }) { session ->
                        Card(onClick = { nav.navigate("gym_session/${session.id}") }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(GymCodec.decode(session.content).name, style = MaterialTheme.typography.titleMedium)
                                Text("${session.date} · ${if (session.completedAt == null) "未结束 · 点击继续" else "已结束 · 查看记录"}")
                            }
                        }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("先安排动作，再专注完成每一组。", style = MaterialTheme.typography.titleMedium)
                        Text("离线动作库 · 模板可自由修改 · 组记录自动保存", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.navigate("gym_edit/0") }) { Text("新建模板") }
                            OutlinedButton(onClick = { scope.launch { seeds = GymCatalogue.starters(context); starterPicker = true } }) { Text("导入入门模板") }
                        }
                    }
                    sessions.firstOrNull { it.completedAt == null }?.let { session -> item {
                        Card(onClick = { nav.navigate("gym_session/${session.id}") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("继续未结束的训练", style = MaterialTheme.typography.titleSmall)
                                Text(GymCodec.decode(session.content).name)
                            }
                        }
                    } }
                    if (templates.isEmpty()) item { Text("还没有模板。可以导入推 / 拉 / 腿、上下肢、全身或 5×5 模板，再按自己的情况调整。") }
                    items(templates, key = { it.id }) { template ->
                        val routine = remember(template.content) { GymCodec.decode(template.content) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(template.name, style = MaterialTheme.typography.titleMedium)
                                Text("${routine.exercises.size} 个动作 · ${routine.exercises.sumOf { it.sets }} 组")
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(enabled = !busy, onClick = {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val session = GymSessionEntity(UUID.randomUUID().toString(), LocalDate.now().toString(), template.content)
                                                repo.db.gymDao().saveSession(session)
                                                nav.navigate("gym_session/${session.id}")
                                            } catch (e: Exception) { error = "无法开始训练，请重试" } finally { busy = false }
                                        }
                                    }) { Text("开始训练") }
                                    TextButton(onClick = { nav.navigate("gym_edit/${template.id}") }) { Text("编辑") }
                                }
                            }
                        }
                    }
                    item { TextButton(onClick = { scope.launch { notice = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        listOf("SOURCE.txt", "LICENSE", "NOTICE.md").joinToString("\n\n") { file -> context.assets.open("opengym/$file").bufferedReader().use { it.readText() } }
                    } } }) { Text("openGym 来源与开源许可") } }
                }
            }
        }
    }
    if (starterPicker) AlertDialog(onDismissRequest = { if (!busy) starterPicker = false }, title = { Text("选择入门模板") }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) { items(seeds) { seed ->
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch { try {
                    repo.db.gymDao().saveTemplate(GymTemplateEntity(name = seed.name, content = GymCodec.encode(seed)))
                    starterPicker = false
                } catch (e: Exception) { error = "导入失败，请重试" } finally { busy = false } }
            }) { Text("${seed.name} · ${seed.exercises.size} 个动作") }
        } }
    }, confirmButton = { TextButton(onClick = { starterPicker = false }, enabled = !busy) { Text("关闭") } })
    detail?.let { ExerciseDetail(it, repo, { detail = null }) }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, title = { Text("开源许可") }, text = {
        Text(it, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
    }, confirmButton = { TextButton(onClick = { notice = null }) { Text("关闭") } }) }
}

@Composable
private fun ExerciseBrowser(exercises: List<GymExercise>, onPick: (GymExercise) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    var demoOnly by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }
    val results = remember(exercises, query, body, equipment, demoOnly) { exercises.filter {
        (!demoOnly || OriginalDemo.forExercise(it) != null) && (body.isBlank() || body == it.bodyPart) && (equipment.isBlank() || equipment == it.equipment) &&
            (query.isBlank() || "${it.name} ${it.englishName} ${it.target} ${gymLabel(it.bodyPart)}".contains(query.trim(), ignoreCase = true))
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("搜索中文 / 英文动作") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = body.isNotEmpty(), onClick = { filter = "body" }, label = { Text(if (body.isBlank()) "全部部位" else gymLabel(body)) })
            FilterChip(selected = equipment.isNotEmpty(), onClick = { filter = "equipment" }, label = { Text(if (equipment.isBlank()) "全部器械" else gymLabel(equipment)) })
        }
        FilterChip(selected = demoOnly, onClick = { demoOnly = !demoOnly }, label = { Text("仅看原创动态示意") })
        Text("${results.size} 个动作", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.weight(1f)) {
            if (results.isEmpty()) item { Text("没有匹配的动作，试试其他关键词或清除筛选。", modifier = Modifier.padding(vertical = 20.dp)) }
            items(results, key = { it.id }) { exercise ->
                ListItem(headlineContent = { Text(exercise.name) }, supportingContent = { Text("${gymLabel(exercise.bodyPart)} · ${gymLabel(exercise.equipment)}${if (OriginalDemo.forExercise(exercise) != null) " · 原创示意" else ""}") }, modifier = Modifier.clickable { onPick(exercise) })
                HorizontalDivider()
            }
        }
    }
    filter?.let { kind ->
        val options = remember(kind, exercises) { listOf("") + exercises.map { if (kind == "body") it.bodyPart else it.equipment }.distinct().sorted() }
        AlertDialog(onDismissRequest = { filter = null }, title = { Text("筛选${if (kind == "body") "部位" else "器械"}") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) { items(options) { option -> TextButton(onClick = { if (kind == "body") body = option else equipment = option; filter = null }) { Text(if (option.isBlank()) "全部" else gymLabel(option)) } } }
        }, confirmButton = { TextButton(onClick = { filter = null }) { Text("关闭") } })
    }
}

@Composable
private fun ExerciseDetail(exercise: GymExercise, repo: AppRepository, dismiss: () -> Unit, add: (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(exercise.name) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.workhub.app.ui.gym.media.ExerciseMedia(exercise, repo)
            Text(exercise.englishName, style = MaterialTheme.typography.bodySmall)
            Text("${gymLabel(exercise.bodyPart)} · ${gymLabel(exercise.equipment)}")
            Text(exercise.description)
            exercise.instructions.forEachIndexed { i, instruction -> Text("${i + 1}. $instruction") }
        }
    }, confirmButton = { TextButton(onClick = { if (add != null) add() else dismiss() }) { Text(if (add != null) "加入模板" else "关闭") } },
        dismissButton = { if (add != null) TextButton(onClick = dismiss) { Text("返回") } })
}

@Composable
fun GymEditorScreen(repo: AppRepository, nav: NavHostController, id: Long) {
    val exercises = catalogue()
    val scope = rememberCoroutineScope()
    var content by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var originalContent by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var leave by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<GymExercise?>(null) }
    var editing by remember { mutableStateOf<GymPrescription?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            if (content == null) content = if (id == 0L) GymCodec.encode(GymRoutine("我的训练", emptyList())) else repo.db.gymDao().template(id)?.content
            if (originalContent == null) originalContent = content
            if (content == null) error = "模板不存在"
        } catch (e: Exception) { error = "读取模板失败" }
        ready = true
    }
    val routine = content?.let { GymCodec.decode(it) }
    fun update(value: GymRoutine) { content = GymCodec.encode(value) }
    val dirty = ready && content != originalContent
    val back: () -> Unit = { if (!busy) { if (dirty) leave = true else nav.popBackStack() } }
    BackHandler(enabled = dirty || busy) { back() }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("放弃未保存的修改？") },
        text = { Text("模板尚未保存，返回后这次修改会丢失。") },
        confirmButton = { TextButton(onClick = { leave = false; nav.popBackStack() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text("继续编辑") } })
    GymPage("编辑训练模板", nav, onBack = back) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!ready) item { CircularProgressIndicator() }
            if (routine != null) {
                item { OutlinedTextField(routine.name, { update(routine.copy(name = it)) }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                items(routine.exercises, key = { it.slotId }) { p ->
                    val exercise = exercises?.firstOrNull { it.id == p.exerciseId }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(exercise?.name ?: p.exerciseId, style = MaterialTheme.typography.titleMedium)
                            Text("${p.sets} 组 × ${if (p.timed) "${p.seconds} 秒" else "${p.reps} 次"} · ${p.weightKg} kg · 休息 ${p.restSeconds} 秒")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = p }) { Text("修改") }
                                TextButton(onClick = {
                                    val index = routine.exercises.indexOf(p)
                                    if (index > 0) update(routine.copy(exercises = routine.exercises.toMutableList().apply { removeAt(index); add(index - 1, p) }))
                                }, enabled = routine.exercises.first() != p) { Text("上移") }
                                TextButton(onClick = { update(routine.copy(exercises = routine.exercises.filterNot { it.slotId == p.slotId })) }) { Text("移除") }
                            }
                        }
                    }
                }
                item { OutlinedButton(onClick = { picking = true }, enabled = exercises != null, modifier = Modifier.fillMaxWidth()) { Text("从动作库添加") } }
                item { Button(enabled = !busy && routine.name.isNotBlank() && routine.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true
                    scope.launch { try {
                        require(routine.exercises.all { it.valid() })
                        repo.db.gymDao().saveTemplate(GymTemplateEntity(id, routine.name.trim(), GymCodec.encode(routine.copy(name = routine.name.trim()))))
                        nav.popBackStack()
                    } catch (e: Exception) { error = "保存失败，请检查动作参数后重试" } finally { busy = false } }
                }) { Text(if (busy) "保存中…" else "保存模板") } }
                if (id != 0L) item { TextButton(onClick = { deleting = true }, enabled = !busy) { Text("删除模板") } }
            }
        }
    }
    if (picking && exercises != null && routine != null) Dialog(onDismissRequest = { picking = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column { TextButton(onClick = { picking = false }) { Text("完成选择") }; Box(Modifier.weight(1f)) { ExerciseBrowser(exercises) { detail = it } } } }
    }
    detail?.let { exercise -> ExerciseDetail(exercise, repo, { detail = null }) {
        if (routine != null) update(routine.copy(exercises = routine.exercises + GymPrescription(exercise.id, timed = exercise.hold)))
        detail = null; picking = false
    } }
    editing?.let { p -> PrescriptionDialog(p, { editing = null }) { updated ->
        if (routine != null) update(routine.copy(exercises = routine.exercises.map { if (it.slotId == p.slotId) updated else it }))
        editing = null
    } }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除这个模板？") }, text = { Text("已保存的训练记录仍会保留。") }, confirmButton = {
        TextButton(enabled = !busy, onClick = { busy = true; scope.launch { try { repo.db.gymDao().deleteTemplate(id); nav.popBackStack() } catch (e: Exception) { error = "删除失败" } finally { busy = false; deleting = false } } }) { Text("删除") }
    }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}

@Composable
private fun PrescriptionDialog(p: GymPrescription, dismiss: () -> Unit, save: (GymPrescription) -> Unit) {
    var sets by rememberSaveable { mutableStateOf(p.sets.toString()) }
    var reps by rememberSaveable { mutableStateOf(p.reps.toString()) }
    var seconds by rememberSaveable { mutableStateOf(p.seconds.toString()) }
    var weight by rememberSaveable { mutableStateOf(p.weightKg.toString()) }
    var rest by rememberSaveable { mutableStateOf(p.restSeconds.toString()) }
    var timed by rememberSaveable { mutableStateOf(p.timed) }
    val candidate = p.copy(sets = sets.toIntOrNull() ?: 0, reps = reps.toIntOrNull() ?: 0, seconds = seconds.toIntOrNull() ?: 0,
        weightKg = weight.toDoubleOrNull() ?: -1.0, restSeconds = rest.toIntOrNull() ?: -1, timed = timed)
    AlertDialog(onDismissRequest = dismiss, title = { Text("动作安排") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(timed, { timed = it }); Text("按时长记录") }
            NumberField("组数（1–20）", sets) { sets = it }
            if (timed) NumberField("每组秒数（1–3600）", seconds) { seconds = it } else NumberField("每组次数（1–500）", reps) { reps = it }
            NumberField("重量 kg（0 表示自重 / 不负重）", weight) { weight = it }
            NumberField("组间休息秒数（0–900）", rest) { rest = it }
        }
    }, confirmButton = { TextButton(enabled = candidate.valid(), onClick = { save(candidate) }) { Text("应用") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
fun GymSessionScreen(repo: AppRepository, nav: NavHostController, sessionId: String) {
    var session by remember(sessionId) { mutableStateOf<GymSessionEntity?>(null) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId) { repo.db.gymDao().observeSession(sessionId).collect { session = it; loaded = true } }
    val sets by repo.db.gymDao().observeSets(sessionId).collectAsStateWithLifecycle(emptyList())
    val history by repo.observeAllWorkoutSets().collectAsStateWithLifecycle(emptyList())
    val exercises = catalogue()
    val scope = rememberCoroutineScope()
    var demonstration by remember { mutableStateOf<GymExercise?>(null) }
    var editing by remember { mutableStateOf<Pair<GymPrescription, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finish by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var restUntil by rememberSaveable(sessionId) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restUntil) { while (restUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val routine = session?.content?.let { GymCodec.decode(it) }
    GymPage(routine?.name ?: if (loaded) "训练记录" else "加载训练…", nav) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loaded) item { CircularProgressIndicator() }
            else if (session == null) item { Text("这次训练已取消或记录不存在，请返回训练列表。") }
            if (routine != null) {
                item {
                    Text("${session?.date} · 已记录 ${sets.size} / ${routine.exercises.sumOf { it.sets }} 组", style = MaterialTheme.typography.titleMedium)
                    Text(if (session?.completedAt == null) "点击组次填写实际完成量。返回后可从训练记录继续。" else "本次训练已结束，以下为实际记录。", style = MaterialTheme.typography.bodySmall)
                    if (session?.completedAt == null && sets.isEmpty()) {
                        TextButton(onClick = { discard = true }, enabled = !busy) { Text("取消本次训练") }
                    }
                    if (restUntil > now && session?.completedAt == null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("休息 ${(restUntil - now + 999) / 1000} 秒", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { restUntil = 0 }) { Text("跳过") }
                            }
                        }
                    }
                }
                items(routine.exercises, key = { it.slotId }) { p ->
                    val exercise = exercises?.firstOrNull { it.id == p.exerciseId }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise?.name ?: p.exerciseId, style = MaterialTheme.typography.titleMedium)
                            if (exercise != null) TextButton(onClick = { demonstration = exercise }) { Text("查看动作演示与步骤") }
                            Text("目标 ${p.sets} 组 × ${if (p.timed) "${p.seconds} 秒" else "${p.reps} 次"} · ${p.weightKg} kg", style = MaterialTheme.typography.bodySmall)
                            repeat(p.sets) { index ->
                                val saved = sets.firstOrNull { it.gymSlotKey == "${p.slotId}:$index" }
                                OutlinedButton(enabled = !busy && session?.completedAt == null && exercise != null,
                                    onClick = { editing = p to index }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (saved == null) "第 ${index + 1} 组 · 记录" else "✓ 第 ${index + 1} 组 · ${saved.weightKg ?: 0.0} kg × ${if (p.timed) "${saved.durationSeconds} 秒" else "${saved.reps} 次"}${if (session?.completedAt == null) " · 修改" else ""}")
                                }
                            }
                        }
                    }
                }
                if (session?.completedAt == null && sets.isNotEmpty()) item {
                    Button(onClick = { finish = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("结束本次训练") }
                }
            }
        }
    }
    demonstration?.let { ExerciseDetail(it, repo, { demonstration = null }) }
    editing?.let { (p, index) ->
        val exercise = exercises?.firstOrNull { it.id == p.exerciseId }
        val saved = sets.firstOrNull { it.gymSlotKey == "${p.slotId}:$index" }
        val previous = saved ?: history.filter { it.exercise == exercise?.name && it.metricType == if (p.timed) "duration" else "reps" }.maxByOrNull { it.createdAt }
        if (exercise != null && session != null) key(p.slotId, index) {
            SetRecordDialog(exercise.name, p, previous, busy, error, { if (!busy) { editing = null; error = null } }) { weight, value, rpe ->
                busy = true; error = null
                val snapshot = session!!
                scope.launch {
                    try {
                        repo.saveGymSet(WorkoutSetEntity(date = snapshot.date, exercise = exercise.name, gymSessionId = sessionId,
                            gymSlotKey = "${p.slotId}:$index", weightKg = weight, reps = if (p.timed) null else value,
                            metricType = if (p.timed) "duration" else "reps", durationSeconds = if (p.timed) value else null, rpe = rpe,
                            note = "${routine?.name} · openGym:${exercise.id}", createdAt = saved?.createdAt ?: System.currentTimeMillis()))
                        editing = null
                        if (saved == null) restUntil = System.currentTimeMillis() + p.restSeconds * 1000L
                    } catch (e: Exception) { error = "保存失败，请重试" } finally { busy = false }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { if (!busy) discard = false }, title = { Text("取消这次训练？") },
        text = { Text("还没有记录任何组次。取消后不会计入训练，模板仍会保留。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    if (repo.db.gymDao().discardEmptySession(sessionId) == 1) nav.popBackStack()
                    else error = "训练已有记录，请返回查看后结束训练。"
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = "取消失败，请重试" }
                finally { busy = false; discard = false }
            }
        }) { Text("确认取消") } }, dismissButton = { TextButton(enabled = !busy, onClick = { discard = false }) { Text("继续训练") } })
    if (finish) AlertDialog(onDismissRequest = { if (!busy) finish = false }, title = { Text("结束本次训练？") },
        text = { Text("已记录 ${sets.size} / ${routine?.exercises?.sumOf { it.sets }} 组。未记录的组不会算作完成。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { try { repo.db.gymDao().finish(sessionId, System.currentTimeMillis()); finish = false; restUntil = 0 } catch (e: Exception) { error = "结束训练失败，请重试" } finally { busy = false } }
        }) { Text("结束并保存") } }, dismissButton = { TextButton(onClick = { finish = false }, enabled = !busy) { Text("继续训练") } })
}

@Composable
private fun SetRecordDialog(name: String, p: GymPrescription, previous: WorkoutSetEntity?, busy: Boolean, error: String?, dismiss: () -> Unit,
    save: (Double, Int, Int?) -> Unit) {
    var weight by rememberSaveable { mutableStateOf((previous?.weightKg ?: p.weightKg).toString()) }
    var value by rememberSaveable { mutableStateOf((if (p.timed) previous?.durationSeconds ?: p.seconds else previous?.reps ?: p.reps).toString()) }
    var rpe by rememberSaveable { mutableStateOf(previous?.rpe?.toString() ?: "") }
    val w = weight.toDoubleOrNull()
    val v = value.toIntOrNull()
    val effort = rpe.toIntOrNull()
    val valid = w != null && w.isFinite() && w in 0.0..1000.0 && v != null && v in 1..(if (p.timed) 3600 else 500) && (rpe.isBlank() || (effort != null && effort in 1..10))
    AlertDialog(onDismissRequest = dismiss, title = { Text(name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (previous == null) "按实际完成量记录，可修改模板默认值。" else "已带入最近记录，请按本组实际完成量调整。", style = MaterialTheme.typography.bodySmall)
            NumberField("重量 kg（0 表示自重 / 不负重）", weight) { weight = it }
            NumberField(if (p.timed) "实际秒数（1–3600）" else "实际次数（1–500）", value) { value = it }
            NumberField("RPE（1–10，可不填）", rpe) { rpe = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { save(w!!, v!!, effort) }) { Text(if (busy) "保存中…" else "保存本组") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("取消") } })
}
