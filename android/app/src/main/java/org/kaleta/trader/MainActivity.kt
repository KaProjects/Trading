package org.kaleta.trader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.kaleta.trader.data.AlertCCI
import org.kaleta.trader.data.Company
import org.kaleta.trader.data.DataSource
import org.kaleta.trader.data.Log
import java.math.RoundingMode


class MainActivity : AppCompatActivity(), ValueEventListener {

    val notificationChannelId = "7777"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
                R.id.navigation_home, R.id.navigation_opportunities, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        DataSource.refAlertsCCI45.addValueEventListener(this)

        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(NotificationChannel(notificationChannelId, "trader", NotificationManager.IMPORTANCE_HIGH))
    }

    /**
     * consumes alert, which can results in: update a company, create log and/or throw notification
     */
    override fun onDataChange(dataSnapshot: DataSnapshot) {
        for (postSnapshot in dataSnapshot.children) {
            val alert: AlertCCI = postSnapshot.getValue(AlertCCI::class.java) as AlertCCI
            var company: Company? = DataSource.companies.find { company -> company.ticker == alert.ticker }

            if (company == null) {
                continue
                // TODO: 29.7.2020 better don't do nothing than create duplicates
//                company = Company(alert.ticker, "", "", "", "", "")
//                company.id = DataSource.refCompanies.push().key!!
//                DataSource.refCompanies.child(company.id).setValue(company)
            }

            val noSignalBefore = company.signal == ""
            val noConditionBefore = company.condition == ""
            consumeAlert(company, alert)
            if (noSignalBefore && company.signal != "") {
                var log = Log(company.ticker, company.price, company.signal, company.condition, company.time)
                val id = DataSource.refLogs.push().key
                DataSource.refLogs.child(id!!).setValue(log)
                throwSignalNotification(log)
            }
            if (noConditionBefore && company.condition != ""){
                throwConditionNotification(company)
            }

            DataSource.database.getReference(DataSource.refCompaniesPath + company.id).setValue(company)

            DataSource.database.getReference(DataSource.refAlertCCI45Path + postSnapshot.key).removeValue()
        }
    }
    override fun onCancelled(p0: DatabaseError) {
        println(p0.message)
    }

    private fun consumeAlert(company: Company, alertCCI: AlertCCI) {
        val cci = alertCCI.cci45.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat()
        when {
            // general
            cci >= 75 -> {
                company.price = alertCCI.price
                company.cci = alertCCI.cci45
                company.time = alertCCI.time
            }
            cci < 75 && cci > -75 -> {
                company.price = ""
                company.condition = ""
                company.signal = ""
                company.cci = ""
                company.time = ""
            }
            cci <= -75 -> {
                company.price = alertCCI.price
                company.cci = alertCCI.cci45
                company.time = alertCCI.time
            }
        }

        when {
            // sell opportunity
            cci >= 150 -> {
                company.signal = ""
                if (company.condition == ""){
                    company.condition = alertCCI.cci45
                } else {
                    if ((company.condition.toFloat() < alertCCI.cci45.toFloat())) {
                        company.condition = alertCCI.cci45
                    }
                }
            }
            cci < 150 && cci >= 100 -> {
                if (company.signal != "") {
                    company.signal = alertCCI.cci45
                }
            }
            cci < 100 && cci >= 75 -> {
                if (company.condition != ""){
                    company.signal = alertCCI.cci45
                }
            }
            // buy opportunity
            cci <= -75 && cci > -100 -> {
                if (company.condition != ""){
                    company.signal = alertCCI.cci45
                }
            }
            cci <= -100 && cci > -150 -> {
                if (company.signal != "") {
                    company.signal = alertCCI.cci45
                }
            }
            cci <= -150 -> {
                company.signal = ""
                if (company.condition == ""){
                    company.condition = alertCCI.cci45
                } else {
                    if ((company.condition.toFloat() > alertCCI.cci45.toFloat())) {
                        company.condition = alertCCI.cci45
                    }
                }
            }
        }
    }

    private fun throwSignalNotification(log: Log) {
        val action = if (log.signal.toFloat() > 0) { "Sell" } else { "Buy" }
        val title = "$action Opportunity"
        val content = "${log.ticker} @ ${log.price}$"
        val bigContent = "${log.ticker} @ ${log.price}$ | ${log.condition} | ${log.signal}"

        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            .setPriority(NotificationManager.IMPORTANCE_HIGH)

        with(NotificationManagerCompat.from(this)) {
            notify(bigContent.hashCode(), builder.build())
        }
    }

    private fun throwConditionNotification(company: Company) {
        val action = if (company.condition.toFloat() > 0) { "Sell" } else { "Buy" }
        val title = "$action Condition Passed"
        val content = "${company.ticker} @ ${company.price}$"
        val bigContent = "${company.ticker} @ ${company.price}$ | ${company.condition}"

        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(bigContent.hashCode(), builder.build())
        }
    }
}