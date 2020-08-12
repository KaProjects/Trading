package org.kaleta.trader.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.View
import org.kaleta.trader.DataSource
import org.kaleta.trader.R
import org.kaleta.trader.data.Asset

class RemoveAssetDialog: AlertDialog.Builder {

    constructor(context: Context, asset: Asset) : super(context) {
        setTitle("Removing Asset...")

        val dialogViewItems: View = View.inflate(context, R.layout.remove_asset_dialog, null)

        setView(dialogViewItems)

        setPositiveButton("Remove", fun(_, _){

            asset.price = ""
            asset.amount = ""
            asset.current = ""

            DataSource.refAssets.child(asset.id).setValue(asset)

            // TODO: 12/08/2020 create buy log
        })
    }
}