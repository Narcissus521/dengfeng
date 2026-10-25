package com.campusfinance.wallet

import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class AccountBalanceUiTest:TeaUiScenario() {
    private fun start() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",false).commit();ready()
    }
    private fun openAccounts(){navigate("账本");tapTag("card-LEDGER_ASSETS");ui.onNodeWithTag("form-title").assertTextEquals("我的账户")}
    private fun openSettings(){navigate("记账");tapTag("leaf-SETTINGS")}
    private fun cleanup(){prefs.edit().putBoolean("motion_enabled",true).commit()}

    @Test fun accountDetailsShowCurrentMoneyAndCalibrationNeverChangesOpeningOrSpending() {
        start();val db=WalletDatabase.open(context,true)
        val reason="核对账户界面测试${System.currentTimeMillis()}"
        try {
            val before=runBlocking{db.walletDao().read()}
            val a=before.accounts.first{it.disabledOn==null&&before.balance(it.id)!=it.opening}
            openAccounts();submit()
            ui.onNodeWithTag("form-title").assertTextEquals("添加账户")
            find(hasText("开始记账时的余额（元，可为负）"));find(hasText("开始记账日 YYYY-MM-DD"))
            back();assertEquals(before.accounts,runBlocking{db.walletDao().read().accounts})
            tapTag("account-open-${a.id}")
            ui.onNodeWithTag("form-title").assertTextEquals("账户详情")
            ui.onNodeWithTag("account-current-balance").assertTextEquals("¥ ${Money.format(before.balance(a.id))}")
            ui.onNodeWithText("开始记账时的余额（元，可为负）").assertDoesNotExist()
            ui.onNodeWithText("开始记账日 YYYY-MM-DD").assertDoesNotExist();shot("account-current-balance")
            tapTag("account-calibrate");fill("实际余额（元，可为负）",Money.plain(before.balance(a.id)+12345))
            fill("校准原因",reason);shot("account-calibration");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().balance(a.id)==before.balance(a.id)+12345}}
            ui.waitForIdle();ui.onNodeWithTag("form-title").assertTextEquals("账户详情")
            ui.onNodeWithTag("account-current-balance").assertTextEquals("¥ ${Money.format(before.balance(a.id)+12345)}")
            val after=runBlocking{db.walletDao().read()}
            assertEquals(a,after.accounts.first{it.id==a.id})
            assertEquals(before.income(currentMonth()),after.income(currentMonth()))
            assertEquals(before.usage(currentMonth()),after.usage(currentMonth()))
            val adjustment=after.activeEntries.single{it.note.contains(reason)}
            assertEquals("ADJUSTMENT",adjustment.kind);assertEquals(12345L,adjustment.amount)
            assertTrue(after.audits.any{it.entityId==adjustment.id&&it.reason==reason})
        }finally{
            runBlocking{db.walletDao().read().activeEntries.filter{it.note.contains(reason)}.forEach{WalletRepository(db).voidEntry(it.id,"测试恢复原余额")}}
            db.close();cleanup()
        }
    }
    @Test fun initialBalanceAndDateLiveInSettingsAndHistoricalDateGuardStillApplies() {
        start();val db=WalletDatabase.open(context,true)
        val before=runBlocking{db.walletDao().read()}
        val a=before.accounts.first{it.disabledOn==null&&it.start<today().toString()&&before.entries.any{e->e.accountId==it.id}}
        try {
            openAccounts();tapTag("account-open-${a.id}");tap("账户设置")
            ui.onNodeWithTag("form-title").assertTextEquals("账户设置")
            fill("开始记账日 YYYY-MM-DD",today().toString());fill("核对或修改原因","验证历史日期保护");submit()
            ui.waitUntil(10000){ui.onAllNodesWithText("未能保存").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("已有流水后，开始记账日只能向前扩展").assertIsDisplayed()
            ui.onNodeWithTag("save-confirmation").assertDoesNotExist()
            assertEquals(a,runBlocking{db.walletDao().read().accounts.first{it.id==a.id}});tap("知道了")
            fill("开始记账日 YYYY-MM-DD",a.start);fill("开始记账时的余额（元，可为负）",Money.plain(a.opening+1000))
            fill("核对或修改原因","核对账户初始设置");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().accounts.first{it.id==a.id}.opening==a.opening+1000}}
            ui.waitUntil(10000){ui.onAllNodesWithTag("save-confirmation").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("✓ 保存成功").assertIsDisplayed()
            ui.waitForIdle();ui.onNodeWithTag("account-current-balance").assertTextEquals("¥ ${Money.format(before.balance(a.id)+1000)}")
            openSettings();tap("账户设置");tap(a.name)
            find(hasText("开始记账时的余额（元，可为负）"));find(hasText("开始记账日 YYYY-MM-DD"));shot("account-initial-settings")
        }finally{runBlocking{WalletRepository(db).account(a,"测试恢复原账户设置")};db.close();cleanup()}
    }
    @Test fun settingsGroupImportAndExportInOneChildPageAndBackReturnsToSettings() {
        start()
        try {
            openSettings();find(hasText("导入与导出"))
            ui.onNodeWithText("导出账本备份（JSON）").assertDoesNotExist()
            ui.onNodeWithText("导入账本备份（仅限空账本）").assertDoesNotExist()
            tap("导入与导出");ui.onNodeWithTag("form-title").assertTextEquals("导入与导出")
            find(hasTestTag("backup-export"));find(hasTestTag("backup-import"));shot("import-export-group")
            back();ui.onNodeWithTag("form-title").assertTextEquals("设置");find(hasText("导入与导出"))
        }finally{cleanup()}
    }
}
