package org.kaleta.trader.listener

import android.content.Context
import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Company

class AlertDataChildListener(private val context: Context) : AbstractChildEventListener() {

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        println("Alert " + dataSnapshot.key + " changed")
    }

    /**
     * Consumes alert and update company.
     */
    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val alertData: Company = dataSnapshot.getValue(Company::class.java) as Company

        DataSource.companyReference.child(alertData.ticker).setValue(alertData)
        DataSource.alertDataReference.child(dataSnapshot.key!!).removeValue()
        Thread.sleep(100L)
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        println("Alert " + dataSnapshot.key + " removed")
    }
}