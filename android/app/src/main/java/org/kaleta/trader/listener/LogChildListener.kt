package org.kaleta.trader.listener

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.DataSnapshot
import org.kaleta.trader.DataSource
import org.kaleta.trader.MainActivity
import org.kaleta.trader.R
import org.kaleta.trader.data.Log


class LogChildListener(private val context: Context) : AbstractChildEventListener() {

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
        if (DataSource.logsLoaded) {
            DataSource.logMap.remove(dataSnapshot.key)
        }
    }

    override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
        if (DataSource.logsLoaded) {
            val log: Log = dataSnapshot.getValue(Log::class.java) as Log
            log.id = dataSnapshot.key!!
            DataSource.logMap.put(dataSnapshot.key!!, log)
            throwNotification(log)
        }
    }

    override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
        if (DataSource.logsLoaded) {
            val log: Log = dataSnapshot.getValue(Log::class.java) as Log
            DataSource.logMap.replace(dataSnapshot.key!!, log)
        }
    }

    private fun throwNotification(log: Log) {
        val title = log.type
        val content = "${log.ticker} @ ${log.price}$"
        val bigContent = "${log.ticker} @ ${log.price}$ | " +
                "${log.cci} | ${log.macd} | ${log.diff}"

        val builder = NotificationCompat.Builder(context, MainActivity.notificationChannelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(contentIntent)
        builder.setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(bigContent.hashCode(), builder.build())
        }
    }

}