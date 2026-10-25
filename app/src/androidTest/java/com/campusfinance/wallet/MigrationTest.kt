package com.campusfinance.wallet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionFourAddsIndependentMetadataWithoutRewritingHistoricalMoney()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="test-v4-upgrade-${newId()}.db"
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.campusfinance.wallet.WalletDatabase/4.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val tables=schema.getJSONArray("entities")
            for(i in 0 until tables.length()){val t=tables.getJSONObject(i);old.execSQL(t.getString("createSql").replace("\${TABLE_NAME}",t.getString("tableName")))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profiles VALUES (1,'旧账本',123,0)")
            old.execSQL("INSERT INTO accounts VALUES ('a','现金','现金',100000,'2026-01-01',NULL)")
            old.execSQL("INSERT INTO entries (id,kind,amount,accountId,date,category,bucket,note,voided) VALUES ('e','EXPENSE',10000,'a','2026-09-01','购物','NECESSARY','旧消费',0)")
            old.execSQL("INSERT INTO entries (id,kind,amount,accountId,date,category,bucket,note,voided,originalId) VALUES ('r','REFUND',2000,'a','2026-10-01','','FREE','退款',0,'e')")
            old.execSQL("INSERT INTO recurring_rules VALUES ('rule','洗护','EXPENSE',3000,'MONTHLY',1,'08:00','2026-11-01','美护','FREE',1)")
            old.execSQL("INSERT INTO recurring_allocations VALUES ('rule','a',3000)")
            old.execSQL("INSERT INTO reminder_settings VALUES (1,1,'20:00','22:00','购物,美护,其他')")
            old.version=4
        }finally{old.close()}
        val upgraded=Room.databaseBuilder(context,WalletDatabase::class.java,name).addMigrations(WalletDatabase.MIGRATION_4_5).build()
        try {
            val s=upgraded.walletDao().read();Rules.validate(s)
            assertEquals(92000L,s.cash());assertEquals(-2000L,s.usage("2026-10"));assertTrue(s.categoryBudgets.isEmpty())
            assertEquals("购物",s.entries.first{it.id=="e"}.category);assertEquals("NECESSARY",s.entries.first{it.id=="e"}.bucket)
            assertTrue(s.entries.all{it.necessity=="UNDECIDED"&&it.categoryGroupId.isEmpty()&&it.subcategoryId.isEmpty()})
            assertEquals("UNDECIDED",s.recurringRules.single().necessity);assertEquals("美护",s.recurringRules.single().category)
            assertEquals("other.unclassified",s.expenseCategory(s.entries.first()).leaf.id)
            assertEquals(setOf("other"),s.reminder.selectedGroupIds().toSet())
            val repo=WalletRepository(upgraded);repo.budget(Budget("2026-10",20000,10000,5000));repo.categoryBudgets("2026-10",listOf(CategoryBudget("2026-10","other",12000)))
            assertEquals(92000L,upgraded.walletDao().read().cash())
        }finally{upgraded.close();context.deleteDatabase(name)}
    }
    @Test fun versionTwoPreservesPaidDebtSavingsAndFixedIncome()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="test-v2-upgrade-${newId()}.db"
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.campusfinance.wallet.WalletDatabase/2.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val tables=schema.getJSONArray("entities")
            for(i in 0 until tables.length()){val t=tables.getJSONObject(i);old.execSQL(t.getString("createSql").replace("\${TABLE_NAME}",t.getString("tableName")))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profiles VALUES (1, '0.3个人账本', 123, 0)")
            old.execSQL("INSERT INTO accounts VALUES ('a', '银行卡', '银行卡', 250000, ?, NULL)",arrayOf(today().minusMonths(1).withDayOfMonth(1).toString()))
            old.execSQL("INSERT INTO goals VALUES ('g', '电脑', 600000, ?, 80000, 0, 45678)",arrayOf(today().plusMonths(6).toString()))
            old.execSQL("INSERT INTO debts VALUES ('d', '旧负债', 10000, ?, '旧备注')",arrayOf(today().minusDays(1).toString()))
            old.execSQL("INSERT INTO installments VALUES ('i', 'd', 1, ?, 10000, 1000)",arrayOf(today().toString()))
            old.execSQL("INSERT INTO income_plans VALUES ('a', 50000, 1, '生活费', ?, 1)",arrayOf(currentMonth()))
            old.execSQL("INSERT INTO entries (id,kind,amount,accountId,date,category,bucket,note,voided,installmentId) VALUES ('p','REPAYMENT',11000,'a',?,'分期还款','FREE','旧还款',0,'i')",arrayOf(today().toString()))
            old.execSQL("INSERT INTO entries (id,kind,amount,accountId,date,category,bucket,note,voided,incomePlanId,incomePlanMonth) VALUES ('inc','INCOME',50000,'a',?,'生活费','FREE','旧收入',0,'a',?)",arrayOf(today().toString(),currentMonth()))
            old.version=2
        }finally{old.close()}
        val upgraded=Room.databaseBuilder(context,WalletDatabase::class.java,name).addMigrations(WalletDatabase.MIGRATION_2_3,WalletDatabase.MIGRATION_3_4,WalletDatabase.MIGRATION_4_5).build()
        try {
            val s=upgraded.walletDao().read();Rules.validate(s)
            assertEquals(289000L,s.cash());assertEquals(45678L,s.goals.single().saved);assertEquals(80000L,s.goals.single().intention)
            assertEquals("LEGACY",s.debts.single().scheduleType);assertNull(s.debts.single().categoryId);assertNull(s.installments.single().scheduledAmount)
            assertEquals(1,s.payments(s.installments.single()).size);assertNull(s.payments(s.installments.single()).single().repaymentId);assertNotNull(s.incomeReceipt("a",currentMonth()))
            assertEquals(defaultDebtCategories().toSet(),s.debtCategories.toSet())
            val repo=WalletRepository(upgraded);val d=Debt(id="new",name="新负债",principal=0,purchaseDate=today().toString(),categoryId="debt-kind-other",scheduleType="EQUAL")
            repo.debt(d,manualDebtSchedule(d.id,today().plusMonths(1).toString(),List(2){12345L}));assertEquals(289000L,upgraded.walletDao().read().cash())
        }finally{upgraded.close();context.deleteDatabase(name)}
    }
    @Test fun versionOneLedgerMigratesWithoutChangingMoneyOrLegacyReserve()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="test-v1-upgrade-${newId()}.db"
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.campusfinance.wallet.WalletDatabase/1.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val tables=schema.getJSONArray("entities")
            for(i in 0 until tables.length()){
                val table=tables.getJSONObject(i)
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}",table.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profiles VALUES (1, '旧版个人账本', 123, 0)")
            old.execSQL("INSERT INTO accounts VALUES ('a', '银行卡', '银行卡', 250000, ?, NULL)",arrayOf(today().minusMonths(1).toString()))
            old.execSQL("INSERT INTO goals VALUES ('g', '电脑', 600000, ?, 80000, 0)",arrayOf(today().plusMonths(6).toString()))
            old.execSQL("INSERT INTO entries VALUES ('e', 'EXPENSE', 50000, 'a', ?, '购物', 'FREE', NULL, NULL, NULL, '旧版消费', 0, NULL)",arrayOf(today().toString()))
            old.version=1
        }finally{old.close()}
        val upgraded=Room.databaseBuilder(context,WalletDatabase::class.java,name).addMigrations(WalletDatabase.MIGRATION_1_2,WalletDatabase.MIGRATION_2_3,WalletDatabase.MIGRATION_3_4,WalletDatabase.MIGRATION_4_5).build()
        try {
            val s=upgraded.walletDao().read();Rules.validate(s)
            assertEquals("旧版个人账本",s.profile.name);assertEquals(200000L,s.cash());assertEquals(80000L,s.goals.single().intention);assertEquals(0L,s.goals.single().saved);assertTrue(s.incomePlans.isEmpty());assertNull(s.entries.single().incomePlanId)
            val repo=WalletRepository(upgraded)
            repo.goal(s.goals.single().copy(saved=50000));repo.incomePlan(IncomePlan("a",200000,1,"生活费",currentMonth()))
            assertEquals(200000L,upgraded.walletDao().read().cash());assertEquals(50000L,upgraded.walletDao().read().goals.single().saved)
        }finally{upgraded.close();context.deleteDatabase(name)}
    }
}

