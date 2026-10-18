package de.studyplan

import android.app.*
import android.content.*
import org.json.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// kind: LECTURE (Stundenplan), AUTO (automatische Übung), TASK (eigene Aufgabe), FREE (Freizeit)
data class Item(val id: Long, var title: String, var start: Long, var end: Long, val kind: String, var done: Boolean = false)

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("sp", 0)
    fun s(c: Context, k: String, d: String = "") = p(c).getString(k, d)!!
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun int(c: Context, k: String, d: Int) = s(c, k, d.toString()).toIntOrNull() ?: d
}

object Store {
    fun load(c: Context): MutableList<Item> {
        val a = JSONArray(Prefs.s(c, "items", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Item(o.getLong("id"), o.getString("t"), o.getLong("s"), o.getLong("e"), o.getString("k"), o.getBoolean("d"))
        }
    }
    fun save(c: Context, l: List<Item>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.start).put("e", it.end).put("k", it.kind).put("d", it.done)) }
        Prefs.put(c, "items", a.toString())
    }
}

object Ics {
    fun fetch(u: String): String = URL(u.trim().replace("webcal://", "https://")).openStream().bufferedReader().readText()

    fun parse(t: String): List<Triple<String, Long, Long>> {
        val lines = ArrayList<String>()
        for (raw in t.split("\r\n", "\n"))
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) lines[lines.size - 1] += raw.substring(1) else lines.add(raw)
        val out = ArrayList<Triple<String, Long, Long>>()
        var s = 0L; var e = 0L; var n = ""; var ev = false
        for (l in lines) when {
            l == "BEGIN:VEVENT" -> { ev = true; s = 0; e = 0; n = "" }
            l == "END:VEVENT" -> { if (ev && s > 0) out.add(Triple(n, s, if (e > s) e else s + 3600000)); ev = false }
            ev && l.startsWith("DTSTART") -> s = dt(l)
            ev && l.startsWith("DTEND") -> e = dt(l)
            ev && l.startsWith("SUMMARY") -> n = l.substringAfter(":").replace("\\,", ",").replace("\\;", ";")
        }
        return out
    }

    private fun dt(l: String): Long {
        val tz = Regex("TZID=([^:;]+)").find(l)?.groupValues?.get(1)
        val v = l.substringAfterLast(":")
        val f = SimpleDateFormat(if (v.length >= 15) "yyyyMMdd'T'HHmmss" else "yyyyMMdd", Locale.US)
        f.timeZone = when { v.endsWith("Z") -> TimeZone.getTimeZone("UTC"); tz != null -> TimeZone.getTimeZone(tz); else -> TimeZone.getDefault() }
        return f.parse(v.removeSuffix("Z"))!!.time
    }
}

object Planner {
    fun id() = Random.nextLong() and Long.MAX_VALUE
    fun at(t: Long, h: Int) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun overlaps(l: List<Item>, s: Long, e: Long) = l.any { it.start < e && s < it.end }

    // 1) tägliche Freizeit (AFREE) zur Wunschzeit, 2) Übungsblock (AUTO) nach jeder Vorlesung – alles im Tagesfenster, Raster 15 Min
    fun autoPlan(c: Context, items: MutableList<Item>) {
        val now = System.currentTimeMillis()
        items.removeAll { (it.kind == "AFREE" || (it.kind == "AUTO" && !it.done)) && it.end > now }
        val ds = Prefs.int(c, "ds", 6).coerceIn(6, 22); val de = Prefs.int(c, "de", 22).coerceIn(ds, 22)
        val dur = Prefs.int(c, "dur", 45) * 60000L
        val fd = Prefs.int(c, "fd", 90) * 60000L
        val fh = when (Prefs.int(c, "ft", 2)) { 0 -> ds; 1 -> 12; else -> 18 }.coerceIn(ds, de)
        for (d in 0..6) {
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, d) }.timeInMillis
            val limit = at(day, de)
            var s = maxOf(at(day, fh), now)
            while (s + fd <= limit) {
                if (!overlaps(items, s, s + fd)) { items.add(Item(id(), "Freizeit", s, s + fd, "AFREE")); break }
                s += 15 * 60000L
            }
        }
        for (lec in items.filter { it.kind == "LECTURE" && it.end > now }.sortedBy { it.start }) {
            val limit = at(lec.end, de)
            var s = maxOf(lec.end, at(lec.end, ds))
            while (s + dur <= limit) {
                if (!overlaps(items, s, s + dur)) { items.add(Item(id(), "Übung: " + lec.title, s, s + dur, "AUTO")); break }
                s += 15 * 60000L
            }
        }
    }
}

object Rem {
    // Erinnerung zum Start und Nachfrage ("erledigt?") zum Ende – für Aufgaben und automatische Übungen
    fun schedule(c: Context, items: List<Item>) {
        val am = c.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        for (i in items) {
            if (i.done || (i.kind != "TASK" && i.kind != "AUTO")) continue
            set(c, am, i, 0, i.start, now); set(c, am, i, 1, i.end, now)
        }
    }
    private fun set(c: Context, am: AlarmManager, i: Item, t: Int, at: Long, now: Long) {
        if (at <= now) return
        val pi = PendingIntent.getBroadcast(c, (i.id % 1000000).toInt() * 2 + t,
            Intent(c, Rx::class.java).putExtra("id", i.id).putExtra("t", t), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
    }
}

class Rx : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        val all = Store.load(c); val it = all.find { x -> x.id == id } ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sp", "Studienplaner", NotificationManager.IMPORTANCE_HIGH))
        when (i.action) {
            "DONE" -> { it.done = true; Store.save(c, all); nm.cancel(id.toInt()); return }
            "MORE" -> { it.end += 30 * 60000L; Store.save(c, all); Rem.schedule(c, listOf(it)); nm.cancel(id.toInt()); return }
        }
        val f = SimpleDateFormat("HH:mm", Locale.GERMANY)
        val b = Notification.Builder(c, "sp").setSmallIcon(android.R.drawable.ic_popup_reminder).setAutoCancel(true)
        if (i.getIntExtra("t", 0) == 0) b.setContentTitle("Jetzt: " + it.title).setContentText(f.format(Date(it.start)) + "–" + f.format(Date(it.end)))
        else b.setContentTitle(it.title + " erledigt?").setContentText("Oder brauchst du mehr Zeit?")
            .addAction(0, "Erledigt", act(c, "DONE", id)).addAction(0, "+30 Min", act(c, "MORE", id))
        nm.notify(id.toInt(), b.build())
    }
    private fun act(c: Context, a: String, id: Long) = PendingIntent.getBroadcast(c, (id % 1000000).toInt() * 2 + if (a == "DONE") 0 else 1,
        Intent(c, Rx::class.java).setAction(a).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Rem.schedule(c, Store.load(c))
}
