package org.kaleta.trader

import com.google.firebase.database.*
import org.kaleta.trader.data.Asset
import org.kaleta.trader.data.Company
import org.kaleta.trader.data.Log
import java.util.*
import kotlin.collections.ArrayList


object DataSource {
    val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    const val refAlertCCI45Path = "alert/cci45/"
    val refAlertsCCI45 = database.getReference(refAlertCCI45Path)

    const val refCompanyPath = "company/"
    val refCompanies = database.getReference(refCompanyPath)
    var companies: MutableList<Company> = Collections.synchronizedList(ArrayList())

    const val refLogPath = "log/"
    val refLogs = database.getReference(refLogPath)
    val logs: MutableList<Log> = ArrayList()

    const val refAssetPath = "asset/"
    val refAssets = database.getReference(refAssetPath)
    val assets: MutableList<Asset> = ArrayList()

    init {

    }
}