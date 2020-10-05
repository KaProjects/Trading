package org.kaleta.trader.listener

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

abstract class AbstractValueEventListener : ValueEventListener {

    override fun onCancelled(p0: DatabaseError) {
        error(p0.message)
    }
}