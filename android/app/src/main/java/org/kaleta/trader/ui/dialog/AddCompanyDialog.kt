package org.kaleta.trader.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.TextView
import org.kaleta.trader.R
import org.kaleta.trader.data.Company
import org.kaleta.trader.DataSource


class AddCompanyDialog: AlertDialog.Builder {

    constructor(context: Context) : super(context) {
        setTitle("Adding Company...")

        val dialogViewItems: View = View.inflate(context, R.layout.add_company_dialog, null)
        val tickerInput: TextView = dialogViewItems.findViewById(R.id.ticker_input)
        setView(dialogViewItems)



        setPositiveButton("Add", fun (_, _){
            val company = Company(tickerInput.text.toString(), "", "", "", "", "")
            company.id = DataSource.refCompanies.push().key!!
            DataSource.refCompanies.child(company.id).setValue(company)
        })

    }


}