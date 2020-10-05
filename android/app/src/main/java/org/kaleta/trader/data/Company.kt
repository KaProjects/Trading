package org.kaleta.trader.data

class Company(val ticker: String, val time: String, val price: String, val cci: String, val macd: String, val signal: String, val diff: String) {

    constructor() : this("","", "", "", "", "", "")
}