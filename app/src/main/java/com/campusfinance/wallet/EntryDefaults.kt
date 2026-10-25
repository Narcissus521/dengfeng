package com.campusfinance.wallet

import android.content.SharedPreferences

object ExpenseAccountPolicy {
    fun choose(accounts:List<Account>,remembered:String?,date:String):String {
        fun usable(a:Account)=a.disabledOn==null && a.start<=date
        return (accounts.firstOrNull{it.id==remembered&&usable(it)}
            ?: accounts.firstOrNull{usable(it)})?.id ?: ""
    }
}

/** Device preference, scoped to the ledger; only successful new manual expenses update it. */
class ExpenseAccountMemory(private val prefs:SharedPreferences) {
    fun selected(s:Ledger,date:String)=ExpenseAccountPolicy.choose(s.accounts,prefs.getString(key(s.profile.demo),null),date)
    fun rememberSaved(demo:Boolean,e:Entry,isNew:Boolean) {
        if(isNew&&e.kind=="EXPENSE"&&e.recurringOccurrenceId==null)
            prefs.edit().putString(key(demo),e.accountId).apply()
    }
    companion object {
        fun key(demo:Boolean)="last_expense_account_${if(demo) "demo" else "personal"}"
    }
}
