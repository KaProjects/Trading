package org.kaleta.trader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.kaleta.trader.listener.*
import org.kaleta.trader.ui.dialog.AddAssetDialog


class MainActivity : AppCompatActivity() {

    companion object {
        val notificationChannelId = "7777"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DataSource.logsLoaded = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
                R.id.navigation_assets, R.id.navigation_opportunities, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(NotificationChannel(notificationChannelId, "trader", NotificationManager.IMPORTANCE_HIGH))

        DataSource.companyReference.addChildEventListener(CompanyChildListener())
        DataSource.opportunityReference.addChildEventListener(OpportunityChildListener())
        DataSource.assetReference.addChildEventListener(AssetChildListener())
        DataSource.logReference.addChildEventListener(LogChildListener(this))
        DataSource.logReference.addValueEventListener(LogStartupListener())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id: Int = item.itemId
        if (id == R.id.add_asset) {
           AddAssetDialog(this).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}