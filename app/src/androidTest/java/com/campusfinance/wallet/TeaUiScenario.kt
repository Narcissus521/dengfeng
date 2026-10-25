package com.campusfinance.wallet

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import java.io.File

abstract class TeaUiScenario {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    protected val context get()=ApplicationProvider.getApplicationContext<Context>()
    protected val prefs get()=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
    protected fun ready() {
        prefs.edit().putString("mode","demo").putInt(TutorialPolicy.KEY,TutorialPolicy.VERSION).commit()
        ui.activityRule.scenario.recreate()
        ui.waitUntil(20000){ui.onAllNodesWithTag("dock-首页").fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle()
    }
    protected fun node(m:SemanticsMatcher)=ui.onAllNodes(m).onLast()
    protected fun find(m:SemanticsMatcher) {
        ui.waitForIdle()
        fun visible()=runCatching{node(m).assertIsDisplayed()}.isSuccess
        if(visible())return
        for(i in 0 until 50) {
            val prev=ui.onAllNodesWithTag("pagePrevious").onLast()
            if(runCatching{prev.assertIsEnabled()}.isFailure)break
            prev.performClick();ui.waitForIdle()
        }
        for(i in 0 until 60) {
            if(visible())return
            val next=ui.onAllNodesWithTag("pageNext").onLast()
            if(runCatching{next.assertIsEnabled()}.isFailure)break
            next.performClick();ui.waitForIdle()
        }
        node(m).assertIsDisplayed()
    }
    protected fun tap(text:String){val m=hasText(text);find(m);node(m).performClick();ui.waitForIdle()}
    protected fun fill(text:String,value:String){val m=hasText(text);find(m);node(m).performTextReplacement(value);Espresso.closeSoftKeyboard();ui.waitForIdle()}
    protected fun tapTag(tag:String){val m=hasTestTag(tag);find(m);node(m).performClick();ui.waitForIdle()}
    protected fun fillTag(tag:String,value:String){val m=hasTestTag(tag);find(m);node(m).performTextReplacement(value);Espresso.closeSoftKeyboard();ui.waitForIdle()}
    protected fun submit(){Espresso.closeSoftKeyboard();node(hasTestTag("form-submit")).performClick();ui.waitForIdle()}
    protected fun navigate(page:String){
        node(hasTestTag("dock-$page")).performClick()
        val index=listOf("首页","账本","规划","分析").indexOf(page)
        if(index>=0)ui.waitUntil(15000){ui.onAllNodesWithTag("main-page-$index").fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle()
    }
    protected fun back(){node(hasText("‹ 返回")).performClick();ui.waitForIdle()}
    protected fun shot(name:String){
        ui.waitForIdle();val file=File(context.getExternalFilesDir(null),"screenshots/0.7-$name.png");file.parentFile?.mkdirs()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}

