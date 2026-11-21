package io.github.akiig.moodlog

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The daily reminder: one quiet notification at the time you chose, only on days you
 * haven't logged a mood yet. No streaks and no "you missed" messages. It survives restarts
 * (the receiver also runs after the phone boots or Moodlog is updated).
 */
object Reminder {
    const val CHANNEL = "reminder"
    const val ACTION = "io.github.akiig.moodlog.REMIND"
    const val OPEN_TODAY = "open_today"

    /** The next time to remind: today at `time` if that's still ahead (and nothing is logged
     *  yet), else tomorrow at `time`. */
    fun next(now: LocalDateTime, time: LocalTime, loggedToday: Boolean): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now) && !loggedToday) today else today.plusDays(1)
    }

    fun parse(text: String): LocalTime = try { LocalTime.parse(text) } catch (e: Exception) { LocalTime.of(20, 30) }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun loggedToday(context: Context): Boolean =
        AppStore(File(context.filesDir, "moodlog.json")).moodsOn(Day.today).isNotEmpty()

    /** (Re)plans the next reminder, or cancels it when the reminder is off. */
    fun schedule(context: Context) {
        val prefs = Prefs(context)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context))
        if (!prefs.reminderOn) return
        val at = next(LocalDateTime.now(), parse(prefs.reminderTime), loggedToday(context))
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // a 10-minute window lets Android save battery; no "exact alarm" permission needed
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pending(context))
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, tr("Daily reminder"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("Once a day, only if you haven't logged a mood yet.")
            setShowBadge(false)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(context, 2,
            Intent(context, MainActivity::class.java).putExtra(OPEN_TODAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("How are you today?"))
            .setContentText(tr("One tap on a face is enough."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, n)
        } catch (e: SecurityException) {
            // notifications were turned off in the meantime
        }
    }

    fun onAlarm(context: Context) {
        if (Prefs(context).reminderOn && !loggedToday(context)) show(context)
        schedule(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        loadTranslations(context)
        when (intent.action) {
            Reminder.ACTION -> Reminder.onAlarm(context)
            SurveyReminder.ACTION -> SurveyReminder.onAlarm(context)
            else -> {   // boot, update, time or time-zone change
                Reminder.schedule(context)
                SurveyReminder.schedule(context)
            }
        }
    }
}
