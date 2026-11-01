package com.campusfinance.wallet

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test

/** In-memory illustrative data only: these screens cannot write into any ledger. */
class AnalysisLayoutPreviewTest:TeaUiScenario() {
    private var initialPage by mutableStateOf("budget")
    private fun previewLedger():Ledger {
        val month=currentMonth();val day=today().toString()
        val amounts=listOf("food.canteen" to 56000L,"education.ai" to 16800L,"transport.transit" to 13800L,"household.personalcare" to 9800L,"leisure.media" to 13200L,"clothing.clothes" to 9000L)
        return Ledger(accounts=listOf(Account(id="preview",name="示例账户",type="现金",opening=250000,start="$month-01")),
            entries=amounts.mapIndexed{i,(leaf,amount)->val free=leaf.startsWith("leisure")||leaf.startsWith("clothing");Entry(id="preview-$i",kind="EXPENSE",amount=amount,accountId="preview",date=day,categoryGroupId=leaf.substringBefore('.'),subcategoryId=leaf,bucket=if(free)"FREE" else "NECESSARY",necessity=if(free)"NONESSENTIAL" else "NECESSARY")},
            budgets=listOf(Budget(month,200000,130000,50000)),categoryBudgets=listOf(CategoryBudget(month,"food",70000),CategoryBudget(month,"education",20000),CategoryBudget(month,"transport",12000)))
    }
    private fun render() {
        val fixture=previewLedger();val range=ReportRange.month(currentMonth())
        ui.runOnUiThread {ui.activity.setContent {
            DisplayEnvironment(ui.activity.displayController){WalletTheme {
                Box(Modifier.fillMaxSize().teaWallpaper().systemBarsPadding()) {
                    CompositionLocalProvider(LocalEmbeddedForm provides true) {
                        key(initialPage) {
                            val history=remember{mutableStateListOf(analysisEditor(initialPage,range))}
                            val editor=history.last()
                            key(editor){
                                val close={if(history.size>1){history.removeAt(history.lastIndex);Unit}}
                                val open:(Editor)->Unit={history.add(it)}
                                if(editor.type=="analysisRecords")AnalysisRecordsForm(fixture,ReportFilter.decode(editor.kind!!),close,open)
                                else AdvancedAnalysisForm(fixture,currentMonth(),editor.kind!!,close,open)
                            }
                        }
                    }
                }
            }}
        }}
        ui.waitForIdle()
    }
    @Test fun previewBudgetAndNecessityInBothThemesWithOnePageSummaries() {
        val theme=prefs.getString("theme_mode",null)
        try {
            listOf("LIGHT","DARK").forEach{mode->
                prefs.edit().putString("theme_mode",mode).commit();ready();initialPage="budget";render()
                find(hasTestTag("budget-focus-list"));shot("preview-budget-layout-check");ui.onNodeWithTag("budget-nature-summary").assertIsDisplayed();ui.onNodeWithText("查看全部分类").assertIsDisplayed()
                ui.onNodeWithTag("pageNext").assertDoesNotExist();shot("preview-budget-${mode.lowercase()}")
                tap("查看全部分类");find(hasTestTag("budget-all-list"));shot("preview-budget-categories-${mode.lowercase()}")
                tapTag("budget-category-food");find(hasTestTag("budget-branch-diagram"));back();back()
                ui.runOnIdle{initialPage="necessity"};find(hasTestTag("necessity-distribution"))
                val chart=ui.onNodeWithTag("necessity-chart").fetchSemanticsNode().boundsInRoot
                val legend=ui.onNodeWithTag("necessity-legend").fetchSemanticsNode().boundsInRoot
                assertTrue(legend.left>=chart.right);assertTrue(legend.top<chart.bottom&&legend.bottom>chart.top)
                ui.onNodeWithTag("necessity-type-UNDECIDED").assertIsDisplayed();ui.onNodeWithTag("pageNext").assertDoesNotExist();shot("preview-necessity-${mode.lowercase()}")
                ui.onNodeWithTag("analysis-donut").assertContentDescriptionEquals("双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断")
                tap("外圈");ui.onNodeWithTag("necessity-category-education").assertIsDisplayed();ui.onNodeWithTag("pageNext").assertDoesNotExist();shot("preview-necessity-categories-${mode.lowercase()}")
                tapTag("necessity-legend-next");ui.onNodeWithTag("necessity-category-clothing").assertIsDisplayed();tapTag("necessity-legend-previous")
                tapTag("necessity-category-education");ui.onNodeWithTag("form-title").assertTextEquals("学习教育");back()
                tap("内圈");ui.onNodeWithTag("analysis-donut").performTouchInput{click(Offset(center.x,center.y-minOf(width,height)*.21f))}
                ui.onNodeWithTag("form-title").assertTextEquals("消费记录");ui.onAllNodesWithText("必要消费").onFirst().assertIsDisplayed();back()
                tapTag("necessity-type-NONESSENTIAL");find(hasTestTag("analysis-trend"));ui.onNodeWithTag("form-title").assertTextEquals("非必要消费详情")
                back();find(hasTestTag("necessity-distribution"))
            }
        } finally {prefs.edit().putString("theme_mode",theme).commit()}
    }
}
