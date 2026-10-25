package com.campusfinance.wallet

import kotlin.math.roundToInt

data class CardGridLayout(val columns:Int,val rows:Int,val column:Int=0,val row:Int=-1,val page:Int=0) {
    fun normalized(availableRows:Int=4)=copy(columns=columns.coerceIn(2,4),rows=rows.coerceIn(1,minOf(4,availableRows.coerceAtLeast(1))),
        column=column.coerceIn(0,4-columns.coerceIn(2,4)),row=if(row<0)-1 else row.coerceIn(0,availableRows.coerceAtLeast(1)-rows.coerceIn(1,minOf(4,availableRows.coerceAtLeast(1)))),page=page.coerceIn(0,12))
}
data class PlacedGridCard(val card:OverviewCard,val layout:CardGridLayout)

object CardGridPolicy {
    fun default(card:OverviewCard)=when(card) {
        OverviewCard.HOME_DEBT,OverviewCard.HOME_SAVINGS,OverviewCard.LEDGER_FIXED_EXPENSE,OverviewCard.LEDGER_FIXED_INCOME->CardGridLayout(2,1)
        OverviewCard.LEDGER_ASSETS,OverviewCard.PLAN_SAVINGS,OverviewCard.ANALYSIS_CHART->CardGridLayout(4,3)
        OverviewCard.ANALYSIS_TREND->CardGridLayout(4,2)
        OverviewCard.ANALYSIS_TOTAL->CardGridLayout(4,1)
        else->CardGridLayout(4,2)
    }
    fun pack(cards:List<OverviewCard>,saved:Map<OverviewCard,CardGridLayout>,availableRows:Int,pinned:OverviewCard?=null):List<PlacedGridCard> {
        val rows=availableRows.coerceIn(1,12)
        val occupied=mutableMapOf<Int,Array<BooleanArray>>()
        val placed=linkedMapOf<OverviewCard,CardGridLayout>()
        fun size(c:OverviewCard)=(saved[c] ?: default(c)).normalized(rows).let{it.copy(page=it.page.coerceAtMost((cards.size-1).coerceAtLeast(0)))}
        fun free(v:CardGridLayout):Boolean {
            if(v.row<0||v.row+v.rows>rows||v.column+v.columns>4)return false
            val cells=occupied[v.page] ?: return true
            return (v.row until v.row+v.rows).all{y->(v.column until v.column+v.columns).all{x->!cells[y][x]}}
        }
        fun put(c:OverviewCard,v:CardGridLayout) {
            val cells=occupied.getOrPut(v.page){Array(rows){BooleanArray(4)}}
            for(y in v.row until v.row+v.rows)for(x in v.column until v.column+v.columns)cells[y][x]=true
            placed[c]=v
        }
        if(pinned in cards){val c=pinned!!;val v=size(c);put(c,v.copy(row=v.row.coerceAtLeast(0)))}
        // Reserve unaffected anchors before finding new space for displaced cards.
        if(pinned!=null)for(c in cards.distinct())if(c !in placed){val v=size(c);if(v.row>=0&&free(v))put(c,v)}
        for(c in cards.distinct()) {
            if(c in placed)continue
            val v=size(c)
            if(pinned==null&&v.row>=0&&free(v)){put(c,v);continue}
            var spot:CardGridLayout?=null
            for(p in 0..cards.size) {
                for(y in 0..rows-v.rows) {
                    val columns=(listOf(v.column)+(0..4-v.columns)).distinct()
                    for(x in columns){val attempt=v.copy(column=x,row=y,page=p);if(free(attempt)){spot=attempt;break}}
                    if(spot!=null)break
                }
                if(spot!=null)break
            }
            put(c,checkNotNull(spot))
        }
        return cards.distinct().map{PlacedGridCard(it,placed.getValue(it))}
    }
    fun snap(initial:Int,delta:Float,step:Float,min:Int,max:Int):Int {
        if(!delta.isFinite()||!step.isFinite()||step<=0f)return initial.coerceIn(min,max.coerceAtLeast(min))
        return (initial+(delta/step).roundToInt()).coerceIn(min,max.coerceAtLeast(min))
    }
    fun move(initial:CardGridLayout,dx:Float,dy:Float,stepX:Float,stepY:Float,availableRows:Int):CardGridLayout {
        val v=initial.normalized(availableRows).let{it.copy(row=it.row.coerceAtLeast(0))}
        return v.copy(column=snap(v.column,dx,stepX,0,4-v.columns),row=snap(v.row,dy,stepY,0,availableRows.coerceAtLeast(1)-v.rows))
    }
}
