package org.kaleta.trader.listener

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.MainActivity
import org.kaleta.trader.R
import org.kaleta.trader.data.Company
import org.kaleta.trader.data.Log
import org.kaleta.trader.data.Opportunity
import java.math.RoundingMode

class CompanyChildListener(private val context: Context) : AbstractChildEventListener() {

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        val company: Company = dataSnapshot.getValue(Company::class.java) as Company
        DataSource.companyMap.remove(company.ticker)
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        val company: Company = dataSnapshot.getValue(Company::class.java) as Company
        DataSource.companyMap.put(company.ticker, company)
    }

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        val company: Company = dataSnapshot.getValue(Company::class.java) as Company
        DataSource.companyMap.replace(company.ticker, company)

        val opportunity: Opportunity? = DataSource.opportunityMap.get(company.ticker)
        if (opportunity == null) {
            consumeCompany(company)
        } else {
            consumeCompany(company, opportunity)
        }
    }

    /**
     * null opportunity, may create opportunity when conditions are met
     */
    fun consumeCompany(company: Company) {
        val cci = company.cci.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat()
        val macd = company.macd.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat()
        val diff = company.diff.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat()

        // TODO: 8.9.2020 kedy crate opp.?
        if (cci < -1.50f) {
            val opportunity = Opportunity(company, company.cci, company.macd, company.diff, company.price)
            DataSource.opportunityReference.child(opportunity.company.ticker).setValue(opportunity)
            // TODO: 15.9.2020  create log and/or throw notification
            createLog(opportunity, "cci<-1.5")
            throwOpportunityCreated(opportunity)
        }
    }

    /**
     * non-null opportunity, updates or may delete opportunity when conditions are met
     */
    fun consumeCompany(company: Company, opportunity: Opportunity) {
        val cci = company.cci.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toFloat()

        // TODO: 8.9.2020 co ukladat do opp. co staci comp?
        if (cci < -0.25f) {
            // TODO: 15.9.2020 if (cci crossing up -1f) { create log and/or throw notification
            // TODO: 15.9.2020 if (diff crossing up 0f) { create log and/or throw notification
            // TODO: 18.9.2020 macd crossing up something ....

            if (company.cci.toBigDecimal().toFloat() < opportunity.cciMin.toBigDecimal().toFloat()) opportunity.cciMin = company.cci
            if (company.macd.toBigDecimal().toFloat() < opportunity.macdMin.toBigDecimal().toFloat()) opportunity.macdMin = company.macd
            if (company.diff.toBigDecimal().toFloat() < opportunity.diffMin.toBigDecimal().toFloat()) opportunity.diffMin = company.diff
            if (company.price.toBigDecimal().toFloat() < opportunity.priceMin.toBigDecimal().toFloat()) opportunity.priceMin = company.price

            opportunity.company = company
            DataSource.opportunityReference.child(opportunity.company.ticker).setValue(opportunity)
        } else {
            DataSource.opportunityReference.child(opportunity.company.ticker).removeValue()
        }
    }

    private fun throwOpportunityCreated(opportunity: Opportunity) {
        val title = "Opportunity Created"
        val content = "${opportunity.company.ticker} @ ${opportunity.company.price}$"
        val bigContent = "${opportunity.company.ticker} @ ${opportunity.company.price}$ | " +
                "${opportunity.company.cci} | ${opportunity.company.macd} | " +
                "${opportunity.company.signal} | ${opportunity.company.diff}"

        val builder = NotificationCompat.Builder(context, MainActivity.notificationChannelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            notify(bigContent.hashCode(), builder.build())
        }
    }

    private fun createLog(opportunity: Opportunity, type: String){
        var log = Log(type, opportunity.company.ticker, opportunity.company.price, opportunity.company.time,
            opportunity.company.cci, opportunity.cciMin,
            opportunity.company.macd, opportunity.macdMin,
            opportunity.company.diff,opportunity.diffMin)

        val id = DataSource.logReference.push().key
        DataSource.logReference.child(id!!).setValue(log)
    }
}