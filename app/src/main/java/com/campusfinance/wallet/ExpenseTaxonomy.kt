package com.campusfinance.wallet

data class ExpenseLeaf(val id:String,val name:String)
data class ExpenseGroup(val id:String,val name:String,val children:List<ExpenseLeaf>,val guide:String) {
    val fallback get()=children.lastOrNull() ?: ExpenseLeaf("","")
}
data class ExpenseCategory(val group:ExpenseGroup,val leaf:ExpenseLeaf) {
    val label get()=if(group.children.isEmpty())group.name else "${group.name} / ${leaf.name}"
}
enum class Necessity(val label:String) { NECESSARY("必要消费"), NONESSENTIAL("非必要消费"), UNDECIDED("未判断") }

/** Versioned fixed catalogue: identifiers are stable; no user-defined taxonomy is stored. */
object ExpenseTaxonomy {
    private fun group(id:String,name:String,guide:String,vararg children:Pair<String,String>)=
        ExpenseGroup(id,name,children.map{ExpenseLeaf("$id.${it.first}",it.second)},guide)
    private val legacyGroups=listOf(
        group("food","餐饮食品","自用的食物和饮料归这里；以请客为主要目的的聚餐归人情社交。",
            "canteen" to "食堂","takeaway" to "外卖","restaurant" to "餐馆","ingredients" to "食材","fruit" to "水果","snacks" to "零食饮料","other" to "其他餐饮"),
        group("household","生活日用","日常洗护、护肤化妆及清洁等实物用品；理发美容等服务归生活服务，药品归医疗健康。",
            "personalcare" to "洗护用品","cleaning" to "清洁用品","paper" to "纸品耗材","misc" to "生活杂物","other" to "其他日用"),
        group("clothing","服饰鞋包","自用衣服鞋包及配饰；主要用于赠送的物品归人情社交／礼物。",
            "clothes" to "服装","shoes" to "鞋靴","bags" to "箱包","accessories" to "配饰","other" to "其他服饰"),
        group("education","学习教育","书籍、学习用品、报名及课程费用；通用软件工具归数码电子，游戏订阅归休闲娱乐。",
            "books" to "教材书籍","printing" to "文具打印","exams" to "考试报名","training" to "培训课程","schoolfees" to "学杂费","other" to "其他教育"),
        group("digital","数码电子","设备、配件、通用软件订阅与数码维修；娱乐内容订阅归休闲娱乐。",
            "devices" to "电子设备","accessories" to "数码配件","software" to "软件服务","repair" to "设备维修","other" to "其他数码"),
        group("transport","交通出行","按实际出行用途记录；旅游中的交通仍归这里，住宿归居住通讯。",
            "transit" to "公交地铁","taxi" to "打车","bicycle" to "共享单车","longdistance" to "长途交通","other" to "其他交通"),
        group("housing","居住通讯","住宿、水电、话费和网络；通过何种平台付款不影响分类。",
            "rent" to "住宿房租","utilities" to "水电燃气","phone" to "手机话费","internet" to "网络费用","other" to "其他居住"),
        group("leisure","休闲娱乐","游戏、娱乐内容、健身和休闲门票等；运动服饰归服饰鞋包，治疗检查归医疗健康。",
            "game" to "游戏","media" to "影视音乐","sports" to "运动健身","travel" to "旅游休闲","other" to "其他娱乐"),
        group("social","人情社交","以人情往来为主要目的时优先归这里；普通自用消费仍按物品或服务用途分类。",
            "gift" to "礼物","cashgift" to "礼金","hosting" to "请客","activity" to "社交活动","other" to "其他人情"),
        group("health","医疗健康","医疗检查、药物和医疗用品；日常护理实物归生活日用，美容服务归生活服务。",
            "outpatient" to "门诊检查","medicine" to "药品","checkup" to "体检","supplies" to "医疗用品","other" to "其他医疗"),
        group("services","生活服务","记录支付的服务费用；数码设备维修归数码电子／设备维修。",
            "beauty" to "理发美容","laundry" to "洗衣","delivery" to "快递寄件","repair" to "物品维修","other" to "其他服务"),
        group("finance","金融费用","仅记录已按原业务规则单独记为支出的费用或利息，借款本金和还款不在此重复记账。",
            "fees" to "手续费","interest" to "借款利息","other" to "其他金融费用"),
        group("other","其他支出","确实无法判断用途时使用未分类选项；之后可在交易详情中重新归类。",
            "charity" to "公益捐赠","unclassified" to "其他未分类支出")
    )
    // Old child identifiers are retained only to read existing records and backups.
    val legacyLeaves get()=legacyGroups.flatMap{it.children}
    val groups=legacyGroups.dropLast(1)+listOf(
        ExpenseGroup("charity","公益捐赠",emptyList(),"用于公益项目、慈善机构等捐赠；赠送亲友的礼物仍归人情社交。"),
        legacyGroups.last().copy(children=emptyList()))
    val analysisLeaves get()=groups.flatMap{g->g.children.ifEmpty{listOf(ExpenseLeaf("${g.id}.direct",g.name))}}
    val default get()=ExpenseCategory(groups.last(),groups.last().fallback)
    val legacyNames=setOf("餐饮","购物","衣服","美护","交通","学习","通讯","居住","娱乐","健康","其他","转账手续费")
    fun explicit(groupId:String,leafId:String):ExpenseCategory? {
        if(leafId.isEmpty())return groups.find{it.id==groupId}?.let{ExpenseCategory(it,it.fallback)}
        val old=legacyGroups.find{it.id==groupId} ?: return null
        val leaf=old.children.find{it.id==leafId} ?: return null
        val group=groups.first{it.id==if(leafId=="other.charity")"charity" else groupId}
        return ExpenseCategory(group,leaf)
    }
    fun fromLegacy(name:String):ExpenseCategory {
        groups.find{it.name==name}?.let{return ExpenseCategory(it,it.fallback)}
        val target=when(name) {
            "餐饮"->"food";"衣服"->"clothing";"交通"->"transport";"学习"->"education"
            "通讯","居住"->"housing";"娱乐"->"leisure";"健康"->"health";"转账手续费"->"finance"
            else->"other"
        }
        val old=legacyGroups.first{it.id==target}
        val leaf=when(name){"衣服","转账手续费"->old.children.first();else->old.fallback}
        return ExpenseCategory(groups.first{it.id==target},leaf)
    }
    fun resolve(groupId:String,leafId:String,legacy:String)=explicit(groupId,leafId) ?: fromLegacy(legacy)
    fun validate(groupId:String,leafId:String,necessity:String) {
        require(necessity in Necessity.entries.map{it.name}){"消费必要性无效"}
        require((groupId.isEmpty()&&leafId.isEmpty())||explicit(groupId,leafId)!=null){"消费分类无效，请重新选择"}
    }
    fun reminderGroupId(name:String):String?=groups.find{it.name==name}?.id ?: if(name in legacyNames)fromLegacy(name).group.id else null
}
fun Ledger.expenseCategory(e:Entry):ExpenseCategory {
    val source=if(e.kind=="REFUND")entries.firstOrNull{it.id==e.originalId} ?: e else e
    return ExpenseTaxonomy.resolve(source.categoryGroupId,source.subcategoryId,source.category)
}
fun Ledger.necessityOf(e:Entry):String=(if(e.kind=="REFUND")entries.firstOrNull{it.id==e.originalId} ?: e else e).necessity
