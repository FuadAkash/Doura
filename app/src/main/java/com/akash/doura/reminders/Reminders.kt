package com.akash.doura.reminders

/*
 * Reminder engine: storage, alarm scheduling, notification and boot receivers.
 *
 * One Reminder = one departure + a list of "minutes before" offsets.
 *   - Journey detail "Remind me":  5, 2, 0
 *   - Set journey page:            30, 15, 5, 0
 * Each offset becomes its own alarm. Offsets already in the past are skipped.
 */

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
import com.akash.doura.MainActivity
import com.akash.doura.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val JOURNEY_OFFSETS = listOf(5, 2, 0)
val PLANNED_OFFSETS = listOf(30, 15, 5, 0)

data class Reminder(
    val id: String,
    val originName: String,
    val destName: String,
    val departMillis: Long,
    val offsets: List<Int>,
    val line: String? = null
)

data class HistoryEntry(
    val originId: String,
    val destId: String,
    val originName: String,
    val destName: String,
    val lastMillis: Long
)

/* ------------------------------------------------------------------------ */
/* Storage (SharedPreferences + Gson; the lists are tiny)                    */
/* ------------------------------------------------------------------------ */

private class JsonListStore<T>(context: Context, private val key: String, private val type: TypeToken<List<T>>) {
    private val prefs = context.applicationContext.getSharedPreferences("doura_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(): List<T> = runCatching {
        gson.fromJson<List<T>>(prefs.getString(key, null) ?: "[]", type.type)
    }.getOrNull().orEmpty()

    fun write(list: List<T>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}

class ReminderStore(context: Context) {
    private val store = JsonListStore(context, "reminders", object : TypeToken<List<Reminder>>() {})

    /** Upcoming reminders only, soonest first. Expired ones are pruned. */
    fun all(): List<Reminder> {
        val now = System.currentTimeMillis()
        val all = store.read()
        val live = all.filter { it.departMillis >= now - 60_000 }
        if (live.size != all.size) store.write(live)
        return live.sortedBy { it.departMillis }
    }

    fun add(reminder: Reminder) = store.write(store.read().filterNot { it.id == reminder.id } + reminder)
    fun remove(id: String) = store.write(store.read().filterNot { it.id == id })
    fun find(id: String) = store.read().firstOrNull { it.id == id }
}

class HistoryStore(context: Context) {
    private val store = JsonListStore(context, "history", object : TypeToken<List<HistoryEntry>>() {})

    fun all(): List<HistoryEntry> = store.read().sortedByDescending { it.lastMillis }

    /** Same origin+destination moves to the top instead of duplicating. */
    fun record(originId: String, destId: String, originName: String, destName: String) {
        val entry = HistoryEntry(originId, destId, originName, destName, System.currentTimeMillis())
        val rest = store.read().filterNot { it.originId == originId && it.destId == destId }
        store.write((listOf(entry) + rest).take(30))
    }

    fun remove(entry: HistoryEntry) =
        store.write(store.read().filterNot { it.originId == entry.originId && it.destId == entry.destId })

    fun clear() = store.write(emptyList())
}

/* ------------------------------------------------------------------------ */
/* Scheduling                                                                */
/* ------------------------------------------------------------------------ */

object ReminderScheduler {

    const val CHANNEL_ID = "departures"
    const val EXTRA_ID = "id"
    const val EXTRA_OFFSET = "offset"
    const val EXTRA_FROM = "from"
    const val EXTRA_TO = "to"
    const val EXTRA_DEPART = "depart"
    const val EXTRA_LINE = "line"

    /** Schedules every future offset. Returns how many alarms were actually set. */
    fun schedule(context: Context, reminder: Reminder, persist: Boolean = true): Int {
        if (persist) ReminderStore(context).add(reminder)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        var count = 0

        reminder.offsets.forEach { offset ->
            val trigger = reminder.departMillis - offset * 60_000L
            if (trigger <= now) return@forEach
            val pi = pendingIntent(context, reminder, offset)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                // Exact-alarm access not granted: still fires, just possibly a few minutes late.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            count++
        }
        return count
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminder.offsets.forEach { alarms.cancel(pendingIntent(context, reminder, it)) }
        ReminderStore(context).remove(reminder.id)
    }

    /** Alarms don't survive a reboot — the boot receiver calls this. */
    fun rescheduleAll(context: Context) {
        ReminderStore(context).all().forEach { schedule(context, it, persist = false) }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    fun newId() = System.currentTimeMillis().toString()

    private fun pendingIntent(context: Context, r: Reminder, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.akash.doura.REMINDER"
            data = android.net.Uri.parse("doura://reminder/${r.id}/$offset")
            putExtra(EXTRA_ID, r.id)
            putExtra(EXTRA_OFFSET, offset)
            putExtra(EXTRA_FROM, r.originName)
            putExtra(EXTRA_TO, r.destName)
            putExtra(EXTRA_DEPART, r.departMillis)
            putExtra(EXTRA_LINE, r.line)
        }
        return PendingIntent.getBroadcast(
            context,
            "${r.id}/$offset".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/* ------------------------------------------------------------------------ */
/* Receivers                                                                 */
/* ------------------------------------------------------------------------ */

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 0)
        val from = intent.getStringExtra(ReminderScheduler.EXTRA_FROM).orEmpty()
        val to = intent.getStringExtra(ReminderScheduler.EXTRA_TO).orEmpty()
        val depart = intent.getLongExtra(ReminderScheduler.EXTRA_DEPART, 0L)
        val line = intent.getStringExtra(ReminderScheduler.EXTRA_LINE)

        // Permission can be revoked after scheduling; fail quietly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(depart))
        val title = if (offset == 0) "Departing now" else "Leaves in $offset min"
        val what = if (line.isNullOrBlank()) "" else "$line · "
        val text = "$what$from → $to · $time"

        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_doura)
            .setColor(0xFF27C46B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify("${id}/$offset".hashCode(), notification)
        }

        // Last alarm of a reminder: drop it from the saved list.
        if (offset == 0) ReminderStore(context).remove(id)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Departure reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Heads-ups before your bus, train or metro leaves" }
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.rescheduleAll(context)
    }
}
