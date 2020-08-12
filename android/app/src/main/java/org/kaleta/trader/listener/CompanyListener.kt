package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Company

class CompanyListener : ValueEventListener{

    override fun onDataChange(dataSnapshot: DataSnapshot) {
        DataSource.companies.clear()
        for (postSnapshot in dataSnapshot.children) {
            val company: Company = postSnapshot.getValue(Company::class.java) as Company
            company.id = postSnapshot.key!!
            DataSource.companies.add(company)
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }
}