package de.studyplan

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val ctx get() = this
    private val BG = 0xFF0E1015.toInt()
    private val CARD = 0xFF181B23.toInt()
    private val ACC = 0xFF7C5CFF.toInt()
    private val ACC2 = 0xFF22D3EE.toInt()
    private val TX = 0xFFF2F4F8.toInt()
    private val MU = 0xFF8B93A5.toInt()
    private val FT = listOf("Morgens", "Mittags", "Abends")
    private val FD = listOf(60, 90, 120, 180)
    private val DS = listOf(6, 7, 8, 9, 10)
    private val DE = listOf(18, 19, 20, 21, 22)
    private val DR = listOf(30, 45, 60, 90)
    private var ft = 2; private var fdI = 1; private var dsI = 0; private var deI = 4; private var drI = 1
    private var link = ""
    private val tf = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val df = SimpleDateFormat("EEEE, dd.MM.", Locale.GERMANY)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG; window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        link = Prefs.s(ctx, "url")
        ft = Prefs.int(ctx, "ft", 2).coerceIn(0, 2)
        fdI = FD.indexOf(Prefs.int(ctx, "fd", 90)).coerceAtLeast(0)
        dsI = DS.indexOf(Prefs.int(ctx, "ds", 6)).coerceAtLeast(0)
        deI = DE.indexOf(Prefs.int(ctx, "de", 22)).let { if (it < 0) 4 else it }
        drI = DR.indexOf(Prefs.int(ctx, "dur", 45)).let { if (it < 0) 1 else it }
        if (Prefs.s(ctx, "onb") == "1") { Rem.schedule(ctx, Store.load(ctx)); main() } else welcome()
    }

    // ---------- Helfer ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(c: Int, r: Int = 16) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun grad(r: Int = 28) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).apply { cornerRadius = dp(r).toFloat() }
    private fun tv(t: String, sz: Float = 15f, c: Int = TX, bold: Boolean = false) =
        TextView(ctx).apply { text = t; textSize = sz; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD) }
    private fun btn(t: String, primary: Boolean = true, f: () -> Unit) = TextView(ctx).apply {
        text = t; textSize = 16f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else TX); setPadding(dp(24), dp(16), dp(24), dp(16))
        background = if (primary) grad(28) else shape(CARD, 28)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        setOnClickListener { f() }
    }
    private fun show(v: View) { v.setBackgroundColor(BG); setContentView(v) }
    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    private fun chips(opts: List<String>, sel: Int, on: (Int) -> Unit): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val views = ArrayList<TextView>()
        fun paint(s: Int) = views.forEachIndexed { i, v -> v.background = if (i == s) grad(14) else shape(CARD, 14); v.setTextColor(if (i == s) Color.WHITE else MU) }
        opts.forEachIndexed { i, o ->
            val v = TextView(ctx).apply {
                text = o; gravity = Gravity.CENTER; textSize = 15f; setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { paint(i); on(i) }
            }
            views.add(v); row.addView(v)
        }
        paint(sel); return row
    }

    // ---------- Startscreen & Onboarding ----------
    private fun welcome() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(32), 0, dp(32), dp(32)) }
        fun space() = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        col.addView(space())
        col.addView(TextView(ctx).apply {
            text = "🎓"; textSize = 56f; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(132), dp(132))
        })
        col.addView(tv("Studienplaner", 34f, TX, true).apply { setPadding(0, dp(32), 0, dp(8)) })
        col.addView(tv("Dein Semester. Dein Rhythmus.\nVorlesungen, Übungen und Freizeit – automatisch geplant.", 15f, MU).apply { gravity = Gravity.CENTER })
        col.addView(space())
        col.addView(btn("Los geht's") { step1() })
        show(col)
    }

    private fun frame(step: Int, emoji: String, title: String, sub: String, body: View?, next: String,
                      skip: Pair<String, () -> Unit>? = null, onNext: () -> Unit) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(56), dp(24), dp(24)) }
        val dots = LinearLayout(ctx)
        for (i in 1..4) dots.addView(View(ctx).apply {
            background = if (i <= step) grad(4) else shape(CARD, 4)
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f).apply { marginEnd = dp(6) }
        })
        col.addView(dots)
        col.addView(tv(emoji, 48f).apply { setPadding(0, dp(40), 0, dp(8)) })
        col.addView(tv(title, 28f, TX, true))
        col.addView(tv(sub, 15f, MU).apply { setPadding(0, dp(8), 0, dp(28)) })
        body?.let { col.addView(it) }
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) })
        col.addView(btn(next, true, onNext))
        skip?.let { col.addView(btn(it.first, false, it.second)) }
        show(ScrollView(ctx).apply { isFillViewport = true; addView(col, ViewGroup.LayoutParams(-1, -1)) })
    }

    private fun step1() {
        val f = EditText(ctx).apply {
            hint = "webcal://… oder https://…"; setText(link); setSingleLine(); setTextColor(TX); setHintTextColor(MU)
            setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(CARD, 14)
        }
        frame(1, "📅", "Dein Stundenplan", "Füge den iCloud-Export-Link deiner Hochschule ein. Daraus plane ich deine Übungszeiten.",
            f, "Weiter", "Ohne Link fortfahren" to { link = ""; step2() }) { link = f.text.toString().trim(); step2() }
    }

    private fun step2() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(tv("Wann hast du am liebsten Freizeit?", 15f, MU).apply { setPadding(0, 0, 0, dp(10)) })
        b.addView(chips(FT, ft) { ft = it })
        b.addView(tv("Wie viel pro Tag?", 15f, MU).apply { setPadding(0, dp(28), 0, dp(10)) })
        b.addView(chips(FD.map { if (it % 60 == 0) "${it / 60} Std" else "${it / 60},5 Std" }, fdI) { fdI = it })
        frame(2, "🌴", "Deine Freizeit", "Ich blocke sie jeden Tag fest ein – Übungen kommen dir nie dazwischen.", b, "Weiter") { step3() }
    }

    private fun step3() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(tv("Dein Tag beginnt um …", 15f, MU).apply { setPadding(0, 0, 0, dp(10)) })
        b.addView(chips(DS.map { "$it:00" }, dsI) { dsI = it })
        b.addView(tv("… und endet um", 15f, MU).apply { setPadding(0, dp(28), 0, dp(10)) })
        b.addView(chips(DE.map { "$it:00" }, deI) { deI = it })
        frame(3, "⏰", "Dein Tag", "Alles wird zwischen diesen Zeiten geplant – frühestens 6, spätestens 22 Uhr.", b, "Weiter") { step4() }
    }

    private fun step4() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(chips(DR.map { "$it Min" }, drI) { drI = it })
        frame(4, "✏️", "Übungszeit", "Wie lange möchtest du nach jeder Vorlesung üben?", b, "Plan erstellen") { finishOnboarding() }
    }

    private fun finishOnboarding() {
        Prefs.put(ctx, "url", link); Prefs.put(ctx, "ft", ft.toString()); Prefs.put(ctx, "fd", FD[fdI].toString())
        Prefs.put(ctx, "ds", DS[dsI].toString()); Prefs.put(ctx, "de", DE[deI].toString())
        Prefs.put(ctx, "dur", DR[drI].toString()); Prefs.put(ctx, "onb", "1")
        replan()
    }

    private fun replan() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(tv("✨", 48f).apply { gravity = Gravity.CENTER })
        col.addView(tv("Dein Plan wird erstellt …", 18f, MU).apply { setPadding(0, dp(16), 0, 0) })
        show(col)
        Thread {
            var err: String? = null
            val all = Store.load(ctx)
            try {
                val u = Prefs.s(ctx, "url")
                if (u.isNotBlank()) {
                    val ev = Ics.parse(Ics.fetch(u))
                    all.removeAll { x -> x.kind == "LECTURE" }
                    ev.forEach { e -> all.add(Item(Planner.id(), e.first, e.second, e.third, "LECTURE")) }
                }
            } catch (e: Exception) { err = "Import fehlgeschlagen: ${e.message}" }
            Planner.autoPlan(ctx, all); Store.save(ctx, all); Rem.schedule(ctx, all)
            runOnUiThread { main(); err?.let { toast(it) } }
        }.start()
    }

    // ---------- Hauptscreen ----------
    private fun main() {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(48), dp(16), dp(8)) }
        val tcol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tcol.addView(tv("Dein Plan", 28f, TX, true))
        tcol.addView(tv(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date()), 14f, MU))
        head.addView(tcol)
        fun icon(t: String, f: () -> Unit) = TextView(ctx).apply {
            text = t; textSize = 20f; gravity = Gravity.CENTER; setTextColor(TX); background = shape(CARD, 22)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) }
            setOnClickListener { f() }
        }
        head.addView(icon("↻") { replan() }); head.addView(icon("⚙") { step1() })
        root.addView(head)

        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(16)) }
        root.addView(ScrollView(ctx).apply { addView(box); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) })

        val all = Store.load(ctx)
        val from = Planner.at(System.currentTimeMillis(), 0)
        val shown = all.filter { it.end >= from && it.start < from + 7 * 86400000L }.sortedBy { it.start }
        if (shown.isEmpty()) box.addView(tv("Noch nichts geplant.\nTippe unten auf „Aufgabe“ oder importiere deinen Stundenplan mit ↻.", 15f, MU).apply { setPadding(0, dp(32), 0, 0) })
        else box.addView(tv("Tippen = erledigt · Lange drücken = löschen", 12f, MU).apply { setPadding(dp(4), dp(8), 0, 0) })
        var last = ""
        for (i in shown) {
            val dn = ((Planner.at(i.start, 0) - from + 3600000L) / 86400000L).toInt()
            val d = when (dn) { 0 -> "Heute"; 1 -> "Morgen"; else -> df.format(Date(i.start)) }
            if (d != last) { last = d; box.addView(tv(d.uppercase(Locale.GERMANY), 13f, MU, true).apply { setPadding(dp(4), dp(24), 0, dp(8)) }) }
            val kc = when (i.kind) { "LECTURE" -> ACC; "AUTO" -> ACC2; "TASK" -> 0xFFFFB84D.toInt(); else -> 0xFF4ADE80.toInt() }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; background = shape(CARD, 16); alpha = if (i.done) 0.45f else 1f
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            }
            card.addView(View(ctx).apply { background = shape(kc, 3); layoutParams = LinearLayout.LayoutParams(dp(5), -1).apply { setMargins(dp(12), dp(12), 0, dp(12)) } })
            val txt = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
            txt.addView(tv("${tf.format(Date(i.start))} – ${tf.format(Date(i.end))}", 13f, MU))
            txt.addView(tv(i.title, 16f, TX, true).apply { if (i.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG })
            card.addView(txt)
            card.setOnClickListener { i.done = !i.done; Store.save(ctx, all); main() }
            card.setOnLongClickListener { all.remove(i); Store.save(ctx, all); main(); true }
            box.addView(card)
        }

        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), dp(8), dp(24), dp(24)) }
        bar.addView(btn("＋ Aufgabe") { addDialog("TASK") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) } })
        bar.addView(btn("＋ Freizeit", false) { addDialog("FREE") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) } })
        root.addView(bar)
        show(root)
    }

    private fun addDialog(kind: String) {
        val lay = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun et(h: String, v: String = "") = EditText(ctx).apply { hint = h; setText(v); setSingleLine(); setTextColor(TX); setHintTextColor(MU) }
        val t = et(if (kind == "TASK") "Aufgabe" else "Freizeit (z. B. Sport)")
        val tm = et("Start: HH:mm oder TT.MM. HH:mm"); val len = et("Dauer (Min)", "60")
        listOf(t, tm, len).forEach { lay.addView(it) }
        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (kind == "TASK") "Neue Aufgabe" else "Neue Freizeit").setView(lay)
            .setPositiveButton("Hinzufügen") { _, _ -> add(kind, t.text.toString(), tm.text.toString(), len.text.toString()) }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun add(kind: String, title: String, time: String, len: String) {
        val st = parse(time); val m = len.toIntOrNull()
        if (st == null || m == null || title.isBlank()) { toast("Titel, Startzeit und Dauer prüfen"); return }
        val en = st + m * 60000L
        val all = Store.load(ctx)
        if (st < Planner.at(st, DS[dsI]) || en > Planner.at(st, DE[deI])) { toast("Außerhalb deines Tagesfensters"); return }
        if (Planner.overlaps(all, st, en)) { toast("Überschneidet sich mit einem anderen Eintrag"); return }
        val item = Item(Planner.id(), title.trim(), st, en, kind)
        all.add(item); Store.save(ctx, all); Rem.schedule(ctx, listOf(item)); main()
    }

    private fun parse(s: String): Long? = try {
        val t = s.trim(); val cal = Calendar.getInstance()
        if (t.contains('.')) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(t.replaceFirst(Regex("\\.\\s+"), ".${cal.get(Calendar.YEAR)} "))!!.time
        else {
            val d = Calendar.getInstance().apply { time = SimpleDateFormat("HH:mm", Locale.GERMANY).parse(t)!! }
            cal.set(Calendar.HOUR_OF_DAY, d.get(Calendar.HOUR_OF_DAY)); cal.set(Calendar.MINUTE, d.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); cal.timeInMillis
        }
    } catch (e: Exception) { null }
}
