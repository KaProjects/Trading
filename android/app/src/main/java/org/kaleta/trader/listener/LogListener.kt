package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Log

class LogListener : AbstractValueEventListener() {

    override fun onDataChange(dataSnapshot: DataSnapshot) {
        DataSource.logList.clear()
        for (postSnapshot in dataSnapshot.children) {
            val log: Log = postSnapshot.getValue(Log::class.java) as Log
            DataSource.logList.add(log)
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }
}