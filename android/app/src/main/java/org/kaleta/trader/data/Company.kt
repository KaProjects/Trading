package org.kaleta.trader.data

import com.google.firebase.database.Exclude

data class Company(var ticker: String, var price: String, var condition: String, var signal: String, var cci: String, var time: String) {

    var id: String = ""
        @Exclude
        get() = field

    constructor() : this("","", "", "", "", "")
}