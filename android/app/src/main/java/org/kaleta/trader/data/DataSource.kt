package org.kaleta.trader.data

import com.google.firebase.database.*


object DataSource {
    val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    const val refAlertCCI45Path = "alert/cci45/"
    val refAlertsCCI45 = database.getReference(refAlertCCI45Path)

    const val refCompaniesPath = "company/"
    val refCompanies = database.getReference(refCompaniesPath)
    val companies: MutableList<Company> = ArrayList()

    const val refLogsPath = "log/"
    val refLogs = database.getReference(refLogsPath)
    val logs: MutableList<Log> = ArrayList()

    init {
        refCompanies.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (postSnapshot in dataSnapshot.children) {
                    val outdatedCompany = companies.find { company -> company.id == postSnapshot.key }
                    val updatedCompany: Company = postSnapshot.getValue(Company::class.java) as Company

                    if (outdatedCompany == null){
                        updatedCompany.id = postSnapshot.key!!
                        companies.add(updatedCompany)
                    } else {
                        if (outdatedCompany != updatedCompany) {
                            outdatedCompany.signal = updatedCompany.signal
                            outdatedCompany.condition = updatedCompany.condition
                            outdatedCompany.cci = updatedCompany.cci
                            outdatedCompany.id = updatedCompany.id
                            outdatedCompany.price = updatedCompany.price
                            outdatedCompany.ticker = updatedCompany.ticker
                            outdatedCompany.time = updatedCompany.time
                        }
                    }
                }
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