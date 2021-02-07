package org.kaleta.trader.data

data class Log(var type: String, var ticker: String, var price: String, var time: String,
               var cci: String, var cciMin: String,
               var macd: String, var macdMin: String,
               var diff: String, var diffMin: String,
               var id: String ) {

    constructor() : this("","", "", "", "", "", "", "", "", "", "")
}