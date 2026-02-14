package com.friendlocator.app.location

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.friendlocator.app.FriendLocatorApp
import com.friendlocator.app.MainActivity
import com.friendlocator.app.R
import com.friendlocator.app.data.repository.LocationRepository
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var locationRepository: LocationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        
        // Location update interval (30 seconds)
        private const val LOCATION_INTERVAL = 30_000L
        // Fastest update interval (15 seconds)
        private const val FASTEST_INTERVAL = 15_000L
        // Minimum displacement to trigger update (10 meters)
        private const val MIN_DISPLACEMENT = 10f
    }

    override fun onCreate() {
        super.onCreate()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    serviceScope.launch {
                        locationRepository.updateLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            altitude = location.altitude,
                            speed = location.speed,
                            bearing = location.bearing
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isTracking) return
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
        } catch (e: SecurityException) {
            // Location permission not granted
            stopSelf()
        }
    }

    private fun stopTracking() {
        if (!isTracking) return
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, FriendLocatorApp.CHANNEL_LOCATION)
            .setContentTitle("Location Sharing Active")
            .setContentText("Your friends can see your location")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Sharing", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()
    }
}
