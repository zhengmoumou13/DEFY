package com.workhub.app.ui.gym.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.workhub.app.gym.OriginalDemo
import kotlin.math.*

@Composable
fun OriginalDemoPlayer(demo: OriginalDemo) {
    var playing by rememberSaveable(demo) { mutableStateOf(false) }
    var slow by rememberSaveable(demo) { mutableStateOf(false) }
    var phase by rememberSaveable(demo) { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playing = false }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, slow) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    phase = (phase + ((now - last) / 1_000_000_000f).coerceAtMost(.1f) / (if (slow) 8f else 4f)) % 1f
                    last = now
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("原创动作示意", style = MaterialTheme.typography.labelLarge)
            Text("${demo.viewLabel} · ${if (phase < .5f) "去程" else "回程"}", style = MaterialTheme.typography.labelMedium)
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(320f / 260f).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF142127)).semantics { contentDescription = "原创${demo.viewLabel}动作示意，可用下方进度条逐帧查看" }) {
            scale(size.width / 320f, size.height / 260f, pivot = Offset.Zero) { drawOriginalPose(demo, ((1 - cos(phase * 2 * PI)) / 2).toFloat()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { playing = !playing }) { Text(if (playing) "暂停" else "播放") }
            FilterChip(selected = slow, onClick = { slow = !slow }, label = { Text("0.5× 慢速") })
        }
        Slider(value = phase, onValueChange = { playing = false; phase = it }, modifier = Modifier.semantics { contentDescription = "动作演示进度" })
        Text("示意图用于观察运动方向，具体执行请结合下方动作步骤。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawOriginalPose(demo: OriginalDemo, t: Float) {
    val body = Color(0xFFE7F1EE)
    val active = Color(0xFFFFAF78)
    val muted = Color(0xFF52727E)
    fun point(x: Number, y: Number) = Offset(x.toFloat(), y.toFloat())
    fun mix(a: Offset, b: Offset) = a + (b - a) * t
    fun bone(a: Offset, b: Offset, color: Color = body, width: Float = 9f) = drawLine(color, a, b, width, StrokeCap.Round)
    fun joint(p: Offset) = drawCircle(Color(0xFF142127), 3f, p)
    fun limb(a: Offset, b: Offset, c: Offset, color: Color = body) { bone(a,b,color);bone(b,c,color);joint(b) }
    // Two-bone inverse kinematics keeps elbows attached while the hand travels.
    fun elbow(a: Offset, hand: Offset, bend: Float): Offset {
        val d = (hand - a).getDistance().coerceIn(1f, 77f)
        val direction = (hand - a) / (hand - a).getDistance().coerceAtLeast(1f)
        return a + direction * (d / 2) + Offset(-direction.y, direction.x) * (sqrt((39f * 39f - d * d / 4).coerceAtLeast(0f)) * bend)
    }
    fun bar(p: Offset) {
        bone(p + point(-36,0), p + point(36,0), muted, 4f)
        bone(p + point(-25,-10), p + point(-25,10), active, 7f)
        bone(p + point(25,-10), p + point(25,10), active, 7f)
    }
    fun dumbbell(p: Offset, vertical: Boolean = false) {
        val axis = if (vertical) point(0,13) else point(13,0)
        val end = if (vertical) point(6,0) else point(0,6)
        bone(p-axis,p+axis,muted,4f)
        bone(p-axis-end,p-axis+end,active,6f); bone(p+axis-end,p+axis+end,active,6f)
    }
    fun head(center: Offset) { drawCircle(body,12f,center);drawCircle(muted,2f,center+point(5,-1)) }
    for (x in 20..300 step 40) drawLine(Color(0xFF20343C),point(x,14),point(x,236),1f)
    bone(point(30,238),point(290,238),muted,2f)
    when(demo) {
        OriginalDemo.LATERAL, OriginalDemo.PRESS -> {
            val left = point(140,83); val right = point(180,83)
            bone(left,right);bone(point(160,86),point(160,156),body,15f)
            head(point(160,53));bone(point(160,66),point(160,83))
            limb(point(150,155),point(143,196),point(138,232));limb(point(170,155),point(177,196),point(182,232))
            bone(point(127,234),point(145,234));bone(point(176,234),point(194,234))
            val l = if(demo==OriginalDemo.LATERAL) mix(point(131,158),point(67,87)) else mix(point(113,79),point(137,13))
            val r = point(320-l.x,l.y)
            limb(left,elbow(left,l,-1f),l,active);limb(right,elbow(right,r,1f),r,active)
            dumbbell(l);dumbbell(r)
        }
        OriginalDemo.BENCH, OriginalDemo.INCLINE -> {
            val incline = demo == OriginalDemo.INCLINE
            val shoulder = if(incline) point(122,118) else point(95,166)
            val hip = point(187,174)
            bone(shoulder+point(-14,14),hip+point(12,14),muted,9f)
            bone(point(111,192),point(111,236),muted,6f);bone(point(196,191),point(196,236),muted,6f)
            bone(hip,shoulder,body,14f)
            head(shoulder + if(incline) point(-16,-18) else point(-26,-3))
            limb(hip,point(225,191),point(238,233));bone(point(230,234),point(256,234))
            val hand = mix(shoulder+point(26,-20),shoulder+point(3,-77))
            limb(shoulder,elbow(shoulder,hand,1f),hand,active);bar(hand)
        }
        OriginalDemo.CURL, OriginalDemo.HAMMER -> {
            head(point(159,49));bone(point(160,65),point(160,146),body,14f)
            limb(point(160,145),point(150,188),point(143,232));limb(point(163,145),point(177,188),point(187,232),muted)
            bone(point(137,234),point(158,234));bone(point(181,234),point(201,234),muted)
            val elbow = point(164,126)
            val angle = (65f - 130f*t)*PI.toFloat()/180f
            val hand = elbow + point(38*cos(angle),38*sin(angle))
            limb(point(160,85),elbow,hand,active)
            if(demo==OriginalDemo.HAMMER) dumbbell(hand,true) else bar(hand)
        }
        OriginalDemo.SQUAT -> {
            val hip=mix(point(154,144),point(118,187));val shoulder=mix(point(157,77),point(145,122))
            val knee=mix(point(158,190),point(177,179));val ankle=point(157,233)
            limb(hip+point(-7,0),knee+point(-12,0),ankle+point(-13,0),muted)
            limb(hip,knee,ankle,active);bone(ankle+point(-10,1),ankle+point(22,1))
            bone(hip,shoulder,body,14f);head(shoulder+point(2,-27));bone(shoulder,shoulder+point(1,-13))
            limb(shoulder,shoulder+point(-15,31),shoulder+point(-24,-2));bar(shoulder+point(-12,-2))
        }
        OriginalDemo.RDL, OriginalDemo.ROW -> {
            val hinge=if(demo==OriginalDemo.ROW) 1f else t
            val hip=point(164-35*hinge,144+6*hinge)
            val shoulder=point(162+42*hinge,77+42*hinge)
            limb(hip,point(167+10*hinge,190),point(162,233),active);bone(point(151,234),point(184,234))
            bone(hip,shoulder,body,14f);head(shoulder+point(2+12*hinge,-26+12*hinge))
            val hand=if(demo==OriginalDemo.ROW) mix(shoulder+point(0,76),point(160,155)) else shoulder+point(0,76)
            limb(shoulder,elbow(shoulder,hand,1f),hand,active);bar(hand)
        }
        OriginalDemo.PUSHUP -> {
            val shoulder=mix(point(104,157),point(104,208));val hip=mix(point(192,181),point(192,215))
            val foot=point(265,233);val hand=point(99,233)
            bone(shoulder,hip,body,14f);limb(hip,(hip+foot)/2f,foot);head(shoulder+point(-24,-8))
            limb(shoulder,elbow(shoulder,hand,-1f),hand,active);bone(hand+point(-10,1),hand+point(13,1))
        }
    }
}
