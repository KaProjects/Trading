package org.kaleta.trader.data

class Opportunity(var company: Company, var cciMin: String, var macdMin: String, var diffMin: String, var priceMin: String) {

    constructor() : this(Company("","","","","","",""),"","","", "")
}