package org.kaleta.trader.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.R
import org.kaleta.trader.data.Company
import org.kaleta.trader.data.Opportunity
import java.math.BigDecimal

class OpportunityAdapter(a:String) : RecyclerView.Adapter<OpportunityAdapter.ViewHolder>(), ValueEventListener {

    constructor() :this("") {
        DataSource.opportunityReference.addValueEventListener(this)
        DataSource.companyReference.addValueEventListener(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.opportunity_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val opportunity = DataSource.opportunityMap.values.toList()[position]
        holder.bind(opportunity, DataSource.companyMap.get(opportunity.ticker)!!)
    }

    override fun getItemCount(): Int {
        return DataSource.opportunityMap.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var time: TextView = itemView.findViewById(R.id.time)

        var price: TextView = itemView.findViewById(R.id.price)
        var priceMin: TextView = itemView.findViewById(R.id.priceMin)
        var cci: TextView = itemView.findViewById(R.id.cci)
        var cciMin: TextView = itemView.findViewById(R.id.cciMin)
        var macd: TextView = itemView.findViewById(R.id.macd)
        var macdMin: TextView = itemView.findViewById(R.id.macdMin)
        var diff: TextView = itemView.findViewById(R.id.diff)
        var diffMin: TextView = itemView.findViewById(R.id.diffMin)

        fun bind(opportunity: Opportunity, company: Company) {
            ticker.text = company.ticker
            time.text = timeFormatter(company.time)
            price.text = priceFormatter(company.price)
            priceMin.text = priceFormatter(opportunity.min_price)
            cci.text = dataFormatter(company.cci)
            cciMin.text = dataFormatter(opportunity.min_cci)
            macd.text = dataFormatter(company.macd)
            macdMin.text = dataFormatter(opportunity.min_macd)
            diff.text = dataFormatter(company.diff)
            diffMin.text = dataFormatter(opportunity.min_diff)

            if (opportunity.min_cci.toFloat() < -2f) {
                cciMin.setTypeface(cciMin.getTypeface(), Typeface.BOLD)
                cciMin.setBackgroundResource(R.drawable.back_greener)
            } else {
                cciMin.setTypeface(Typeface.DEFAULT)
                cciMin.setBackgroundResource(R.drawable.back)
            }
            if (company.cci.toFloat() < -1f){
                if (company.cci.toFloat() < -1.5f){
                    cci.setBackgroundResource(R.drawable.back_lighter)
                    cci.setTextColor(ContextCompat.getColor(itemView.context, R.color.textLighter))
                } else {
                    cci.setBackgroundResource(R.drawable.back)
                    cci.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
                }
            } else {
                cci.setBackgroundResource(R.drawable.back_greener)
                cci.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
            }
            if (company.diff.toFloat() >= 0f) {
                diff.setBackgroundResource(R.drawable.back_greener)
            } else {
                diff.setBackgroundResource(R.drawable.back)

            }


        }

        private fun priceFormatter(origin: String): String {
            return if (origin == "") { origin } else { origin + "$" }
        }

        private fun timeFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                val date = origin.split("T")[0].split("-")
                val year = date[0].substring(2)
                val month = date[1]
                val day = date[2]

                val time = origin.split("T")[1].split("Z")[0].substring(0,5)
                "$day-$month-$year $time"
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