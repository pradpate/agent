package com.friendlocator.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FriendLocatorApp : Application() {

    companion object {
        const val CHANNEL_FRIEND_REQUESTS = "friend_requests"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_LOCATION = "location_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Friend Requests Channel
        val friendRequestChannel = NotificationChannel(
            CHANNEL_FRIEND_REQUESTS,
            "Friend Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming friend requests"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
        }
        notificationManager.createNotificationChannel(friendRequestChannel)

        // High-Priority Alerts Channel (bypasses DND)
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority alerts from friends that bypass Do Not Disturb"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            
            // Set sound
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(alarmSound, audioAttributes)
            
            // Bypass DND
            setBypassDnd(true)
            
            // Enable lights
            enableLights(true)
            lightColor = android.graphics.Color.RED
            
            // Lock screen visibility
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(alertChannel)

        // Location Service Channel
        val locationChannel = NotificationChannel(
            CHANNEL_LOCATION,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when location sharing is active"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(locationChannel)
    }
}
