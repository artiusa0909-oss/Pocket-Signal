package com.pocketsignal.app

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/** Forms real M1 OHLC bars from repeated OCR quote observations; never fills missing minutes. */
class OcrMinuteCandleAggregator(private val maxHistory:Int=120) {
    private val completed=ArrayDeque<Candle>()
    private var active:Candle?=null
    private var activeAsset:String?=null
    @Synchronized fun addTick(asset:String,price:Double,timeMillis:Long):List<Candle> {
        if(asset.isBlank()||!price.isFinite()||price<=0.0||timeMillis<=0L)return snapshotUnsafe()
        if(activeAsset!=asset){completed.clear();active=null;activeAsset=asset}
        val minute=timeMillis/60_000L*60_000L
        val current=active
        when {
            current==null->active=Candle(minute,price,price,price,price)
            minute<current.timestampMillis->return snapshotUnsafe()
            minute==current.timestampMillis->active=current.copy(high=max(current.high,price),low=min(current.low,price),close=price)
            else->{completed.addLast(current);while(completed.size>maxHistory)completed.removeFirst();active=Candle(minute,price,price,price,price)}
        }
        return snapshotUnsafe()
    }
    @Synchronized fun snapshot():List<Candle> = snapshotUnsafe()
    @Synchronized fun reset(){completed.clear();active=null;activeAsset=null}
    private fun snapshotUnsafe():List<Candle> = completed.toList()+listOfNotNull(active)
}
