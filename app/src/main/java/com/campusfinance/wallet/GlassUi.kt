package com.campusfinance.wallet

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable fun Modifier.teaWallpaper():Modifier = background(Page)
@Composable fun Modifier.teaGlass(tint:Color=Card,radius:Float=8f):Modifier = ledgerMaterial(tint,radius.coerceIn(4f,10f))
@Composable fun GlassSelector(labels:List<String>,selectedIndex:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,icons:List<String>?=null) {
    LedgerSelector(labels,selectedIndex,onSelect,modifier,icons)
}
