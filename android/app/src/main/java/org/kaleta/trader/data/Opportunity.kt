package org.kaleta.trader.data

class Opportunity(var ticker: String, var edge_cci: String, var edge_macd: String, var edge_diff: String, var edge_price: String, var signal: String) {

    constructor() : this("","","","", "","")
}