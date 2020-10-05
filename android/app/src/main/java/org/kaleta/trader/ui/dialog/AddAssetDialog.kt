package org.kaleta.trader.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.kaleta.trader.R
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Asset


class AddAssetDialog: AlertDialog.Builder {

    constructor(context: Context) : super(context) {
        setTitle("Adding Asset...")

        val dialogViewItems: View = View.inflate(context, R.layout.add_asset_dialog, null)

        val tickerInput: TextView = dialogViewItems.findViewById(R.id.ticker_input)
        val priceInput: TextView = dialogViewItems.findViewById(R.id.price_input)
        val amountInput: TextView = dialogViewItems.findViewById(R.id.amount_input)

        setView(dialogViewItems)

        setPositiveButton("Add", fun(_, _){
            val ticker = tickerInput.text.toString()
            val price = priceInput.text.toString()
            val amount = amountInput.text.toString()

            val company = DataSource.companyMap.get(ticker)
            if (company != null) {
                val asset = Asset(company, price, amount)
                DataSource.assetReference.child(company.ticker).setValue(asset)
                // TODO: 5.10.2020 maybe log buying asset for later analyses
            } else {
                Toast.makeText(context, "Ticker '$ticker' couldn't be found!", Toast.LENGTH_LONG).show()
            }
        })
    }
}