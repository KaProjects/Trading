package org.kaleta.trader.data

class Asset(var ticker: String, var price: String, var quantity: String) {

    constructor() : this("", "", "")
}