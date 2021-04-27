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
import java.lang.String.format
import java.math.BigDecimal
import java.text.DateFormat
import java.text.MessageFormat.format
import java.text.SimpleDateFormat
import java.util.*

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
        var priceEdge: TextView = itemView.findViewById(R.id.priceEdge)
        var cci: TextView = itemView.findViewById(R.id.cci)
        var cciEdge: TextView = itemView.findViewById(R.id.cciEdge)
        var macd: TextView = itemView.findViewById(R.id.macd)
        var macdEdge: TextView = itemView.findViewById(R.id.macdEdge)
        var diff: TextView = itemView.findViewById(R.id.diff)
        var diffEdge: TextView = itemView.findViewById(R.id.diffEdge)

        fun bind(opportunity: Opportunity, company: Company) {
            ticker.text = company.ticker
            time.text = timeFormatter(company.time)
            price.text = priceFormatter(company.price)
            priceEdge.text = priceFormatter(opportunity.edge_price)
            cci.text = dataFormatter(company.cci)
            cciEdge.text = dataFormatter(opportunity.edge_cci)
            macd.text = dataFormatter(company.macd)
            macdEdge.text = dataFormatter(opportunity.edge_macd)
            diff.text = dataFormatter(company.diff)
            diffEdge.text = dataFormatter(opportunity.edge_diff)

            if (opportunity.edge_cci.toFloat() < -2f || opportunity.edge_cci.toFloat() > 2f) {
                cciEdge.setTypeface(cciEdge.getTypeface(), Typeface.BOLD)
                cciEdge.setBackgroundResource(R.drawable.back_greener)
            } else {
                cciEdge.setTypeface(Typeface.DEFAULT)
                cciEdge.setBackgroundResource(R.drawable.back)
            }

            cci.setBackgroundResource(R.drawable.back)
            cci.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
            macd.setBackgroundResource(R.drawable.back)
            macd.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
            diff.setBackgroundResource(R.drawable.back)
            diff.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
            if (opportunity.signal.substring(0,1) == "0"){
                if (company.cci.toFloat() < -1.5f || company.cci.toFloat() > 1.5f){
                    cci.setBackgroundResource(R.drawable.back_lighter)
                    cci.setTextColor(ContextCompat.getColor(itemView.context, R.color.textLighter))
                    macd.setBackgroundResource(R.drawable.back_lighter)
                    macd.setTextColor(ContextCompat.getColor(itemView.context, R.color.textLighter))
                    diff.setBackgroundResource(R.drawable.back_lighter)
                    diff.setTextColor(ContextCompat.getColor(itemView.context, R.color.textLighter))
                }
            } else {
                cci.setBackgroundResource(R.drawable.back_greener)
            }
            if (opportunity.signal.substring(1,2) == "1") {
                macd.setBackgroundResource(R.drawable.back_greener)
            }
            if (opportunity.signal.substring(2,3) == "1") {
                diff.setBackgroundResource(R.drawable.back_greener)
            }
        }

        private fun priceFormatter(origin: String): String {
            return if (origin == "") { origin } else { origin + "$" }
        }

        private fun timeFormatter(origin: String): String {
            return if (origin == "") {
                origin
            } else {
                var gmt =  Calendar.getInstance()
                gmt.timeZone = TimeZone.getTimeZone("GMT")

                val date = origin.split("T")[0].split("-")

                gmt.set(Calendar.YEAR, date[0].toInt())
                gmt.set(Calendar.MONTH, date[1].toInt() - 1)
                gmt.set(Calendar.DAY_OF_MONTH, date[2].toInt())

                val time = origin.split("T")[1].split("Z")[0].substring(0,5).split(":")

                gmt.set(Calendar.HOUR_OF_DAY, time[0].toInt())
                gmt.set(Calendar.MINUTE, time[1].toInt())

                val local = Calendar.getInstance()
                local.timeInMillis = gmt.timeInMillis

                SimpleDateFormat("dd-MM-yy HH:mm").format(Date(local.timeInMillis))
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