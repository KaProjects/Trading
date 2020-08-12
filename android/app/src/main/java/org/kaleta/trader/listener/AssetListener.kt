package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.DataSource
import org.kaleta.trader.DataSource.refAssets
import org.kaleta.trader.data.Asset

class AssetListener : ValueEventListener {

    override fun onDataChange(dataSnapshot: DataSnapshot) {
        if (dataSnapshot.children.count() == 0) {
            createCompany("BA")
            createCompany("AAL")
            createCompany("DAL")
            createCompany("UAL")
            createCompany("CCL")
            createCompany("RCL")
            createCompany("NCLH")
            createCompany("MGM")
        } else {
            DataSource.assets.clear()
            for (postSnapshot in dataSnapshot.children) {
                val asset: Asset = postSnapshot.getValue(Asset::class.java) as Asset
                asset.id = postSnapshot.key!!
                DataSource.assets.add(asset)
            }
        }
    }

    private fun createCompany(ticker: String){
        val asset = Asset(ticker,"","","")
        asset.id = refAssets.push().key!!
        refAssets.child(asset.id).setValue(asset)
    }

    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }

}