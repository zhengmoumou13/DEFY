package com.workhub.app.ui.ball

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.workhub.app.training.BallDrill
import com.workhub.app.training.BallMoves

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallCategoryScreen(category: String, navController: NavHostController) {
    val drill = BallDrill.fromKey(category) ?: BallDrill.MID_RANGE
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("${drill.label}训练") },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                BallPanel {
                    Text("从技术动作到结合球应用", style = MaterialTheme.typography.titleMedium)
                    Text("选择动作自行练习，或让 AI 根据水平、时间、陪练条件和近期记录安排完整训练。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { navController.navigate("ball_plan?focus=${drill.key}") }, modifier = Modifier.fillMaxWidth()) { Text("AI 制定${drill.label}计划") }
                }
            }
            items(BallMoves.forCategory(drill.key), key = { it.key }) { move ->
                BallPanel {
                    Text(move.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(move.steps, style = MaterialTheme.typography.bodyMedium)
                    Text("练习进阶 · ${move.progression}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { navController.navigate("ball_record?category=${drill.key}&movementKey=${move.key}") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("记录这个动作") }
                }
            }
        }
    }
}
