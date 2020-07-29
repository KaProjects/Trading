package org.kaleta.trader.data

data class AlertCCI(val cci45: String, val price: String, val ticker: String, val time: String) {

    constructor() : this("","", "", "")

}