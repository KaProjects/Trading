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
import org.kaleta.trader.data.Opportunity
import java.math.BigDecimal

class OpportunityAdapter(a:String) : RecyclerView.Adapter<OpportunityAdapter.ViewHolder>(), ValueEventListener {

    constructor() :this("") {
        DataSource.opportunityReference.addValueEventListener(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.opportunity_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(DataSource.opportunityMap.values.toList()[position])
    }

    override fun getItemCount(): Int {
        return DataSource.opportunityMap.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var price: TextView = itemView.findViewById(R.id.price)
        var time: TextView = itemView.findViewById(R.id.time)


        var cci: TextView = itemView.findViewById(R.id.cci)
//        var cciLabel: TextView = itemView.findViewById(R.id.cciLabel)
        var cciMin: TextView = itemView.findViewById(R.id.cciMin)
        var macd: TextView = itemView.findViewById(R.id.macd)
//        var macdLabel: TextView = itemView.findViewById(R.id.macdLabel)
        var macdMin: TextView = itemView.findViewById(R.id.macdMin)
        var diff: TextView = itemView.findViewById(R.id.diff)
//        var diffLabel: TextView = itemView.findViewById(R.id.diffLabel)
        var diffMin: TextView = itemView.findViewById(R.id.diffMin)

        fun bind(opportunity: Opportunity) {
            ticker.text = opportunity.company.ticker
            price.text = priceFormatter(opportunity.company.price)
            time.text = timeFormatter(opportunity.company.time)

            cci.text = dataFormatter(opportunity.company.cci)
            cciMin.text = dataFormatter(opportunity.cciMin)
            macd.text = dataFormatter(opportunity.company.macd)
            macdMin.text = dataFormatter(opportunity.macdMin)
            diff.text = dataFormatter(opportunity.company.diff)
            diffMin.text = dataFormatter(opportunity.diffMin)
        }

        private fun priceFormatter(origin: String): String {
            return if (origin == "") { origin } else { origin + "$" }
        }

        private fun timeFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                val date = origin.split("T")[0].substring(2)
                val time = origin.split("T")[1].split("Z")[0].substring(0,5)
                "$date | $time"
            }
        }

        private fun dataFormatter(origin: String): String {
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