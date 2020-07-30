package org.kaleta.trader.data

import com.google.firebase.database.*
import java.util.*
import kotlin.collections.ArrayList


object DataSource {
    val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    const val refAlertCCI45Path = "alert/cci45/"
    val refAlertsCCI45 = database.getReference(refAlertCCI45Path)

    const val refCompaniesPath = "company/"
    val refCompanies = database.getReference(refCompaniesPath)
    var companies: MutableList<Company> = Collections.synchronizedList(ArrayList())

    const val refLogsPath = "log/"
    val refLogs = database.getReference(refLogsPath)
    val logs: MutableList<Log> = ArrayList()

    init {
        refCompanies.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.children.count() == 0) {
                    createCompany("BA")
                    createCompany("AAL")
                    createCompany("DAL")
                    createCompany("UAL")
                    createCompany("CCL")
//                    createCompany("RCL")
//                    createCompany("NCLH")
//                    createCompany("MGM")
//                    createCompany("KSS")
                } else {
                    companies.clear()
                    for (postSnapshot in dataSnapshot.children) {
                        val company: Company = postSnapshot.getValue(Company::class.java) as Company
                        company.id = postSnapshot.key!!
                        companies.add(company)
                    }
                }
            }
            private fun createCompany(ticker: String){
                val company = Company(ticker, "", "", "", "", "")
                company.id = refCompanies.push().key!!
                refCompanies.child(company.id).setValue(company)
            }
            override fun onCancelled(p0: DatabaseError) {
                println(p0.message)
            }
        } )
        refLogs.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                logs.clear()
                for (postSnapshot in dataSnapshot.children) {
                    val log: Log = postSnapshot.getValue(Log::class.java) as Log
                    logs.add(log)
                }
            }
            override fun onCancelled(p0: DatabaseError) {
                println(p0.message)
            }
        } )
    }
}