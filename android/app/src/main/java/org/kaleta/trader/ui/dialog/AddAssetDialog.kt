package org.kaleta.trader.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.TextView
import org.kaleta.trader.R
import org.kaleta.trader.data.Company
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Asset


class AddAssetDialog: AlertDialog.Builder {

    constructor(context: Context, asset: Asset) : super(context) {
        setTitle("Adding Asset...")

        val dialogViewItems: View = View.inflate(context, R.layout.add_asset_dialog, null)

        val priceInput: TextView = dialogViewItems.findViewById(R.id.price_input)

        val amountInput: TextView = dialogViewItems.findViewById(R.id.amount_input)

        setView(dialogViewItems)

        setPositiveButton("Add", fun(_, _){

            asset.price = priceInput.text.toString()
            asset.amount = amountInput.text.toString()

            DataSource.refAssets.child(asset.id).setValue(asset)

            // TODO: 12/08/2020 create buy log
        })
    }
}