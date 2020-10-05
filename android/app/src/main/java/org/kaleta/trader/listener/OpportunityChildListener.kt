package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Opportunity

class OpportunityChildListener : AbstractChildEventListener() {

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        val opportunity: Opportunity = dataSnapshot.getValue(Opportunity::class.java) as Opportunity
        DataSource.opportunityMap.replace(opportunity.company.ticker, opportunity)
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val opportunity: Opportunity = dataSnapshot.getValue(Opportunity::class.java) as Opportunity
        DataSource.opportunityMap.put(opportunity.company.ticker, opportunity)
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        val opportunity: Opportunity = dataSnapshot.getValue(Opportunity::class.java) as Opportunity
        DataSource.opportunityMap.remove(opportunity.company.ticker)
    }
}