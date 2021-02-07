package org.kaleta.trader

import com.google.firebase.database.FirebaseDatabase
import org.kaleta.trader.data.Asset
import org.kaleta.trader.data.Company
import org.kaleta.trader.data.Log
import org.kaleta.trader.data.Opportunity
import java.util.*
import kotlin.collections.HashMap


object DataSource {

    val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    const val companyPath = "company/"
    val companyReference = database.getReference(companyPath)
    var companyMap: MutableMap<String, Company> = Collections.synchronizedMap(HashMap())

    const val opportunityPath = "opportunity/"
    val opportunityReference = database.getReference(opportunityPath)
    var opportunityMap: MutableMap<String, Opportunity> = Collections.synchronizedMap(HashMap())

    const val logPath = "log/"
    val logReference = database.getReference(logPath)
    var logMap: MutableMap<String, Log> = Collections.synchronizedMap(HashMap())
    var logsLoaded = false

    const val assetPath = "asset/"
    val assetReference = database.getReference(assetPath)
    var assetMap: MutableMap<String, Asset> = Collections.synchronizedMap(HashMap())

    init {

    }
}