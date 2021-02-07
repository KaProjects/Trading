package org.kaleta.trader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.R
import org.kaleta.trader.data.Log

class LogAdapter(a:String): RecyclerView.Adapter<LogAdapter.ViewHolder>(), ValueEventListener {

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
        val list = DataSource.logMap.values.toList().sortedByDescending { log -> log.id }
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return DataSource.logMap.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var time: TextView = itemView.findViewById(R.id.time)
        var date: TextView = itemView.findViewById(R.id.date)
        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var price: TextView = itemView.findViewById(R.id.price)

        var type: TextView = itemView.findViewById(R.id.type)


        fun bind(log: Log) {
            time.text = timeFormatter(log.time)
            date.text = dateFormatter(log.time)
            ticker.text = log.ticker
            price.text = priceFormatter(log.price)

            type.text = log.type

        }
        private fun timeFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                origin.split("T")[1].split("Z")[0].substring(0,5)
            }
        }
        private fun dateFormatter(origin: String): String {
            if (origin == "") {
                return origin
            } else {
                val date = origin.split("T")[0].split("-")
                val year = date[0].substring(2)
                val month = date[1]
                val day = date[2]
                return "$day-$month-$year"
            }
        }

        private fun priceFormatter(origin: String): String {
            return if (origin == "") { origin } else { origin + "$" }
        }
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }

    override fun onDataChange(p0: DataSnapshot) {
        this.notifyDataSetChanged();
    }
}