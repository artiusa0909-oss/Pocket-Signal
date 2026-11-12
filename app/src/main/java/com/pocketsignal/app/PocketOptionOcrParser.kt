package com.pocketsignal.app

data class OcrTextLine(val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int)
data class OcrMarketSnapshot(val asset:String?,val priceText:String?)

/** Reads the asset label and an unambiguous numeric label on the chart's right price axis. */
object PocketOptionOcrParser {
    private val assetPattern=Regex("(?i)\\b([A-Z]{3})\\s*/\\s*([A-Z]{3})(\\s*OTC)?\\b")
    private val pricePattern=Regex("(?<![\\d.,])\\d{1,6}[.,]\\d{2,8}(?![\\d.,%])")
    private val excluded=listOf("balance","profit","payout","收益","余额","прибыл","баланс","выплат","инвести","investment","amount","сумм","deposit","withdraw","депозит","вывод")
    fun parse(lines:List<OcrTextLine>,width:Int,height:Int):OcrMarketSnapshot {
        if(width<=0||height<=0)return OcrMarketSnapshot(null,null)
        val asset=lines.asSequence().mapNotNull{assetPattern.find(it.text)}.map{m->
            m.groupValues[1].uppercase()+"/"+m.groupValues[2].uppercase()+if(m.groupValues[3].isNotBlank())" OTC" else ""
        }.firstOrNull()
        val prices=lines.asSequence().filter{l->
            val x=(l.left+l.right)/2.0; val y=(l.top+l.bottom)/2.0; val t=l.text.lowercase()
            x>=width*0.68&&y in (height*0.16)..(height*0.82)&&excluded.none{t.contains(it)}
        }.flatMap{pricePattern.findAll(it.text).map{m->m.value.replace(',','.')}}.distinct().toList()
        return OcrMarketSnapshot(asset,prices.singleOrNull()?.takeIf{it.toDoubleOrNull()?.let{n->n.isFinite()&&n>0.0}==true})
    }
}
