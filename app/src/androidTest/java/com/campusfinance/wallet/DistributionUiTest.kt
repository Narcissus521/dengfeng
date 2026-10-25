package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DistributionUiTest:TeaUiScenario() {
    @Test fun allFourteenCategoriesStayWithDualRingAndBothRingsDrillDown() {
        val oldMotion=prefs.getBoolean("motion_enabled",true)
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db)
        val before=runBlocking{db.walletDao().read()};val note="distribution-${System.currentTimeMillis()}"
        val entries=ExpenseTaxonomy.groups.mapIndexed { i,g -> Entry(kind="EXPENSE",amount=(i+1)*1234L,
            accountId=before.accounts.first().id,date=today().toString(),category=g.name,categoryGroupId=g.id,
            subcategoryId=if(g.children.isEmpty())"" else g.children.first().id,
            necessity=Necessity.entries[i%3].name,note=note) }
        try {
            runBlocking{entries.forEach{repo.entry(it)}}
            navigate("分析");tapTag("analysis-open-category")
            find(hasTestTag("category-distribution"))
            val seen=mutableSetOf<String>()
            repeat(7) {
                ui.onNodeWithTag("analysis-donut").assertIsDisplayed()
                ExpenseTaxonomy.groups.forEach{g->if(runCatching{ui.onNodeWithTag("analysis-group-${g.id}").assertIsDisplayed()}.isSuccess)seen+=g.id}
                val next=ui.onNodeWithTag("distribution-next")
                if(runCatching{next.assertIsEnabled()}.isSuccess)next.performClick()
            }
            assertEquals(ExpenseTaxonomy.groups.map{it.id}.toSet(),seen)
            shot("distribution-all-categories")
            // A tap on the inner circle enters necessity-filtered real records.
            ui.onNodeWithTag("analysis-donut").performTouchInput {
                val radius=minOf(width,height)/2f
                click(Offset(width/2f+radius*.42f,height/2f))
            }
            ui.onNodeWithTag("form-title").assertTextEquals("消费记录");back()
            ui.onNodeWithTag("category-distribution").assertIsDisplayed()
            // A tap on the outer circle opens the corresponding category, independent of ranking page.
            ui.onNodeWithTag("analysis-donut").performTouchInput {
                val radius=minOf(width,height)/2f
                click(Offset(width/2f+radius*.82f,height/2f))
            }
            find(hasText("本类净消费"));ui.onNodeWithTag("analysis-donut").assertIsDisplayed()
            shot("distribution-category-dual-ring")
        } finally {
            runBlocking{db.walletDao().read().activeEntries.filter{it.note==note}.forEach{repo.voidEntry(it.id,"测试清理")}}
            db.close();prefs.edit().putBoolean("motion_enabled",oldMotion).commit()
        }
    }
}
