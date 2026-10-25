package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.filters.SdkSuppress
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class LeafMenuTest:TeaUiScenario() {
    @Test fun popupStaysOverTheCurrentPageAndDismissalPreservesAnalysisSelection() {
        ready()
        ui.onNodeWithText("示例账本").assertDoesNotExist()
        ui.onNodeWithText("我的账本").assertDoesNotExist()
        listOf("首页" to "茶间小结","账本" to "账户总资产","规划" to "资金规划","分析" to "消费分析").forEach { (page,title)->
            navigate(page);navigate("记账")
            ui.onNodeWithTag("leaf-menu").assertIsDisplayed()
            node(hasText(title)).assertIsDisplayed()
            listOf("EXPENSE","INCOME","TRANSFER","SETTINGS").forEach{ui.onNodeWithTag("leaf-$it").assertIsDisplayed()}
            shot("leaf-$page")
            Espresso.pressBack();ui.waitForIdle()
            ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
            node(hasText(title)).assertIsDisplayed()
        }
        tap("3 个月");navigate("记账");tapTag("leaf-close")
        ui.onNodeWithText("消费必要性").assertIsDisplayed()
    }
    @Test fun actionsOpenTheCorrectFormsAndDismissingThePopupKeepsUnfinishedInput() {
        ready()
        listOf("INCOME" to "记录收入","TRANSFER" to "记录转账","EXPENSE" to "记录支出").forEach { (kind,title)->
            navigate("记账");tapTag("leaf-$kind")
            ui.onNodeWithTag("form-title").assertTextEquals(title)
            back()
        }
        navigate("记账");tapTag("leaf-EXPENSE");fill("金额（元）","12.34")
        navigate("记账");tapTag("leaf-close")
        ui.onNodeWithText("金额（元）").assertTextContains("12.34")
        navigate("记账");Espresso.pressBack();ui.waitForIdle()
        ui.onNodeWithTag("form-title").assertTextEquals("记录支出")
        ui.onNodeWithText("金额（元）").assertTextContains("12.34")
        navigate("首页");navigate("记账");tapTag("leaf-SETTINGS")
        ui.onNodeWithTag("form-title").assertTextEquals("设置")
        tap("共同设计与免责声明")
        ui.onNodeWithText("梨花怎黛玉").assertIsDisplayed()
    }
    @Test fun dockPressFeedbackKeepsRoundedClippingInBothThemes() {
        listOf("LIGHT","DARK").forEach { theme ->
            prefs.edit().putString("theme_mode",theme).commit();ready()
            listOf("首页","记账","分析").forEach { page->
                ui.onNodeWithTag("dock-$page").performTouchInput{down(center);advanceEventTime(150)}
                ui.waitForIdle();shot("dock-${theme.lowercase()}-$page-held")
                ui.onNodeWithTag("dock-$page").performTouchInput{cancel()}
                ui.waitForIdle()
                ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
            }
        }
    }
}
