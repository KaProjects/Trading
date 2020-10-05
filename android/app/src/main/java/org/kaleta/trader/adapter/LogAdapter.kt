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
import java.math.BigDecimal

class LogAdapter(a:String): RecyclerView.Adapter<LogAdapter.ViewHolder>(), ValueEventListener {

    private val logs: MutableList<Log> =
        DataSource.logList

    constructor() :this("") {
        DataSource.logReference.addValueEventListener(this);
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

        var time: TextView = itemView.findViewById(R.id.time)
        var date: TextView = itemView.findViewById(R.id.date)
        var action: TextView = itemView.findViewById(R.id.action)
        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var price: TextView = itemView.findViewById(R.id.price)
        var condition: TextView = itemView.findViewById(R.id.condition)
        var signal: TextView = itemView.findViewById(R.id.signal)

        fun bind(log: Log) {
            time.text = timeFormatter(log.time)
            date.text = dateFormatter(log.time)
//            action.text = if (log.condition.toBigDecimal().toInt() > 0) {"Sell"} else {"Buy"}
            ticker.text = log.ticker
            price.text = priceFormatter(log.price)
//            condition.text = cciFormatter(log.condition)
//            signal.text = cciFormatter(log.signal)
        }
        private fun timeFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                origin.split("T")[1].split("Z")[0].substring(0,5)
            }
        }
        private fun dateFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                origin.split("T")[0].substring(2)
            }
        }

        private fun priceFormatter(origin: String): String {
            return if (origin == "") { origin } else { origin + "$" }
        }

        private fun cciFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                BigDecimal(origin).setScale(2, BigDecimal.ROUND_HALF_DOWN).toString()
            }
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }

    override fun onDataChange(p0: DataSnapshot) {
        this.notifyDataSetChanged();
    }
}