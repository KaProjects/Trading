package org.kaleta.trader.listener

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

abstract class AbstractChildEventListener : ChildEventListener {

    override fun onCancelled(p0: DatabaseError) {
        error(p0.message)
    }

    override fun onChildMoved(p0: DataSnapshot, p1: String?) {
        // not needed
    }
}