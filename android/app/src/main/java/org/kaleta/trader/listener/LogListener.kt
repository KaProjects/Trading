package org.kaleta.trader.listener

import android.renderscript.Sampler
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Log

class LogListener : ValueEventListener {

    override fun onDataChange(dataSnapshot: DataSnapshot) {
        DataSource.logs.clear()
        for (postSnapshot in dataSnapshot.children) {
            val log: Log = postSnapshot.getValue(Log::class.java) as Log
            DataSource.logs.add(log)
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }
}