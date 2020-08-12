package org.kaleta.trader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.R
import org.kaleta.trader.data.Asset
import org.kaleta.trader.ui.dialog.AddAssetDialog
import org.kaleta.trader.ui.dialog.RemoveAssetDialog
import java.math.BigDecimal

class AssetAdapter(a:String): RecyclerView.Adapter<AssetAdapter.ViewHolder>(), ValueEventListener {

    private val assets: MutableList<Asset> = DataSource.assets

    constructor() :this("") {
        DataSource.refAssets.addValueEventListener(this);
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.asset_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(assets[position])
    }

    override fun getItemCount(): Int {
        return assets.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var ticker: TextView = itemView.findViewById(R.id.ticker)
        var add: ImageButton = itemView.findViewById(R.id.addAsset)
        var purchaseLabel: TextView = itemView.findViewById(R.id.purchaseLabel)
        var purchase: TextView = itemView.findViewById(R.id.purchase)
        var purchaseSum: TextView = itemView.findViewById(R.id.purchaseSum)
        var currentLabel: TextView = itemView.findViewById(R.id.currentLabel)
        var current: TextView = itemView.findViewById(R.id.current)
        var currentSum: TextView = itemView.findViewById(R.id.currentSum)
        var change: TextView = itemView.findViewById(R.id.change)
        var profit: TextView = itemView.findViewById(R.id.profit)
        var remove: ImageButton = itemView.findViewById(R.id.removeAsset)

        fun bind(asset: Asset) {
            ticker.text = asset.ticker
            purchase.text = purchaseFormatter(asset.price, asset.amount)
            purchaseSum.text = purchaseSumFormatter(asset.price, asset.amount)
            current.text = currentFormatter(asset.current, asset.amount)
            currentSum.text = currentSumFormatter(asset.current, asset.amount)
            change.text = changeFormatter(asset.price, asset.current, asset.amount)
            profit.text = profitFormatter(asset.price, asset.current)

            if (asset.price == "") {
                add.visibility = View.VISIBLE
                add.setOnClickListener(fun(_){ AddAssetDialog(itemView.context, asset).show() })
                purchaseLabel.visibility = View.INVISIBLE
                purchase.visibility = View.INVISIBLE
                purchaseSum.visibility = View.INVISIBLE
                currentLabel.visibility = View.INVISIBLE
                current.visibility = View.INVISIBLE
                currentSum.visibility = View.INVISIBLE
                change.visibility = View.INVISIBLE
                profit.visibility = View.INVISIBLE
                remove.visibility = View.INVISIBLE
            } else {
                add.visibility = View.INVISIBLE
                purchaseLabel.visibility = View.VISIBLE
                purchase.visibility = View.VISIBLE
                purchaseSum.visibility = View.VISIBLE
                currentLabel.visibility = View.VISIBLE
                current.visibility = View.VISIBLE
                currentSum.visibility = View.VISIBLE
                change.visibility = View.VISIBLE
                profit.visibility = View.VISIBLE
                if (profit.text.contains("+")) {
                    change.setTextColor(ContextCompat.getColor(itemView.context, R.color.profit))
                    profit.setTextColor(ContextCompat.getColor(itemView.context, R.color.profit))
                } else {
                    change.setTextColor(ContextCompat.getColor(itemView.context, R.color.loss))
                    profit.setTextColor(ContextCompat.getColor(itemView.context, R.color.loss))
                }
                remove.visibility = View.VISIBLE
                remove.setOnClickListener(fun (_) { RemoveAssetDialog(itemView.context, asset).show() })

            }

        }

        private fun purchaseFormatter(price: String, amount: String): String {
            return if (price == "" || amount == "") { "" } else { "$amount@$price$" }
        }

        private fun purchaseSumFormatter(price: String, amount: String): String {
            return if (price == "" || amount == "") { "" } else { (price.toBigDecimal() * amount.toBigDecimal()).setScale(1, BigDecimal.ROUND_FLOOR).toString() + "$" }
        }

        private fun currentFormatter(current: String, amount: String): String {
            return if (current == "" || amount == "") { "" } else { "$amount@$current$" }
        }

        private fun currentSumFormatter(current: String, amount: String): String {
            return if (current == "" || amount == "") { "" } else { (current.toBigDecimal() * amount.toBigDecimal()).setScale(1, BigDecimal.ROUND_FLOOR).toString() + "$" }
        }

        private fun changeFormatter(price: String, current: String, amount: String): String {
            return if (amount == "" || current == "" || price == "") {
                ""
            } else {
                val change = ((current.toBigDecimal() - price.toBigDecimal()) * amount.toBigDecimal()).setScale(1, BigDecimal.ROUND_FLOOR)
                val sign = if (change > BigDecimal(0)) "+" else ""
                "$sign$change$"
            }
        }

        private fun profitFormatter(price: String, current: String): String {
            return if (current == "" || price == "") {
                ""
            } else {
                val profit = (((current.toFloat() / price.toFloat()) - 1) * 100).toBigDecimal().setScale(1, BigDecimal.ROUND_FLOOR)
                val sign = if (profit > BigDecimal(0)) "+" else ""
                "$sign$profit%"
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
