package dev.toastlabs.toastlift.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.toastlabs.toastlift.R

private const val ACTIVE_WORKOUT_NOTIFICATION_ID = 1001

object WorkoutNotificationContract {
    const val CHANNEL_ID = "active_workout"
    const val CHANNEL_NAME = "Active workout"
}

class WorkoutNotificationNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            WorkoutNotificationContract.CHANNEL_ID,
            WorkoutNotificationContract.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the active workout while a workout is in progress."
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showActiveWorkout(workoutName: String) {
        if (!canPostNotifications()) return
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, WorkoutNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(workoutName)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(appContext).notify(ACTIVE_WORKOUT_NOTIFICATION_ID, notification)
    }

    fun cancelActiveWorkout() {
        NotificationManagerCompat.from(appContext).cancel(ACTIVE_WORKOUT_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
