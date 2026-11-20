package com.campusfinance.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

val LocalEditorVisibility = staticCompositionLocalOf<MutableTransitionState<Boolean>?> { null }
val LocalInlineSave = compositionLocalOf<SaveConfirmation?> { null }

@Composable
internal fun Modifier.listArrival(index: Int): Modifier {
    val enabled = LocalDisplayState.current.motionEnabled
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled && progress.value == 0f) {
            delay(index.coerceAtMost(6) * 30L)
            progress.animateTo(1f, spring(dampingRatio = .74f, stiffness = 450f))
        } else progress.snapTo(1f)
    }
    return if (!enabled) this else graphicsLayer {
        alpha = progress.value.coerceIn(0f,1f)
        translationY = 9.dp.toPx() * (1f-progress.value)
    }
}

@Composable
internal fun Modifier.saveHighlight(event: SaveConfirmation?): Modifier {
    val animated = LocalDisplayState.current.motionEnabled
    val opacity = remember { Animatable(0f) }
    val tint = Forest
    LaunchedEffect(event, animated) {
        opacity.snapTo(if (event == null) 0f else 1f)
        if (event != null && animated) {
            delay(300)
            opacity.animateTo(0f, tween(740))
        }
    }
    return this.then(if (event == null) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        .drawWithCache {
            val radius = CornerRadius(5.dp.toPx())
            onDrawBehind { drawRoundRect(tint, cornerRadius = radius, alpha = .15f * opacity.value) }
        }
}

@Composable
internal fun Modifier.physicalPress(pressed: Boolean, radius: Dp): Modifier {
    val motion = LocalDisplayState.current.motionEnabled
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (motion && pressed) .96f else 1f,
        if (motion) spring(dampingRatio = if (pressed) .82f else .45f, stiffness = 650f, visibilityThreshold = .001f)
        else tween(0),
        label = "button compression")
    val depth by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f,
        tween(if (motion) 100 else 0), label = "button inner shadow")
    return graphicsLayer { scaleX = scale; scaleY = scale }.drawWithCache {
        val edge = minOf(size.height / 3f, 7.dp.toPx()).coerceAtLeast(1f)
        val corner = minOf(radius.toPx(), size.height / 2f, size.width / 2f)
        val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner))) }
        val shades = listOf(Color.Black.copy(alpha = .48f), Color.Transparent)
        val top = Brush.verticalGradient(shades, endY = edge)
        val left = Brush.horizontalGradient(shades, endX = edge)
        onDrawWithContent {
            drawContent()
            if (depth > 0f) clipPath(outline) {
                drawRect(top, size = Size(size.width, edge), alpha = depth)
                drawRect(left, size = Size(edge, size.height), alpha = depth * .6f)
            }
        }
    }
}

@Composable
internal fun PhysicalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    radius: Dp = 999.dp, shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Button(onClick, modifier.physicalPress(pressed, radius), enabled, shape, colors,
        interactionSource = interactions, content = content)
}

/** Transition between exact amounts; never interpolate financial values. */
@Composable fun AnimatedMoney(amount:Long,modifier:Modifier=Modifier,color:Color=Ink,size:TextUnit=15.sp,weight:FontWeight=FontWeight.Medium) {
    val motion=LocalDisplayState.current
    if(!motion.motionEnabled) {
        Text("¥ ${Money.format(amount)}",modifier,fontSize=size,fontWeight=weight,color=color)
        return
    }
    AnimatedContent(targetState=amount,modifier=modifier,label="amount change",transitionSpec={
        (fadeIn(tween(motion.duration(180)))+slideInVertically(tween(motion.duration(220),easing=FastOutSlowInEasing)){it/5}) togetherWith
            (fadeOut(tween(motion.duration(90)))+slideOutVertically(tween(motion.duration(130))){-it/5})
    }) { value -> Text("¥ ${Money.format(value)}",fontSize=size,fontWeight=weight,color=color) }
}
