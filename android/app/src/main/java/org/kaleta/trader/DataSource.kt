package org.kaleta.trader

import com.google.firebase.database.*
import org.kaleta.trader.data.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


object DataSource {

    val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    const val alertDataPath = "alert/data/"
    val alertDataReference = database.getReference(alertDataPath)

    const val companyPath = "company/"
    val companyReference = database.getReference(companyPath)
    var companyMap: MutableMap<String, Company> = Collections.synchronizedMap(HashMap())

    const val opportunityPath = "opportunity/"
    val opportunityReference = database.getReference(opportunityPath)
    var opportunityMap: MutableMap<String, Opportunity> = Collections.synchronizedMap(HashMap())

    const val assetPath = "asset/"
    val assetReference = database.getReference(assetPath)
    var assetMap: MutableMap<String, Asset> = Collections.synchronizedMap(HashMap())

    const val logPath = "log/"
    val logReference = database.getReference(logPath)
    val logList: MutableList<Log> = ArrayList()

    init {

    }
}