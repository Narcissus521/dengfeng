package com.campusfinance.wallet

import androidx.test.filters.SdkSuppress

import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class MotionSettingsTest:TeaUiScenario() {
    @Test fun settingsAndChildPagesWorkWithMotionOnAndOff() {
        prefs.edit().putBoolean("high_refresh",true).putBoolean("motion_enabled",true).commit()
        ready();navigate("记账");tapTag("leaf-SETTINGS");tap("主题、显示与动效")
        tapTag("highRefreshToggle")
        ui.activityRule.scenario.onActivity{assertEquals(0f,it.window.attributes.preferredRefreshRate,0f)}
        tapTag("highRefreshToggle");tapTag("motionToggle")
        assertFalse(prefs.getBoolean("motion_enabled",true))
        navigate("账本");ui.onNodeWithText("我的账本").assertIsDisplayed()
        ui.activityRule.scenario.onActivity{it.displayController.setAnimations(true)}
        navigate("首页");navigate("记账");tap("支出")
        ui.onNodeWithTag("form-title").assertTextEquals("记录支出");back()
        navigate("首页");ui.onNodeWithText("茶间小结").assertIsDisplayed()
    }
}


