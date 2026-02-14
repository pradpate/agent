package com.friendlocator.app.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.friendlocator.app.data.repository.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertRepository: AlertRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_DISMISS = "com.friendlocator.app.ACTION_DISMISS_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra("alert_id") ?: return

        when (intent.action) {
            ACTION_DISMISS -> {
                // Mark alert as read
                scope.launch {
                    alertRepository.markAlertAsRead(alertId)
                }

                // Dismiss notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(alertId.hashCode())
            }
        }
    }
}
