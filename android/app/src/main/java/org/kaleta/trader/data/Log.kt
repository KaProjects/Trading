package org.kaleta.trader.data

data class Log(val ticker: String, var price: String, var signal: String, var condition: String, var time: String) {

    constructor() : this("","", "", "", "")
}