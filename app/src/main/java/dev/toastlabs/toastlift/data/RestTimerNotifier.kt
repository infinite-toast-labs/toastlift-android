package dev.toastlabs.toastlift.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import kotlinx.coroutines.delay

object RestTimerNotificationContract {
    const val CHANNEL_ID = "rest_timer"
    const val CHANNEL_NAME = "Rest timer"
}

class RestTimerNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val attributes = notificationAudioAttributes()
        val channel = NotificationChannel(
            RestTimerNotificationContract.CHANNEL_ID,
            RestTimerNotificationContract.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Sound played when a prescribed set rest timer ends."
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attributes)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    suspend fun playCompletionBeeps() {
        val soundUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(RestTimerNotificationContract.CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
            channel?.sound ?: return
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        }
        val ringtone = RingtoneManager.getRingtone(appContext, soundUri) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ringtone.audioAttributes = notificationAudioAttributes()
        }
        ringtone.play()
        try {
            delay(900)
        } finally {
            if (ringtone.isPlaying) {
                ringtone.stop()
            }
        }
    }

    private fun notificationAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}
