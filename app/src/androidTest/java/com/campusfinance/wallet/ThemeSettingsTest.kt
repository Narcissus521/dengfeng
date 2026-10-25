package com.campusfinance.wallet

import androidx.test.filters.SdkSuppress

import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class ThemeSettingsTest:TeaUiScenario() {
    private fun settings(){navigate("记账");tapTag("leaf-SETTINGS");tap("主题、显示与动效")}
    @Test fun themesApplyImmediatelyPersistAndCoverCalendar() {
        val original=prefs.getString("theme_mode",null)
        try {
            prefs.edit().putString("theme_mode","LIGHT").commit();ready();settings()
            tapTag("theme-DARK");assertEquals("DARK",prefs.getString("theme_mode",null))
            navigate("首页");ui.onNodeWithTag("theme-dark").assertExists()
            ui.activityRule.scenario.recreate()
            ui.waitUntil(15000){runCatching{ui.onAllNodesWithTag("theme-dark").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            ui.waitUntil(10000){var focus=false;ui.activityRule.scenario.onActivity{focus=it.hasWindowFocus()};focus}
            navigate("记账");tap("支出");tapTag("date-picker-实际发生日期 YYYY-MM-DD")
            shot("dark-calendar");Espresso.pressBack();navigate("首页")
            settings();tapTag("theme-LIGHT");navigate("首页");ui.onNodeWithTag("theme-light").assertExists()
            navigate("记账");tap("支出");tapTag("date-picker-实际发生日期 YYYY-MM-DD")
            shot("light-calendar");Espresso.pressBack();navigate("首页")
            settings();tapTag("theme-SYSTEM");navigate("首页")
            val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
            ui.onNodeWithTag(if(dark)"theme-dark" else "theme-light").assertExists()
        }finally{prefs.edit().apply{if(original==null)remove("theme_mode") else putString("theme_mode",original)}.commit()}
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    @Test fun followingSystemRespondsAndManualChoiceOverridesIt() {
        assumeTrue(Build.VERSION.SDK_INT>=29 && (Build.MODEL.contains("sdk",true)||Build.FINGERPRINT.contains("generic",true)))
        val original=shell("cmd uimode night").substringAfter(":").trim()
        val previous=prefs.getString("theme_mode",null)
        try {
            prefs.edit().putString("theme_mode","SYSTEM").commit();shell("cmd uimode night no");ready()
            ui.onNodeWithTag("theme-light").assertExists();shell("cmd uimode night yes")
            ui.waitUntil(15000){runCatching{ui.onAllNodesWithTag("theme-dark").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            settings();tapTag("theme-LIGHT");navigate("首页");ui.onNodeWithTag("theme-light").assertExists()
            settings();tapTag("theme-SYSTEM");navigate("首页");ui.onNodeWithTag("theme-dark").assertExists()
            shell("cmd uimode night no")
            ui.waitUntil(15000){runCatching{ui.onAllNodesWithTag("theme-light").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        }finally{
            if(original in listOf("yes","no","auto","custom"))shell("cmd uimode night $original")
            prefs.edit().apply{if(previous==null)remove("theme_mode") else putString("theme_mode",previous)}.commit()
        }
    }
}


