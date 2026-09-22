package ouija.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ouija.app.OuijaApp
import ouija.app.R
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.telemetry.Telemetry

class ClientForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val effectManager: EffectManager by lazy { (applicationContext as OuijaApp).effectManager }
    private val realtimeManager: RealtimeManager by lazy { (applicationContext as OuijaApp).realtimeManager }

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE) ?: ""

        if (roomCode.isNotBlank()) {
            realtimeManager.joinRoom(roomCode, isController = false)

            // Collect incoming commands even in background
            serviceScope.launch {
                realtimeManager.incomingCommand.collect { cmd ->
                    effectManager.executeCommand(cmd)
                }
            }

            // Periodic heartbeat
            heartbeatJob?.cancel()
            heartbeatJob = serviceScope.launch {
                while (true) {
                    delay(2500L)
                    realtimeManager.sendTelemetry(Telemetry(currentScreen = "BACKGROUND"))
                }
            }
        }

        return START_STICKY
    }

    private fun promoteToForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ouija Board Active")
            .setContentText("Connected to session")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatJob?.cancel()
        effectManager.stopAll()
        realtimeManager.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ouija Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ouija_background_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_ROOM_CODE = "extra_room_code"

        fun startService(context: Context, roomCode: String) {
            try {
                val intent = Intent(context, ClientForegroundService::class.java).apply {
                    putExtra(EXTRA_ROOM_CODE, roomCode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, ClientForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
