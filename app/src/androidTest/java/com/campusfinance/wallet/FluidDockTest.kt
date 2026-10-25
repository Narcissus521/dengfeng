package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class FluidDockTest:TeaUiScenario() {
    // captureToImage waits for the Android draw too, while the Compose clock is paused.
    private fun dockShot(name:String):Bitmap {
        val bitmap=ui.onNodeWithTag("dock-drag").captureToImage().asAndroidBitmap()
        val file=File(context.getExternalFilesDir(null),"screenshots/0.8.1-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        return bitmap
    }
    @Test fun holdingAndCancellingDoesNotChangePageOrOpenTheLeaf() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit()
        ready();shot("fluid-home");val baseline=dockShot("dock-rest")
        ui.mainClock.autoAdvance=false
        try {
            val dock=ui.onNodeWithTag("dock-drag")
            dock.performTouchInput{down(Offset(width*.9f,center.y))}
            ui.mainClock.advanceTimeBy(160);ui.waitForIdle()
            ui.onNodeWithTag("dock-首页").assertIsSelected()
            ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
            val held=dockShot("dock-held");assertFalse("Holding must visibly move and lift the glass without committing a page",baseline.sameAs(held))
            held.recycle();baseline.recycle();shot("fluid-hold-analysis")
            dock.performTouchInput{cancel()};ui.mainClock.advanceTimeBy(1600);ui.waitForIdle()
            ui.onNodeWithTag("dock-首页").assertIsSelected()
            ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
            // A drag that passes the leaf and is cancelled also keeps the committed page.
            dock.performTouchInput{down(Offset(width*.1f,center.y));moveTo(Offset(width*.85f,center.y),160)}
            ui.mainClock.advanceTimeBy(120);ui.waitForIdle();dockShot("dock-stretched").recycle();shot("fluid-drag-cancel")
            dock.performTouchInput{cancel()};ui.mainClock.advanceTimeBy(1600);ui.waitForIdle()
            ui.onNodeWithTag("dock-首页").assertIsSelected();ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
        }finally{ui.mainClock.autoAdvance=true}
    }
    @Test fun quickReversalsFinishOnTheLastPageAndKeepASingleDock() {
        ready();ui.mainClock.autoAdvance=false
        try {
            for(label in listOf("分析","账本","规划","首页","分析")) {
                ui.onNodeWithTag("dock-$label").performTouchInput{click()}
                ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
            }
            ui.waitUntil(10000){ui.mainClock.advanceTimeByFrame();ui.onAllNodesWithTag("main-page-3").fetchSemanticsNodes().isNotEmpty()}
            shot("fluid-reversal-mid");ui.mainClock.advanceTimeBy(1800);ui.waitForIdle()
            ui.onNodeWithTag("dock-分析").assertIsSelected()
            ui.onNodeWithText("消费分析").assertIsDisplayed()
            ui.onAllNodesWithTag("dock-drag").assertCountEquals(1)
            ui.onNodeWithTag("leaf-menu").assertDoesNotExist();shot("fluid-analysis")
        }finally{ui.mainClock.autoAdvance=true}
        navigate("记账");ui.onNodeWithTag("leaf-menu").assertIsDisplayed();shot("fluid-leaf-menu");tapTag("leaf-close")
    }
}
