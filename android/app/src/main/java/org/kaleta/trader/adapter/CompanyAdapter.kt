package org.kaleta.trader.adapter

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.R
import org.kaleta.trader.data.Company
import org.kaleta.trader.DataSource
import java.math.BigDecimal

class CompanyAdapter(a:String): RecyclerView.Adapter<CompanyAdapter.ViewHolder>(), ValueEventListener {

    private val companies: MutableList<Company> = DataSource.companies

    constructor() :this("") {
        DataSource.refCompanies.addValueEventListener(this);
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.company_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(companies[position])
    }

    override fun getItemCount(): Int {
        return companies.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var price: TextView = itemView.findViewById(R.id.price)
        var condition: TextView = itemView.findViewById(R.id.condition)
        var conditionLabel: TextView = itemView.findViewById(R.id.conditionLabel)
        var signal: TextView = itemView.findViewById(R.id.signal)
        var signalLabel: TextView = itemView.findViewById(R.id.signalLabel)
        var time: TextView = itemView.findViewById(R.id.time)
        var advice: TextView = itemView.findViewById(R.id.advice)

        fun bind(company: Company) {
            ticker.text = company.ticker
            if (company.price == "") {
                price.visibility = View.INVISIBLE
                condition.visibility = View.INVISIBLE
                conditionLabel.visibility = View.INVISIBLE
                signal.visibility = View.INVISIBLE
                signalLabel.visibility = View.INVISIBLE
                advice.visibility = View.INVISIBLE
            } else {
                price.visibility = View.VISIBLE
                condition.visibility = View.VISIBLE
                conditionLabel.visibility = View.VISIBLE
                signal.visibility = View.VISIBLE
                signalLabel.visibility = View.VISIBLE
                advice.visibility = View.VISIBLE
            }
            price.text = priceFormatter(company.price)
            time.text = timeFormatter(company.time)

            if (company.condition == ""){
                condition.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_lighter, null)
                condition.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.textLighter, null))
                conditionLabel.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_lighter, null)
                conditionLabel.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.textLighter, null))
            } else {
                condition.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back, null)
                condition.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.text, null))
                conditionLabel.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back, null)
                conditionLabel.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.text, null))
            }
            condition.text = cciFormatter(company.condition)

            if (company.signal == ""){
                signal.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_lighter, null)
                signal.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.textLighter, null))
                signalLabel.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_lighter, null)
                signalLabel.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.textLighter, null))
                advice.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_lighter, null)
                advice.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.textLighter, null))
            } else {
                signal.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back, null)
                signal.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.text, null))
                signalLabel.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back, null)
                signalLabel.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.text, null))
                advice.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.back_advice, null)
                advice.setTextColor(ResourcesCompat.getColor(itemView.resources, R.color.text, null))
            }
            signal.text = cciFormatter(company.signal)

            if (company.cci != "" && company.cci.toFloat() < 0) {
                advice.text = "B\nU\nY"
                advice.setTextSize(TypedValue.COMPLEX_UNIT_SP,12f);
            } else {
                advice.text = "S\nE\nL\nL"
                advice.setTextSize(TypedValue.COMPLEX_UNIT_SP,9f);
            }

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