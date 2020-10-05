package org.kaleta.trader.listener

import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.data.Asset

class AssetChildListener : AbstractChildEventListener() {

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        val asset: Asset = dataSnapshot.getValue(Asset::class.java) as Asset
        DataSource.assetMap.replace(asset.company.ticker, asset)
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val asset: Asset = dataSnapshot.getValue(Asset::class.java) as Asset
        DataSource.assetMap.put(asset.company.ticker, asset)
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        val asset: Asset = dataSnapshot.getValue(Asset::class.java) as Asset
        DataSource.assetMap.remove(asset.company.ticker)
    }
}