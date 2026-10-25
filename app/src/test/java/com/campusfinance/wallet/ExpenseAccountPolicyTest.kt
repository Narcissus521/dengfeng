package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test

class ExpenseAccountPolicyTest {
    private val first=Account("first","微信","微信",10000,"2026-01-01")
    private val last=Account("last","银行卡","银行卡",10000,"2026-01-01")
    private val date="2026-10-23"
    @Test fun preferredAccountWinsOverTheFirstAccount(){
        assertEquals("last",ExpenseAccountPolicy.choose(listOf(first,last),"last",date))
    }
    @Test fun missingPreferenceAndRemovedAccountFallBackToAnAvailableAccount(){
        assertEquals("first",ExpenseAccountPolicy.choose(listOf(first,last),null,date))
        assertEquals("first",ExpenseAccountPolicy.choose(listOf(first,last),"removed",date))
    }
    @Test fun stoppedAccountIsNeverAutomaticallyChosen(){
        assertEquals("first",ExpenseAccountPolicy.choose(listOf(first,last.copy(disabledOn=date)),"last",date))
    }
    @Test fun accountThatHasNotStartedDoesNotBecomeTheDefault(){
        assertEquals("first",ExpenseAccountPolicy.choose(listOf(first,last.copy(start="2027-01-01")),"last",date))
    }
    @Test fun fallbackSkipsStoppedAndNotYetStartedAccounts(){
        assertEquals("last",ExpenseAccountPolicy.choose(listOf(first.copy(disabledOn=date),first.copy(id="future",start="2027-01-01"),last),null,date))
    }
    @Test fun emptyOrUnavailableAccountsDoNotCreateAnInvalidSelection(){
        assertEquals("",ExpenseAccountPolicy.choose(emptyList(),"last",date))
        assertEquals("",ExpenseAccountPolicy.choose(listOf(last.copy(disabledOn=date)),"last",date))
    }
    @Test fun chosenTransactionDateControlsWhetherAnAccountHasStarted(){
        assertEquals("",ExpenseAccountPolicy.choose(listOf(last),"last","2025-12-31"))
        assertEquals("last",ExpenseAccountPolicy.choose(listOf(last),"last","2026-01-01"))
    }
}
