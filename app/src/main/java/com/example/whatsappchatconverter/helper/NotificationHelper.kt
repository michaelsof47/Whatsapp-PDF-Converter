package com.example.whatsappchatconverter.helper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.whatsappchatconverter.MainActivity
import com.example.whatsappchatconverter.R
import com.example.whatsappchatconverter.model.DataFileModel
import org.greenrobot.eventbus.EventBus

class NotificationHelper private constructor() {

    val notificationId: Int = NOTIFICATION_ID
    val channelId: String = CHANNEL_ID

    companion object {
        private const val CHANNEL_ID = "WhatsappChatConverter"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper().also { instance = it }
            }
        }
    }

    fun createNotification(context: Context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Whatsapp Chat Converter",
                NotificationManager.IMPORTANCE_HIGH,
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context, text: String, dataModel: DataFileModel? = null): Notification {

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Converting To PDF")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (dataModel != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("filename_uri",dataModel.filename)
                putExtra("progress", dataModel.progress)
            }

            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)

            if(dataModel.progress == 100) {
                builder.setProgress(100, dataModel.progress, false)
                builder.setOngoing(false)
            }
        }

        return builder.build()
    }

    fun updateNotification(context: Context, text: String, dataModel: DataFileModel) {
        val notification = buildNotification(context, text, dataModel)

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if(ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

}