package org.kaleta.trader.data

import com.google.firebase.database.Exclude

class Asset(var ticker: String, var price: String, var amount: String, var current: String) {

    var id: String = ""
        @Exclude
        get() = field

    constructor() : this("", "", "", "")
}