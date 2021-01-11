package org.kaleta.trader.data

class Opportunity(var ticker: String, var min_cci: String, var min_macd: String, var min_diff: String, var min_price: String) {

    constructor() : this("","","","", "")
}