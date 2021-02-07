package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Log

class LogStartupListener: AbstractValueEventListener() {
    override fun onDataChange(dataSnapshot: DataSnapshot) {
        if (!DataSource.logsLoaded) {
            for (data in dataSnapshot.children) {
                val log: Log = data.getValue(Log::class.java) as Log
                DataSource.logMap.put(dataSnapshot.key!!, log)
            }
            DataSource.logsLoaded = true
        }
    }
}