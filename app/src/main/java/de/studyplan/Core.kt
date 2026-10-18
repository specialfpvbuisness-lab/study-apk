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
        Prefs.put(c, "items", a.toString()); Widget.refresh(c)
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
    private fun addDays(t: Long, d: Int) = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.DAY_OF_YEAR, d) }.timeInMillis

    // Überfällige, offene To-dos zählen als "heute"
    fun eff(i: Item, from: Long) = if (i.kind == "TODO" && !i.done && i.start < from) from else i.start

    // Alles für den heutigen Tag (Vorlesungen, Sport, Aufgaben, Übungen, Freizeit, To-dos)
    fun today(all: List<Item>): List<Item> {
        val d0 = at(System.currentTimeMillis(), 0); val d1 = at(addDays(d0, 1), 0)
        return all.filter { if (it.kind == "TODO") !it.done || it.start >= d0 else it.start in d0 until d1 }
            .sortedWith(compareBy({ if (it.kind == "TODO") 1 else 0 }, { it.start }))
    }

    // Erster freier Slot (Raster 15 Min) zwischen from und limit; accept kann den Slot nachträglich ablehnen.
    private fun place(items: MutableList<Item>, from: Long, limit: Long, len: Long, title: String, kind: String, accept: () -> Boolean = { true }): Boolean {
        var s = from
        while (s + len <= limit) {
            if (!overlaps(items, s, s + len)) {
                val cand = Item(id(), title, s, s + len, kind)
                items.add(cand)
                if (accept()) return true
                items.remove(cand)
            }
            s += 15 * 60000L
        }
        return false
    }

    // Ist am Tag `day` noch Platz für einen Freizeitblock im Tagesfenster (oder schon eigene Freizeit eingetragen)?
    private fun freeRoom(items: List<Item>, day: Long, ds: Int, de: Int, fd: Long): Boolean {
        val d0 = at(day, 0); val d1 = at(addDays(day, 1), 0)
        if (items.any { it.kind == "FREE" && it.start >= d0 && it.start < d1 }) return true
        var s = at(day, ds); val limit = at(day, de)
        while (s + fd <= limit) { if (!overlaps(items, s, s + fd)) return true; s += 15 * 60000L }
        return false
    }

    // 1) Übungsblock (AUTO) direkt nach jeder Vorlesung. Ist der Tag voll (andere Termine/Aufgaben, oder es bliebe keine Freizeit),
    //    rutscht er auf den nächsten Tag – dort in den frühestmöglichen freien Slot.
    // 2) Danach täglich ein Freizeitblock (AFREE) zur Wunschzeit.
    fun autoPlan(c: Context, items: MutableList<Item>) {
        val now = System.currentTimeMillis()
        items.removeAll { (it.kind == "AFREE" || (it.kind == "AUTO" && !it.done)) && it.end > now }
        val ds = Prefs.int(c, "ds", 6).coerceIn(6, 22); val de = Prefs.int(c, "de", 22).coerceIn(ds, 22)
        val dur = Prefs.int(c, "dur", 45) * 60000L
        val fd = Prefs.int(c, "fd", 90) * 60000L
        val fh = when (Prefs.int(c, "ft", 2)) { 0 -> ds; 1 -> 12; else -> 18 }.coerceIn(ds, de)
        val horizon = addDays(now, 14)
        for (lec in items.filter { it.kind == "LECTURE" && it.end > now && it.start < horizon }.sortedBy { it.start }) {
            for (d in 0..7) {
                val day = addDays(lec.end, d); val limit = at(day, de)
                val from = maxOf(if (d == 0) maxOf(lec.end, at(day, ds)) else at(day, ds), now)
                val room = freeRoom(items, day, ds, de, fd)
                if (place(items, from, limit, dur, "Übung: " + lec.title, "AUTO") { !room || freeRoom(items, day, ds, de, fd) }) break
            }
        }
        for (d in 0..13) {
            val day = addDays(now, d); val limit = at(day, de)
            val d0 = at(day, 0); val d1 = at(addDays(day, 1), 0)
            if (items.any { it.kind == "FREE" && it.start >= d0 && it.start < d1 }) continue
            if (!place(items, maxOf(at(day, fh), now), limit, fd, "Freizeit", "AFREE"))
                place(items, maxOf(at(day, ds), now), limit, fd, "Freizeit", "AFREE")
        }
    }
}

