package com.campusfinance.wallet

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZonedDateTime

@Dao
abstract class WalletDao {
    @Query("SELECT * FROM profiles WHERE id = 1") abstract fun watchProfile(): Flow<Profile?>
    @Query("SELECT * FROM profiles WHERE id = 1") abstract suspend fun profile(): Profile?
    @Query("SELECT * FROM accounts") abstract suspend fun accounts(): List<Account>
    @Query("SELECT * FROM entries") abstract suspend fun entries(): List<Entry>
    @Query("SELECT * FROM budgets") abstract suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM category_budgets") abstract suspend fun categoryBudgets():List<CategoryBudget>
    @Query("DELETE FROM category_budgets WHERE month=:month AND categoryGroupId=:groupId") abstract suspend fun deleteCategoryBudget(month:String,groupId:String)
    @Query("SELECT * FROM goals") abstract suspend fun goals(): List<Goal>
    @Query("SELECT * FROM debts") abstract suspend fun debts(): List<Debt>
    @Query("SELECT * FROM installments") abstract suspend fun installments(): List<Installment>
    @Query("SELECT * FROM audits ORDER BY time DESC") abstract suspend fun audits(): List<Audit>
    @Query("SELECT * FROM income_plans") abstract suspend fun incomePlans(): List<IncomePlan>
    @Query("SELECT * FROM debt_categories") abstract suspend fun debtCategories(): List<DebtCategory>
    @Query("SELECT * FROM recurring_rules") abstract suspend fun recurringRules(): List<RecurringRule>
    @Query("SELECT * FROM recurring_allocations") abstract suspend fun recurringAllocations(): List<RecurringAllocation>
    @Query("SELECT * FROM recurring_occurrences") abstract suspend fun recurringOccurrences(): List<RecurringOccurrence>
    @Query("SELECT * FROM reminder_settings WHERE id = 1") abstract suspend fun reminderSettings(): ReminderSettings?
    @Query("DELETE FROM recurring_allocations WHERE ruleId = :id") abstract suspend fun clearRecurringAllocations(id: String)
    @Query("DELETE FROM installments WHERE id IN (:ids)") abstract suspend fun deleteInstallments(ids: List<String>)
    @Query("DELETE FROM debt_categories") abstract suspend fun clearDefaultDebtCategories()
    @Upsert abstract suspend fun putProfile(v: Profile)
    @Upsert abstract suspend fun putAccounts(v: List<Account>)
    @Upsert abstract suspend fun putEntries(v: List<Entry>)
    @Upsert abstract suspend fun putBudgets(v: List<Budget>)
    @Upsert abstract suspend fun putCategoryBudgets(v:List<CategoryBudget>)
    @Upsert abstract suspend fun putGoals(v: List<Goal>)
    @Upsert abstract suspend fun putDebts(v: List<Debt>)
    @Upsert abstract suspend fun putInstallments(v: List<Installment>)
    @Upsert abstract suspend fun putAudits(v: List<Audit>)
    @Upsert abstract suspend fun putIncomePlans(v: List<IncomePlan>)
    @Upsert abstract suspend fun putDebtCategories(v: List<DebtCategory>)
    @Upsert abstract suspend fun putRecurringRules(v: List<RecurringRule>)
    @Upsert abstract suspend fun putRecurringAllocations(v: List<RecurringAllocation>)
    @Upsert abstract suspend fun putRecurringOccurrences(v: List<RecurringOccurrence>)
    @Upsert abstract suspend fun putReminder(v: ReminderSettings)
    @Transaction open suspend fun read(): Ledger = Ledger(profile() ?: Profile(), accounts(), entries(), budgets(), goals(), debts(), installments(), audits(), incomePlans(), debtCategories(), recurringRules(), recurringAllocations(), recurringOccurrences(), reminderSettings() ?: ReminderSettings(),categoryBudgets())
    @Transaction open suspend fun write(s: Ledger) {
        putAccounts(s.accounts); putEntries(s.entries); putBudgets(s.budgets); putGoals(s.goals)
        putDebts(s.debts); putInstallments(s.installments); putAudits(s.audits); putIncomePlans(s.incomePlans); putDebtCategories(s.debtCategories); putProfile(s.profile)
        putRecurringRules(s.recurringRules); putRecurringAllocations(s.recurringAllocations); putRecurringOccurrences(s.recurringOccurrences); putReminder(s.reminder)
        putCategoryBudgets(s.categoryBudgets)
    }
}
@Database(entities = [Profile::class, Account::class, Entry::class, Budget::class, CategoryBudget::class, Goal::class, Debt::class, Installment::class, Audit::class, IncomePlan::class, DebtCategory::class, RecurringRule::class, RecurringAllocation::class, RecurringOccurrence::class, ReminderSettings::class], version = 5, exportSchema = true)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN saved INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN incomePlanId TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN incomePlanMonth TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS income_plans (accountId TEXT NOT NULL, amount INTEGER NOT NULL, day INTEGER NOT NULL, source TEXT NOT NULL, startMonth TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(accountId))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN categoryId TEXT")
                db.execSQL("ALTER TABLE debts ADD COLUMN scheduleType TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE installments ADD COLUMN scheduledAmount INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN repaymentId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS debt_categories (id TEXT NOT NULL, name TEXT NOT NULL, archived INTEGER NOT NULL, PRIMARY KEY(id))")
                defaultDebtCategories().forEach { db.execSQL("INSERT INTO debt_categories (id, name, archived) VALUES (?, ?, 0)", arrayOf(it.id, it.name)) }
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN recurringOccurrenceId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_rules (id TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, frequency TEXT NOT NULL, day INTEGER NOT NULL, time TEXT NOT NULL, startDate TEXT NOT NULL, category TEXT NOT NULL, bucket TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_allocations (ruleId TEXT NOT NULL, accountId TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(ruleId, accountId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_occurrences (id TEXT NOT NULL, ruleId TEXT NOT NULL, date TEXT NOT NULL, amount INTEGER NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_settings (id INTEGER NOT NULL, enabled INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, categories TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_4_5=object:Migration(4,5) {
            override fun migrate(db:SupportSQLiteDatabase) {
                listOf("entries","recurring_rules").forEach{table->
                    db.execSQL("ALTER TABLE $table ADD COLUMN categoryGroupId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN subcategoryId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN necessity TEXT NOT NULL DEFAULT 'UNDECIDED'")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS category_budgets (month TEXT NOT NULL, categoryGroupId TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(month, categoryGroupId))")
            }
        }
        fun open(context: Context, demo: Boolean): WalletDatabase = Room.databaseBuilder(
            context.applicationContext, WalletDatabase::class.java, if (demo) "campus_demo.db" else "campus_wallet.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,MIGRATION_4_5).enableMultiInstanceInvalidation().build() // No destructive migration, deletion or silent recovery.
    }
}

class WalletRepository(val db: WalletDatabase) {
    private val dao = db.walletDao()
    val ledger: Flow<Ledger> = dao.watchProfile().map { dao.read().also { Rules.validate(it) } }
    suspend fun prepare(demo: Boolean) = db.withTransaction {
        if (dao.profile() == null) dao.write(if (demo) demoLedger() else Ledger(debtCategories = defaultDebtCategories()))
        Rules.validate(dao.read())
        // Old confirmation-based plans start automatic bookkeeping only from tomorrow; no retroactive cash changes.
        val state = dao.read()
        val oldPlans = state.incomePlans.filter { it.enabled }
        if (oldPlans.isNotEmpty()) {
            val converted = oldPlans.filter { p -> state.recurringRules.none { it.id == "legacy-auto-${p.accountId}" } }.map { p ->
                RecurringRule(id="legacy-auto-${p.accountId}", name=p.source, kind="INCOME", amount=p.amount, day=p.day,
                    startDate=maxOf(today().plusDays(1), YearMonth.parse(p.startMonth).atDay(1)).toString(), category=p.source, bucket="FREE")
            }
            val next = state.copy(incomePlans=state.incomePlans.map { it.copy(enabled=false) },
                recurringRules=state.recurringRules+converted,
                recurringAllocations=state.recurringAllocations+converted.map { r -> RecurringAllocation(r.id, r.id.removePrefix("legacy-auto-"), r.amount) },
                profile=state.profile.copy(revision=maxOf(System.currentTimeMillis(),state.profile.revision+1)),
                audits=state.audits+Audit(entityId="profile",action="升级固定收入计划",before="手动确认到账",after="从明日起自动记账",reason="保留原流水，不补记历史月份"))
            Rules.validate(next); dao.write(next)
        }
    }
    private suspend fun change(id: String, action: String, reason: String, transform: (Ledger) -> Ledger) = db.withTransaction {
        val before = dao.read().also { Rules.validate(it) }
        val next = transform(before).also { Rules.validate(it) }
        val removed = before.installments.filter { row -> next.installments.none { it.id == row.id } }
        require(removed.none { row -> before.entries.any { it.installmentId == row.id } }) { "曾登记过还款的期次不能删除，请保留历史记录" }
        val audit = Audit(entityId = id, action = action, before = describe(before, id), after = describe(next, id), reason = reason)
        if (removed.isNotEmpty()) dao.deleteInstallments(removed.map { it.id })
        before.categoryBudgets.filter{cap->next.categoryBudgets.none{it.month==cap.month&&it.categoryGroupId==cap.categoryGroupId}}.forEach{dao.deleteCategoryBudget(it.month,it.categoryGroupId)}
        before.recurringRules.filter { r -> before.recurringAllocations.filter { it.ruleId == r.id }.toSet() != next.recurringAllocations.filter { it.ruleId == r.id }.toSet() }.forEach { dao.clearRecurringAllocations(it.id) }
        dao.write(next.copy(profile = next.profile.copy(revision = maxOf(System.currentTimeMillis(), before.profile.revision + 1)), audits = next.audits + audit))
    }
    private fun describe(s: Ledger, id: String): String = when {
        id == "profile" -> s.profile.toString()
        id.startsWith("category-budget:") -> s.categoryBudgets.filter{it.month==id.removePrefix("category-budget:")}.toString()
        id == "reminder" -> s.reminder.toString()
        id == "recurring" -> "已处理 ${s.recurringOccurrences.size} 次，已记账 ${s.recurringOccurrences.count{it.status=="POSTED"}} 次"
        s.recurringRules.any { it.id == id } -> "${s.recurringRules.first{it.id==id}}\n${s.recurringAllocations.filter{it.ruleId==id}}"
        s.recurringOccurrences.any { it.id == id } -> "${s.recurringOccurrences.first{it.id==id}}\n${s.entries.filter{it.recurringOccurrenceId==id}}"
        s.debts.any { it.id == id } -> "${s.debts.first { it.id == id }}\n${s.installments.filter { it.debtId == id }.sortedBy { it.number }}"
        s.installments.any { it.id == id } -> "${s.installments.first { it.id == id }}\n${s.entries.filter { it.installmentId == id }}"
        else -> (s.entries.find { it.id == id } ?: s.accounts.find { it.id == id } ?: s.budgets.find { it.month == id }
            ?: s.goals.find { it.id == id } ?: s.debtCategories.find { it.id == id } ?: s.incomePlans.find { "income-plan:${it.accountId}" == id } ?: "无").toString()
    }
    suspend fun profile(name: String) = change("profile", "修改账本", "修改本地账本名称") { it.copy(profile = it.profile.copy(name = name.trim())) }
    suspend fun account(a: Account, reason: String) = change(a.id, "保存账户", reason) { s ->
        val old = s.accounts.find { it.id == a.id }
        if (old != null && s.entries.any { it.accountId == a.id || it.toAccountId == a.id }) {
            require(a.start <= old.start) { "已有流水后，开始记账日只能向前扩展" }
        }
        require(reason.isNotBlank()) { "请填写修改或核对原因" }
        s.copy(accounts = s.accounts.filterNot { it.id == a.id } + a)
    }
    suspend fun toggleAccount(id: String) = change(id, "账户状态", "用户手动切换账户启用状态") { s ->
        s.copy(accounts = s.accounts.map { if (it.id == id) it.copy(disabledOn = if (it.disabledOn == null) today().toString() else null) else it })
    }
    suspend fun entry(e: Entry, reason: String = "手动记账", fee: Long = 0) = change(e.id, if (e.voided) "撤销交易" else "保存交易", reason) { s ->
        require(reason.isNotBlank()) { "请填写修改原因" }
        var next = if (e.voided) s.copy(entries = s.entries.map { if (it.id == e.id) e else it }) else Rules.replaceEntry(s, e)
        if (fee > 0) {
            require(e.kind == Kind.TRANSFER.name && s.entries.none { it.id == e.id }) { "手续费只能在新转账时一并登记" }
            val charge = Entry(kind = Kind.EXPENSE.name, amount = fee, accountId = e.accountId, date = e.date,
                category = "转账手续费", bucket = e.bucket, note = "转账手续费 · ${e.note}", transferId = e.id,categoryGroupId="finance",subcategoryId="finance.fees")
            next = Rules.replaceEntry(next, charge).copy(audits = next.audits + Audit(entityId = charge.id, action = "记录手续费", before = "无", after = charge.toString(), reason = "与转账一起保存，后续独立维护"))
        }
        next
    }
    suspend fun voidEntry(id: String, reason: String) = db.withTransaction {
        val s = dao.read()
        val e = s.entries.first { it.id == id }
        require(!e.voided) { "交易已撤销" }
        if (e.recurringOccurrenceId != null) { undoRecurring(e.recurringOccurrenceId, reason); return@withTransaction }
        if (e.kind != Kind.REPAYMENT.name) entry(e.copy(voided = true), reason)
        else change(e.installmentId!!, "撤销整期还款", reason) { state ->
            require(reason.isNotBlank()) { "请填写撤销原因" }
            val members = state.activeEntries.filter { it.kind == Kind.REPAYMENT.name && it.installmentId == e.installmentId }
            state.copy(entries = state.entries.map { if (it in members) it.copy(voided = true) else it },
                audits = state.audits + members.map { Audit(entityId = it.id, action = "撤销还款扣款", before = it.toString(), after = it.copy(voided = true).toString(), reason = reason) })
        }
    }
    suspend fun calibrate(id: String, target: Long, reason: String) = db.withTransaction {
        val s = dao.read()
        val delta = Math.subtractExact(target, s.balance(id))
        require(delta != 0L) { "核对余额与当前余额一致，无需校准" }
        require(reason.isNotBlank()) { "请填写校准原因" }
        entry(Entry(kind = Kind.ADJUSTMENT.name, amount = delta, accountId = id, date = today().toString(),
            category = "余额修正", note = "核对目标 ¥${Money.format(target)}；$reason"), reason)
    }
    suspend fun budget(b: Budget) = change(b.month, "调整预算", "手动月度预算，不产生现金变动") { s -> s.copy(budgets = s.budgets.filterNot { it.month == b.month } + b) }
    suspend fun categoryBudgets(month:String,caps:List<CategoryBudget>)=change("category-budget:$month","调整分类预算","细分月度总预算，不改变现金或必要／自由预算") {s->
        require(caps.all{it.month==month}){"分类预算月份不匹配"}
        s.copy(categoryBudgets=s.categoryBudgets.filterNot{it.month==month}+caps)
    }
    suspend fun goal(g: Goal) = change(g.id, "更新储蓄计划", "手动记录目标和已存金额，不重复记收入或扣款") { s -> s.copy(goals = s.goals.filterNot { it.id == g.id } + g) }
    suspend fun incomePlan(p: IncomePlan) = change("income-plan:${p.accountId}", "设置每月固定收入", "计划不会自动增加余额，历史到账记录保持不变") { s ->
        s.copy(incomePlans = s.incomePlans.filterNot { it.accountId == p.accountId } + p)
    }
    suspend fun receiveIncome(accountId: String, month: String, amount: Long, date: String) = db.withTransaction {
        val s = dao.read()
        val p = s.incomePlans.firstOrNull { it.accountId == accountId } ?: error("请先设置每月固定收入")
        require(p.enabled && month >= p.startMonth) { "该月份未启用固定收入计划" }
        require(s.incomeReceipt(accountId, month) == null) { "这个月已经登记到账，请在账本中修改原记录" }
        entry(Entry(kind = Kind.INCOME.name, amount = amount, accountId = accountId, date = date,
            category = p.source, note = "$month 固定收入 · 已确认实际到账", incomePlanId = accountId, incomePlanMonth = month))
    }
    suspend fun debtCategory(c: DebtCategory) = change(c.id, "设置欠款种类", "自定义种类；停用后保留已有负债") { s ->
        s.copy(debtCategories = s.debtCategories.filterNot { it.id == c.id } + c)
    }
    suspend fun recurring(rule: RecurringRule, allocations: List<RecurringAllocation>) = change(rule.id,"设置固定收支","从开始日期自动记账，历史期次保持不变") { s ->
        require(allocations.all { it.ruleId == rule.id }) { "账户分摊与固定计划不匹配" }
        val old=s.recurringRules.find{it.id==rule.id}
        val effective=if(old?.enabled==false&&rule.enabled)rule.copy(startDate=maxOf(rule.startDate,today().toString())) else rule
        s.copy(recurringRules=s.recurringRules.filterNot { it.id == rule.id }+effective,
            recurringAllocations=s.recurringAllocations.filterNot { it.ruleId == rule.id }+allocations)
    }
    suspend fun reminder(settings: ReminderSettings) = change("reminder","设置记账提醒","每天所选时间段内提醒一次") { it.copy(reminder=settings) }
    private fun postOccurrence(s: Ledger, rule: RecurringRule, date: LocalDate): Ledger {
        val id="${rule.id}@$date"
        val splits=s.recurringAllocations.filter { it.ruleId == rule.id }
        val unavailable=splits.any { allocation -> s.accounts.first { it.id == allocation.accountId }.let { date.toString()<it.start || it.disabledOn?.let { disabled -> date.toString()>=disabled } == true } }
        val occurrence=RecurringOccurrence(id,rule.id,date.toString(),rule.amount,rule.kind,
            if(unavailable)"SKIPPED" else "POSTED",if(unavailable)"账户不可用，请核对后手动补记" else "按固定计划自动记账")
        val entries=if(unavailable)emptyList() else splits.map { a -> Entry(kind=rule.kind,amount=a.amount,accountId=a.accountId,date=date.toString(),
            category=rule.category,bucket=rule.bucket,note="${rule.name} · 自动记账",recurringOccurrenceId=id,
            categoryGroupId=rule.categoryGroupId,subcategoryId=rule.subcategoryId,necessity=rule.necessity) }
        return s.copy(entries=s.entries+entries,recurringOccurrences=s.recurringOccurrences+occurrence)
    }
    suspend fun postDue(now: ZonedDateTime = ZonedDateTime.now(financeZone)): Int = db.withTransaction {
        val pending=pendingRecurring(dao.read(),now)
        if(pending.isNotEmpty()) change("recurring","固定收支自动记账","按北京时间到期处理；已撤销期次不重复生成") { before ->
            pending.fold(before) { current, (rule,date) -> postOccurrence(current,rule,date) }
        }
        pending.size
    }
    suspend fun undoRecurring(id: String, reason: String = "撤销本次自动记账") = change(id,"撤销固定记账",reason) { s ->
        val occurrence=s.recurringOccurrences.first { it.id == id }
        require(occurrence.status == "POSTED") { "该期次不在已记账状态" }
        val rows=s.entries.filter { it.recurringOccurrenceId == id }
        require(rows.none { s.refunds(it.id)>0 }) { "已有退款，请先核对并撤销退款记录，再撤销固定消费" }
        s.copy(entries=s.entries.map { if(it.recurringOccurrenceId == id)it.copy(voided=true) else it },
            recurringOccurrences=s.recurringOccurrences.map { if(it.id == id)it.copy(status="UNDONE",reason=reason) else it })
    }
    suspend fun debt(d: Debt, rows: List<Installment>) = change(d.id, "保存负债", "用户设置还款计划；不自动计算利息或扣款") { s ->
        val old = s.debts.find { it.id == d.id }
        require(rows.all { it.debtId == d.id }) { "还款计划与负债不匹配" }
        if (d.categoryId != old?.categoryId) require(s.debtCategories.any { it.id == d.categoryId && !it.archived }) { "请选择启用中的欠款种类" }
        if (old != null) {
            val oldRows = s.installments.filter { it.debtId == d.id }
            if (old.scheduleType == "LEGACY") require(d.scheduleType == "LEGACY" && d.principal == old.principal && rows.toSet() == oldRows.toSet()) { "旧版记录保留借款和费用明细，可修改名称、种类和备注" }
            else require(d.scheduleType != "LEGACY") { "自定还款不能变更为旧版明细" }
            oldRows.filter { row -> s.entries.any { it.installmentId == row.id } }.forEach { row ->
                require(rows.any { it == row }) { "第 ${row.number} 期曾登记过还款，不能修改金额、日期或删除；其余期次可调整" }
            }
        }
        s.copy(debts = s.debts.filterNot { it.id == d.id } + d, installments = s.installments.filterNot { it.debtId == d.id } + rows)
    }
    suspend fun repay(row: Installment, accountId: String, date: String) = repay(row, listOf(accountId to row.due), date)
    suspend fun repay(row: Installment, allocations: List<Pair<String, Long>>, date: String) = change(row.id, "登记实际还款", "各账户扣款一起保存，不重复计入日常消费") { s ->
        require(s.installments.any { it == row }) { "还款计划已变化，请重新打开核对" }
        require(s.payments(row).isEmpty()) { "这一期已经还款，请勿重复登记" }
        require(allocations.isNotEmpty() && allocations.map { it.first }.distinct().size == allocations.size) { "请选择不同的付款账户" }
        require(allocations.all { it.second in 1..MAX_MONEY } && allocations.map { it.second }.exactSum() == row.due) { "各账户扣款合计须等于当期应还金额" }
        val d = s.debts.first { it.id == row.debtId }
        val repaymentId = newId()
        val payments = allocations.map { (account, amount) ->
            require(s.accounts.any { it.id == account }) { "付款账户不存在" }
            Entry(kind = Kind.REPAYMENT.name, amount = amount, accountId = account, date = date, installmentId = row.id,
                category = "负债还款", note = "${d.name} · 第 ${row.number} 期${if (allocations.size > 1) " · 组合还款" else ""}", repaymentId = repaymentId).also { Rules.checkEnabled(s, it) }
        }
        s.copy(entries = s.entries + payments, audits = s.audits + payments.map { Audit(entityId = it.id, action = "还款账户扣款", before = "无", after = it.toString(), reason = "与第 ${row.number} 期整笔还款一起保存") })
    }
    suspend fun restore(restored: Ledger, demo: Boolean) = db.withTransaction {
        val s = dao.read()
        require(s.accounts.isEmpty() && s.entries.isEmpty() && s.budgets.isEmpty() && s.categoryBudgets.isEmpty() && s.goals.isEmpty() && s.debts.isEmpty() && s.installments.isEmpty() && s.incomePlans.isEmpty() && s.recurringRules.isEmpty() && s.recurringOccurrences.isEmpty()) { "为保护已有数据，只能向空账本恢复备份" }
        require(s.debtCategories.isEmpty() || s.debtCategories.toSet() == defaultDebtCategories().toSet()) { "账本已有自定义欠款种类，请使用未设置数据的空账本恢复" }
        val next = restored.copy(profile = restored.profile.copy(demo = demo, revision = System.currentTimeMillis()),
            audits = s.audits + restored.audits + Audit(entityId = "profile", action = "恢复备份", before = "空账本", after = restored.profile.name, reason = "用户选择本地备份文件"))
        Rules.validate(next); dao.clearDefaultDebtCategories(); dao.write(next)
    }
}

fun demoLedger(): Ledger {
    val now = today()
    val month = YearMonth.from(now)
    val start = month.minusMonths(1).atDay(1).toString()
    val accounts = listOf(Account(id="wechat", name="微信钱包", type="微信", opening=120000, start=start),
        Account(id="alipay", name="支付宝", type="支付宝", opening=80000, start=start),
        Account(id="bank", name="招商银行卡", type="银行卡", opening=90000, start=start),
        Account(id="cash", name="随身现金", type="现金", opening=20000, start=start))
    val dates = listOf(now.minusDays(2).coerceAtLeast(month.atDay(1)), now.minusDays(1).coerceAtLeast(month.atDay(1)), now)
    val entries = listOf(
        Entry(id="living", kind="INCOME", amount=600000, accountId="bank", date=month.atDay(1).toString(), category="父母生活费", note="学期生活费，只记一次实际到账"),
        Entry(id="tuition", kind="EXPENSE", amount=600000, accountId="bank", date=start, category="学习", bucket="NECESSARY", note="学期课程与教材"),
        Entry(id="shopping", kind="EXPENSE", amount=50000, accountId="alipay", date=dates[0].toString(), category="购物", bucket="FREE", note="换季衣物"),
        Entry(id="food", kind="EXPENSE", amount=12400, accountId="wechat", date=dates[1].toString(), category="餐饮", bucket="NECESSARY", note="食堂与朋友聚餐"),
        Entry(id="transport", kind="EXPENSE", amount=2600, accountId="wechat", date=now.toString(), category="交通", bucket="NECESSARY", note="周末出行"),
        Entry(id="refund", kind="REFUND", amount=5000, accountId="alipay", date=now.toString(), originalId="shopping", note="一件退货已到账"))
    val debt = Debt(id="phone", name="手机分期", principal=100000, purchaseDate=start)
    val rows = listOf(Installment(id="p1", debtId="phone", number=1, date=now.plusDays(5).toString(), principal=34000, fee=1000),
        Installment(id="p2", debtId="phone", number=2, date=now.plusMonths(1).plusDays(5).toString(), principal=33000, fee=1000),
        Installment(id="p3", debtId="phone", number=3, date=now.plusMonths(2).plusDays(5).toString(), principal=33000, fee=1000))
    return Ledger(Profile(name="小林的校园账本", demo=true), accounts, entries,
        listOf(Budget(month.toString(), total=200000, necessary=130000, free=50000)),
        listOf(Goal(name="我的第一台电脑", target=600000, date=now.plusMonths(6).toString(), saved=80000),
            Goal(name="毕业旅行", target=300000, date=now.plusMonths(9).toString(), saved=0)), listOf(debt), rows, debtCategories=defaultDebtCategories())
}
