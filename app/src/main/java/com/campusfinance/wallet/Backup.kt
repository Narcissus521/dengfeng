package com.campusfinance.wallet

import org.json.JSONArray
import org.json.JSONObject

object Backup {
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k,v) -> put(k, v ?: JSONObject.NULL) } }
    private fun <T> array(rows: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(encode(it)) } }
    fun encode(s: Ledger): String = obj(
        "format" to "CampusFinance", "version" to 5, "currency" to "CNY", "timezone" to "Asia/Shanghai",
        "profile" to obj("name" to s.profile.name, "demo" to s.profile.demo),
        "accounts" to array(s.accounts) { obj("id" to it.id, "name" to it.name, "type" to it.type, "opening" to it.opening, "start" to it.start, "disabledOn" to it.disabledOn) },
        "entries" to array(s.entries) { obj("id" to it.id, "kind" to it.kind, "amount" to it.amount, "accountId" to it.accountId, "date" to it.date, "category" to it.category,
            "bucket" to it.bucket, "toAccountId" to it.toAccountId, "originalId" to it.originalId, "installmentId" to it.installmentId, "note" to it.note, "voided" to it.voided, "transferId" to it.transferId,
            "incomePlanId" to it.incomePlanId, "incomePlanMonth" to it.incomePlanMonth, "repaymentId" to it.repaymentId, "recurringOccurrenceId" to it.recurringOccurrenceId, "categoryGroupId" to it.categoryGroupId,"subcategoryId" to it.subcategoryId,"necessity" to it.necessity) },
        "categoryBudgets" to array(s.categoryBudgets) { obj("month" to it.month,"categoryGroupId" to it.categoryGroupId,"amount" to it.amount) },
        "budgets" to array(s.budgets) { obj("month" to it.month, "total" to it.total, "necessary" to it.necessary, "free" to it.free) },
        "goals" to array(s.goals) { obj("id" to it.id, "name" to it.name, "target" to it.target, "date" to it.date, "intention" to it.intention, "archived" to it.archived, "saved" to it.saved) },
        "incomePlans" to array(s.incomePlans) { obj("accountId" to it.accountId, "amount" to it.amount, "day" to it.day, "source" to it.source, "startMonth" to it.startMonth, "enabled" to it.enabled) },
        "debts" to array(s.debts) { obj("id" to it.id, "name" to it.name, "principal" to it.principal, "purchaseDate" to it.purchaseDate, "note" to it.note, "categoryId" to it.categoryId, "scheduleType" to it.scheduleType) },
        "installments" to array(s.installments) { obj("id" to it.id, "debtId" to it.debtId, "number" to it.number, "date" to it.date, "principal" to it.principal, "fee" to it.fee, "scheduledAmount" to it.scheduledAmount) },
        "debtCategories" to array(s.debtCategories) { obj("id" to it.id, "name" to it.name, "archived" to it.archived) },
        "recurringRules" to array(s.recurringRules) { obj("id" to it.id,"name" to it.name,"kind" to it.kind,"amount" to it.amount,"frequency" to it.frequency,"day" to it.day,"time" to it.time,"startDate" to it.startDate,"category" to it.category,"bucket" to it.bucket,"enabled" to it.enabled,"categoryGroupId" to it.categoryGroupId,"subcategoryId" to it.subcategoryId,"necessity" to it.necessity) },
        "recurringAllocations" to array(s.recurringAllocations) { obj("ruleId" to it.ruleId,"accountId" to it.accountId,"amount" to it.amount) },
        "recurringOccurrences" to array(s.recurringOccurrences) { obj("id" to it.id,"ruleId" to it.ruleId,"date" to it.date,"amount" to it.amount,"kind" to it.kind,"status" to it.status,"reason" to it.reason) },
        "reminder" to obj("enabled" to s.reminder.enabled,"startTime" to s.reminder.startTime,"endTime" to s.reminder.endTime,"categories" to s.reminder.categories),
        "audits" to array(s.audits) { obj("id" to it.id, "entityId" to it.entityId, "action" to it.action, "before" to it.before, "after" to it.after, "reason" to it.reason, "time" to it.time) }
    ).toString(2)
    private fun JSONObject.nullable(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.integer(k: String): Long {
        val raw = get(k).toString()
        require(Regex("-?\\d+").matches(raw)) { "备份金额或整数字段不合法：$k" }
        return raw.toLong()
    }
    private fun <T> JSONObject.rows(k: String, decode: (JSONObject) -> T): List<T> {
        val a = getJSONArray(k)
        require(a.length() <= 100000) { "备份记录过多" }
        return (0 until a.length()).map { decode(a.getJSONObject(it)) }
    }
    fun decode(text: String): Ledger {
        require(text.length <= 5_000_000) { "备份超过 5 MB 限制" }
        val j = JSONObject(text)
        val version = j.integer("version")
        require(j.getString("format") == "CampusFinance" && version in 1L..5L && j.getString("currency") == "CNY" && j.getString("timezone") == "Asia/Shanghai") { "不是受支持的账本备份" }
        return Ledger(
            Profile(name=j.getJSONObject("profile").getString("name"), demo=j.getJSONObject("profile").getBoolean("demo")),
            j.rows("accounts") { Account(it.getString("id"),it.getString("name"),it.getString("type"),it.integer("opening"),it.getString("start"),it.nullable("disabledOn")) },
            j.rows("entries") { Entry(it.getString("id"),it.getString("kind"),it.integer("amount"),it.getString("accountId"),it.getString("date"),it.getString("category"),it.getString("bucket"),it.nullable("toAccountId"),it.nullable("originalId"),it.nullable("installmentId"),it.getString("note"),it.getBoolean("voided"),it.nullable("transferId"),if(version>=2L)it.nullable("incomePlanId") else null,if(version>=2L)it.nullable("incomePlanMonth") else null,if(version>=3L)it.nullable("repaymentId") else null,if(version>=4L)it.nullable("recurringOccurrenceId") else null,if(version>=5L)it.getString("categoryGroupId") else "",if(version>=5L)it.getString("subcategoryId") else "",if(version>=5L)it.getString("necessity") else Necessity.UNDECIDED.name) },
            j.rows("budgets") { Budget(it.getString("month"),it.integer("total"),it.integer("necessary"),it.integer("free")) },
            j.rows("goals") { Goal(it.getString("id"),it.getString("name"),it.integer("target"),it.getString("date"),it.integer("intention"),it.getBoolean("archived"),if(version>=2L)it.integer("saved") else 0) },
            j.rows("debts") { Debt(it.getString("id"),it.getString("name"),it.integer("principal"),it.getString("purchaseDate"),it.getString("note"),if(version>=3L)it.nullable("categoryId") else null,if(version>=3L)it.getString("scheduleType") else "LEGACY") },
            j.rows("installments") { Installment(it.getString("id"),it.getString("debtId"),it.integer("number").also { v -> require(v in 1..MAX_DEBT_INSTALLMENTS) }.toInt(),it.getString("date"),it.integer("principal"),it.integer("fee"),if(version>=3L&&!it.isNull("scheduledAmount"))it.integer("scheduledAmount") else null) },
            j.rows("audits") { Audit(it.getString("id"),it.getString("entityId"),it.getString("action"),it.getString("before"),it.getString("after"),it.getString("reason"),it.integer("time")) },
            if(version>=2L) j.rows("incomePlans") { IncomePlan(it.getString("accountId"),it.integer("amount"),it.integer("day").also { day -> require(day in 1..31) }.toInt(),it.getString("source"),it.getString("startMonth"),it.getBoolean("enabled")) } else emptyList(),
            if(version>=3L) j.rows("debtCategories") { DebtCategory(it.getString("id"),it.getString("name"),it.getBoolean("archived")) } else defaultDebtCategories(),
            if(version>=4L) j.rows("recurringRules") { RecurringRule(it.getString("id"),it.getString("name"),it.getString("kind"),it.integer("amount"),it.getString("frequency"),it.integer("day").also { n -> require(n in 1..31) }.toInt(),it.getString("time"),it.getString("startDate"),it.getString("category"),it.getString("bucket"),it.getBoolean("enabled"),if(version>=5L)it.getString("categoryGroupId") else "",if(version>=5L)it.getString("subcategoryId") else "",if(version>=5L)it.getString("necessity") else Necessity.UNDECIDED.name) } else emptyList(),
            if(version>=4L) j.rows("recurringAllocations") { RecurringAllocation(it.getString("ruleId"),it.getString("accountId"),it.integer("amount")) } else emptyList(),
            if(version>=4L) j.rows("recurringOccurrences") { RecurringOccurrence(it.getString("id"),it.getString("ruleId"),it.getString("date"),it.integer("amount"),it.getString("kind"),it.getString("status"),it.getString("reason")) } else emptyList(),
            if(version>=4L) j.getJSONObject("reminder").let { ReminderSettings(enabled=it.getBoolean("enabled"),startTime=it.getString("startTime"),endTime=it.getString("endTime"),categories=it.getString("categories")) } else ReminderSettings(),
            if(version>=5L)j.rows("categoryBudgets"){CategoryBudget(it.getString("month"),it.getString("categoryGroupId"),it.integer("amount"))} else emptyList()
        ).also { Rules.validate(it) }
    }
}
