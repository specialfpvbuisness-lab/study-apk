package de.studyplan

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var box: LinearLayout
    private val ctx get() = this
    private val tf = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val df = SimpleDateFormat("EEEE, dd.MM.", Locale.GERMANY)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        fun et(h: String, v: String = "") = EditText(ctx).apply { hint = h; setText(v); setSingleLine() }
        fun btn(t: String, f: () -> Unit) = Button(ctx).apply { text = t; setOnClickListener { f() } }
        val url = et("iCloud-Link (webcal://…)", Prefs.s(ctx, "url"))
        val ds = et("Tagesbeginn (Uhr, ab 6)", Prefs.s(ctx, "ds", "6"))
        val de = et("Tagesende (Uhr, bis 22)", Prefs.s(ctx, "de", "22"))
        val dur = et("Übungsdauer je Vorlesung (Min)", Prefs.s(ctx, "dur", "45"))
        val title = et("Aufgabe / Freizeit")
        val time = et("Start: HH:mm oder TT.MM. HH:mm")
        val len = et("Dauer (Min)", "60")
        fun saveSettings() {
            Prefs.put(ctx, "url", url.text.toString()); Prefs.put(ctx, "ds", ds.text.toString())
            Prefs.put(ctx, "de", de.text.toString()); Prefs.put(ctx, "dur", dur.text.toString())
        }
        fun add(kind: String) {
            saveSettings()
            val st = parse(time.text.toString()); val m = len.text.toString().toIntOrNull()
            if (st == null || m == null || title.text.isBlank()) { toast("Titel, Startzeit und Dauer prüfen"); return }
            val en = st + m * 60000L
            val all = Store.load(ctx)
            if (st < Planner.at(st, Prefs.int(ctx, "ds", 6).coerceIn(6, 22)) || en > Planner.at(st, Prefs.int(ctx, "de", 22).coerceIn(6, 22))) { toast("Außerhalb des Tagesfensters"); return }
            if (Planner.overlaps(all, st, en)) { toast("Überschneidet sich mit einem anderen Eintrag"); return }
            val it = Item(Planner.id(), title.text.toString(), st, en, kind)
            all.add(it); Store.save(ctx, all); Rem.schedule(ctx, listOf(it)); title.setText(""); refresh()
        }
        listOf(url, ds, de, dur).forEach { root.addView(it) }
        root.addView(btn("Stundenplan importieren & Übungen planen") {
            saveSettings()
            Thread {
                try {
                    val ev = Ics.parse(Ics.fetch(url.text.toString()))
                    val all = Store.load(ctx)
                    all.removeAll { x -> x.kind == "LECTURE" || (x.kind == "AUTO" && !x.done) }
                    ev.forEach { e -> all.add(Item(Planner.id(), e.first, e.second, e.third, "LECTURE")) }
                    Planner.autoPlan(ctx, all); Store.save(ctx, all); Rem.schedule(ctx, all)
                    runOnUiThread { refresh(); toast("${ev.size} Termine importiert") }
                } catch (e: Exception) { runOnUiThread { toast("Fehler: ${e.message}") } }
            }.start()
        })
        listOf(title, time, len).forEach { root.addView(it) }
        root.addView(btn("Aufgabe hinzufügen") { add("TASK") })
        root.addView(btn("Freizeit eintragen") { add("FREE") })
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(box)
        setContentView(ScrollView(ctx).apply { addView(root) })
        Rem.schedule(ctx, Store.load(ctx))
        refresh()
    }

    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    private fun parse(s: String): Long? = try {
        val t = s.trim(); val cal = Calendar.getInstance()
        if (t.contains('.')) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(t.replaceFirst(Regex("\\.\\s+"), ".${cal.get(Calendar.YEAR)} "))!!.time
        else { val d = Calendar.getInstance().apply { time = SimpleDateFormat("HH:mm", Locale.GERMANY).parse(t)!! }
            cal.set(Calendar.HOUR_OF_DAY, d.get(Calendar.HOUR_OF_DAY)); cal.set(Calendar.MINUTE, d.get(Calendar.MINUTE)); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); cal.timeInMillis }
    } catch (e: Exception) { null }

    private fun refresh() {
        box.removeAllViews()
        val all = Store.load(ctx)
        val from = Planner.at(System.currentTimeMillis(), 0)
        var last = ""
        for (i in all.filter { it.end >= from && it.start < from + 7 * 86400000L }.sortedBy { it.start }) {
            val d = df.format(Date(i.start))
            if (d != last) { last = d; box.addView(TextView(ctx).apply { text = d; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, 24, 0, 8) }) }
            val icon = when (i.kind) { "LECTURE" -> "📚"; "FREE" -> "🌴"; else -> "✏️" }
            box.addView(TextView(ctx).apply {
                text = "$icon ${tf.format(Date(i.start))}–${tf.format(Date(i.end))}  ${i.title}" + if (i.done) "  ✓" else ""
                textSize = 15f; setPadding(0, 8, 0, 8)
                setOnClickListener { i.done = !i.done; Store.save(ctx, all); refresh() }
                setOnLongClickListener { all.remove(i); Store.save(ctx, all); refresh(); true }
            })
        }
    }
}
