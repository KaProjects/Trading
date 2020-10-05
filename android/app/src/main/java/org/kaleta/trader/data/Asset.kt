package org.kaleta.trader.data

class Asset(var company: Company, var price: String, var amount: String) {

    constructor() : this(Company("","","","","","",""), "", "")
}