object Look {
    fun label(i: Item) = if (i.kind == "TODO") "To-do" else
        SimpleDateFormat("HH:mm", Locale.GERMANY).let { it.format(Date(i.start)) + " – " + it.format(Date(i.end)) }
    fun color(k: String): Int = when (k) {
        "LECTURE" -> 0xFF8B7BFF; "AUTO" -> 0xFF22D3EE; "TASK" -> 0xFFFFB84D; "SPORT" -> 0xFFFF6B9A; "TODO" -> 0xFFA3E635; else -> 0xFF4ADE80
    }.toInt()
    fun icon(k: String): Int = when (k) {
        "LECTURE" -> R.drawable.i_book; "AUTO", "TASK" -> R.drawable.i_pencil; "SPORT" -> R.drawable.i_sport; "TODO" -> R.drawable.i_check; else -> R.drawable.i_sun
    }
}

object Rem {
    private val KINDS = setOf("LECTURE", "SPORT", "TASK", "AUTO")
    // 30 Min vorher für alle Termine; Start + "erledigt?"-Nachfrage zusätzlich für Aufgaben und Übungen (nächste 14 Tage)
    fun schedule(c: Context, items: List<Item>) {
        val am = c.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis(); val horizon = now + 14 * 86400000L
        for (i in items) {
            if (i.done || i.kind !in KINDS || i.start > horizon) continue
            set(c, am, i, 2, i.start - 30 * 60000L, now)
            if (i.kind == "TASK" || i.kind == "AUTO") { set(c, am, i, 0, i.start, now); set(c, am, i, 1, i.end, now) }
        }
    }
    private fun set(c: Context, am: AlarmManager, i: Item, t: Int, at: Long, now: Long) {
        if (at <= now) return
        val pi = PendingIntent.getBroadcast(c, (i.id % 1000000).toInt() * 3 + t,
            Intent(c, Rx::class.java).putExtra("id", i.id).putExtra("t", t), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        catch (e: Exception) { try { am.set(AlarmManager.RTC_WAKEUP, at, pi) } catch (e2: Exception) { } }
    }
}

class Rx : BroadcastReceiver() {
    private fun nid(id: Long, t: Int) = (id % 100000).toInt() * 3 + t
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        val all = Store.load(c); val item = all.find { x -> x.id == id } ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sp", "Studienplaner", NotificationManager.IMPORTANCE_HIGH))
        when (i.action) {
            "DONE" -> { item.done = true; Store.save(c, all); nm.cancel(nid(id, 1)); return }
            "MORE" -> { item.end += 30 * 60000L; Store.save(c, all); Rem.schedule(c, listOf(item)); nm.cancel(nid(id, 1)); return }
        }
        if (item.done) return
        val t = i.getIntExtra("t", 0)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(c, "sp").setSmallIcon(R.drawable.i_check).setAutoCancel(true).setContentIntent(open)
        when (t) {
            2 -> b.setContentTitle("In 30 Min: " + item.title).setContentText(Look.label(item))
            0 -> b.setContentTitle("Jetzt: " + item.title).setContentText(Look.label(item))
            else -> b.setContentTitle(item.title + " erledigt?").setContentText("Oder brauchst du mehr Zeit?")
                .addAction(0, "Erledigt", act(c, "DONE", id)).addAction(0, "+30 Min", act(c, "MORE", id))
        }
        nm.notify(nid(id, t), b.build())
    }
    private fun act(c: Context, a: String, id: Long) = PendingIntent.getBroadcast(c, (id % 1000000).toInt() * 3 + if (a == "DONE") 0 else 1,
        Intent(c, Rx::class.java).setAction(a).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Rem.schedule(c, Store.load(c))
}
