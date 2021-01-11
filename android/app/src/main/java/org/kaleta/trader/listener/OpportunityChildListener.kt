package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Opportunity

class OpportunityChildListener : AbstractChildEventListener() {

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        val opportunity: Opportunity = dataSnapshot.getValue(Opportunity::class.java) as Opportunity
        DataSource.opportunityMap.replace(dataSnapshot.key!!, opportunity)
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val opportunity: Opportunity = dataSnapshot.getValue(Opportunity::class.java) as Opportunity
        DataSource.opportunityMap.put(dataSnapshot.key!!, opportunity)
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        DataSource.opportunityMap.remove(dataSnapshot.key)
    }
}