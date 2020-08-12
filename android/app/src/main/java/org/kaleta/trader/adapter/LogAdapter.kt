package org.kaleta.trader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.R
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Log

class LogAdapter(a:String): RecyclerView.Adapter<LogAdapter.ViewHolder>(), ValueEventListener {

    private val logs: MutableList<Log> =
        DataSource.logs

    constructor() :this("") {
        DataSource.refLogs.addValueEventListener(this);
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.log_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(logs[logs.size - position - 1])
    }

    override fun getItemCount(): Int {
        return logs.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var ticker: TextView = itemView.findViewById(R.id.logTicker)
        var price: TextView = itemView.findViewById(R.id.logPrice)
        var condition: TextView = itemView.findViewById(R.id.logCondition)
        var signal: TextView = itemView.findViewById(R.id.logSignal)
        var time: TextView = itemView.findViewById(R.id.logTime)

        fun bind(log: Log) {
            ticker.text = log.ticker
            price.text = log.price
            condition.text = log.condition
            signal.text = log.signal
            time.text = log.time
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }

    override fun onDataChange(p0: DataSnapshot) {
        this.notifyDataSetChanged();
    }
}