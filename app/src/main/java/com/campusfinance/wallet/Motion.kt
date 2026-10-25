package com.campusfinance.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

val LocalEditorVisibility = staticCompositionLocalOf<MutableTransitionState<Boolean>?> { null }

/** Transition between exact amounts; never interpolate financial values. */
@Composable fun AnimatedMoney(amount:Long,modifier:Modifier=Modifier,color:Color=Ink,size:TextUnit=15.sp,weight:FontWeight=FontWeight.Medium) {
    val motion=LocalDisplayState.current
    AnimatedContent(targetState=amount,modifier=modifier,label="amount change",transitionSpec={
        (fadeIn(tween(motion.duration(180)))+slideInVertically(tween(motion.duration(220),easing=FastOutSlowInEasing)){it/5}) togetherWith
            (fadeOut(tween(motion.duration(90)))+slideOutVertically(tween(motion.duration(130))){-it/5})
    }) { value -> Text("¥ ${Money.format(value)}",fontSize=size,fontWeight=weight,color=color) }
}
