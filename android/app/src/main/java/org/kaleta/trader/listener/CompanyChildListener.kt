package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Company

class CompanyChildListener : AbstractChildEventListener() {

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        DataSource.companyMap.remove(dataSnapshot.key)
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val company: Company = dataSnapshot.getValue(Company::class.java) as Company
        DataSource.companyMap.put(dataSnapshot.key!!, company)
    }

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        val company: Company = dataSnapshot.getValue(Company::class.java) as Company
        DataSource.companyMap.replace(dataSnapshot.key!!, company)
    }
}