package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsAndIncomeTest {
    private val now=LocalDate.of(2026,10,20)
    private fun goal(date:String="2026-12-31",target:Long=10000,saved:Long=0)=Goal(name="电脑",target=target,date=date,saved=saved)
    @Test fun usesFutureMonthsAndRoundsUpToCent(){val p=goal(saved=1).progress(now);assertEquals(2L,p.months);assertEquals(9999L,p.remaining);assertEquals(5000L,p.monthly)}
    @Test fun cannotUnderfundByRoundingDown(){val p=goal().progress(now);assertEquals(5000L,p.monthly);assertTrue(p.monthly*p.months>=p.remaining)}
    @Test fun sameMonthRequiresOneDeposit(){val p=goal("2026-10-31",saved=3000).progress(now);assertEquals(1L,p.months);assertEquals(7000L,p.monthly)}
    @Test fun deadlineTodayStillAllowsOneDeposit(){assertEquals(1L,goal("2026-10-20").progress(now).months)}
    @Test fun overdueHasNoMisleadingMonthlySuggestion(){val p=goal("2026-10-19",saved=3000).progress(now);assertTrue(p.overdue);assertEquals(0L,p.months);assertEquals(0L,p.monthly);assertEquals(7000L,p.remaining)}
    @Test fun achievedOrExceededHasZeroShortfall(){for(saved in listOf(10000L,12000L)){val p=goal(saved=saved).progress(now);assertEquals(0L,p.remaining);assertEquals(0L,p.monthly)}}
    @Test fun yearBoundaryAndLeapYear(){assertEquals(4L,goal("2027-02-28").progress(now).months);assertEquals(1L,goal("2028-02-29").progress(LocalDate.of(2028,1,31)).months)}
    @Test fun updatingSavingsReducesMonthlyAmountWithoutChangingCash(){val s=demoLedger();val g=s.goals.first();val next=s.copy(goals=listOf(g.copy(saved=g.saved+10000)));Rules.validate(next);assertEquals(s.cash(),next.cash());assertTrue(g.copy(saved=g.saved+10000).progress().monthly<g.progress().monthly)}
    @Test fun oldIntentionIsNotSaved(){assertEquals(0L,Goal(name="电脑",target=10000,date="2026-12-31",intention=8000).saved)}
    @Test fun februaryAndThirtyDayMonthsClampDate(){val p=IncomePlan("a",10000,31,"生活费","2026-01");assertEquals("2026-02-28",p.expectedDate("2026-02").toString());assertEquals("2028-02-29",p.expectedDate("2028-02").toString());assertEquals("2026-04-30",p.expectedDate("2026-04").toString());assertEquals("2026-10-31",p.expectedDate("2026-10").toString())}
    @Test fun plannedIncomeDoesNotIncreaseCash(){val s=demoLedger();val next=s.copy(incomePlans=listOf(IncomePlan("bank",200000,1,"生活费",currentMonth())));Rules.validate(next);assertEquals(s.cash(),next.cash());assertEquals(s.income(currentMonth()),next.income(currentMonth()))}
    @Test fun invalidPlanDayRejected(){val s=demoLedger();assertThrows(IllegalArgumentException::class.java){Rules.validate(s.copy(incomePlans=listOf(IncomePlan("bank",200000,32,"生活费",currentMonth()))))}}
}
