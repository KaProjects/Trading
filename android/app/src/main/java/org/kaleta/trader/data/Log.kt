package org.kaleta.trader.data

data class Log(var type: String, var ticker: String, var time: String,
               var price: String, var edge_price: String,
               var cci: String, var edge_cci: String,
               var macd: String, var edge_macd: String,
               var diff: String, var edge_diff: String,
               var id: String ) {

    constructor() : this("","", "", "", "", "", "", "", "", "", "","")
}