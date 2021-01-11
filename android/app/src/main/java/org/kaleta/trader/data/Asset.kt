package org.kaleta.trader.data

class Asset(var ticker: String, var price: String, var amount: String) {

    constructor() : this("", "", "")
}