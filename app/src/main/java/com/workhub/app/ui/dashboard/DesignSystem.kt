package com.workhub.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import com.workhub.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workhub.app.ui.theme.DefyBrand
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PageHeading(kicker: String, title: String, subtitle: String = "") {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_defy_wordmark), "DEFY", Modifier.width(49.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
        }
    }
}

@Composable
fun FeatureCard(tag: String, title: String, subtitle: String, button: String, icon: ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .99f else 1f, tween(120), label = "primaryActionPress")
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1D23), contentColor = Color(0xFFF6F7F9),
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = Color(0xFFC1C6CF))
                Text(tag, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC1C6CF), modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(24.dp), tint = Color(0xFF8D95A3))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBDC3CE))
            Surface(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                interactionSource = source, color = Color(0xFFF3F4F7), contentColor = Color(0xFF15181D),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(button, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The orbit is the real weekly goal. Tapping reveals the remaining days, without changing records. */
@Composable
fun OrbitProgressCard(completed: Int, target: Int, modifier: Modifier = Modifier) {
    var detail by rememberSaveable { mutableStateOf(false) }
    val goal = target.coerceAtLeast(1)
    val progress by animateFloatAsState((completed.toFloat() / goal).coerceIn(0f, 1f), tween(700), label = "weeklyOrbit")
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outlineVariant
    val haptic = LocalHapticFeedback.current
    Card(onClick = { detail = !detail; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, modifier = modifier.semantics { contentDescription = "本周训练 $completed 天，目标 $goal 天。点击切换剩余天数" }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("本周训练", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.size(26.dp)) {
                    val inset = 2.dp.toPx(); val diameter = size.minDimension - inset*2
                    drawCircle(track, diameter/2, style = Stroke(1.dp.toPx()))
                    drawArc(ink, -90f, 360f*progress, false, Offset(inset,inset), Size(diameter,diameter), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                    val angle = Math.toRadians((360f*progress-90f).toDouble())
                    drawCircle(ink, 2.dp.toPx(), center + Offset(cos(angle).toFloat()*diameter/2, sin(angle).toFloat()*diameter/2))
                }
            }
            AnimatedContent(detail, label = "weeklyGoalDetail") { showDetail ->
                Text(if (showDetail) if(completed >= goal) "已达成" else "还差 ${goal-completed} 天" else "$completed / $goal", fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.8).sp)
            }
            Text(if (detail) "目标每周 $goal 天" else "天 · 轻触查看剩余", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        DashboardActionRow(DashboardAction(icon, title, subtitle, onClick))
    }
}

data class DashboardAction(val icon: ImageVector, val title: String, val subtitle: String = "", val onClick: () -> Unit)

@Composable
fun ActionGroup(vararg actions: DashboardAction) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            actions.forEachIndexed { index, action ->
                DashboardActionRow(action)
                if (index < actions.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}

@Composable
private fun DashboardActionRow(action: DashboardAction) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action.onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(action.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (action.subtitle.isNotBlank()) Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BrandHeader(date: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        DefyBrand(Modifier.weight(1f))
        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetricCard(value: String, label: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    Card(modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = if (compact) 21.sp else 27.sp, lineHeight = if (compact) 28.sp else 36.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
