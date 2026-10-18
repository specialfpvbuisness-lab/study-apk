package de.studyplan

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
    private val LINE = 0xFF262B38.toInt()
    private val ACC = 0xFF7C5CFF.toInt()
    private val ACC2 = 0xFF22D3EE.toInt()
    private val TX = 0xFFF2F4F8.toInt()
    private val MU = 0xFF8B93A5.toInt()
    private val DOABLE = setOf("TASK", "AUTO", "SPORT", "TODO")
    private val FT = listOf("Morgens", "Mittags", "Abends")
    private val FD = listOf(60, 90, 120, 180)
    private val DS = listOf(6, 7, 8, 9, 10)
    private val DE = listOf(18, 19, 20, 21, 22)
    private val DR = listOf(30, 45, 60, 90, 120)
    private var ft = 2; private var fdI = 1; private var dsI = 0; private var deI = 4; private var drI = 1
    private var link = ""
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
        if (Prefs.s(ctx, "onb") != "1") welcome()
        else {
            val stale = System.currentTimeMillis() - (Prefs.s(ctx, "plan_t", "0").toLongOrNull() ?: 0L) > 12 * 3600000L
            if (stale) replan() else { Rem.schedule(ctx, Store.load(ctx)); main() }
        }
    }

    // ---------- Helfer ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun shape(c: Int, r: Int = 16) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun grad(r: Int = 28) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).apply { cornerRadius = dp(r).toFloat() }
    private fun oval() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).apply { setShape(GradientDrawable.OVAL) }
    private fun tv(t: String, sz: Float = 15f, c: Int = TX, bold: Boolean = false) =
        TextView(ctx).apply { text = t; textSize = sz; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD) }
    private fun icon(res: Int, tint: Int, size: Int, pad: Int = 0, bg: Drawable? = null) = ImageView(ctx).apply {
        setImageResource(res); setColorFilter(tint); setPadding(dp(pad), dp(pad), dp(pad), dp(pad)); background = bg
        layoutParams = lp(dp(size), dp(size))
    }
    private fun btn(t: String, primary: Boolean = true, f: () -> Unit) = TextView(ctx).apply {
        text = t; textSize = 16f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else TX); setPadding(dp(24), dp(16), dp(24), dp(16))
        background = if (primary) grad(28) else shape(CARD, 28)
        layoutParams = lp(-1, -2).apply { topMargin = dp(12) }
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
                setPadding(0, dp(14), 0, dp(14)); layoutParams = lp(0, -2, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { paint(i); on(i) }
            }
            views.add(v); row.addView(v)
        }
        paint(sel); return row
    }

    // ---------- Startscreen & Onboarding ----------
    private fun welcome() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(32), 0, dp(32), dp(32)) }
        fun space() = View(ctx).apply { layoutParams = lp(-1, 0, 1f) }
        col.addView(space())
        col.addView(icon(R.drawable.i_note, Color.TRANSPARENT, 150, 30, grad(40)).apply {
            clearColorFilter(); elevation = dp(12).toFloat()
        })
        col.addView(tv("Studienplaner", 34f, TX, true).apply { setPadding(0, dp(32), 0, dp(8)) })
        col.addView(tv("Dein Semester. Dein Rhythmus.\nVorlesungen, Sport, Übungen und Freizeit – automatisch geplant.", 15f, MU).apply { gravity = Gravity.CENTER })
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER; setPadding(0, dp(28), 0, 0) }
        listOf(R.drawable.i_book to "LECTURE", R.drawable.i_sport to "SPORT", R.drawable.i_sun to "FREE", R.drawable.i_check to "TODO").forEach { (r, k) ->
            row.addView(icon(r, Look.color(k), 48, 12, shape(CARD, 24)).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(6), 0, dp(6), 0) })
        }
        col.addView(row)
        col.addView(space())
        col.addView(btn("Los geht's") { step1() })
        show(col)
    }

    private fun frame(step: Int, iconRes: Int, title: String, sub: String, body: View?, next: String,
                      skip: Pair<String, () -> Unit>? = null, onNext: () -> Unit) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(56), dp(24), dp(24)) }
        val dots = LinearLayout(ctx)
        for (i in 1..4) dots.addView(View(ctx).apply {
            background = if (i <= step) grad(4) else shape(CARD, 4); layoutParams = lp(0, dp(4), 1f).apply { marginEnd = dp(6) }
        })
        col.addView(dots)
        col.addView(icon(iconRes, Color.WHITE, 72, 20, oval()).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(36) })
        col.addView(tv(title, 30f, TX, true).apply { setPadding(0, dp(20), 0, 0) })
        col.addView(tv(sub, 15f, MU).apply { setPadding(0, dp(8), 0, dp(28)) })
        body?.let { col.addView(it) }
        col.addView(View(ctx).apply { layoutParams = lp(-1, 0, 1f) })
        col.addView(btn(next, true, onNext))
        skip?.let { col.addView(btn(it.first, false, it.second)) }
        show(ScrollView(ctx).apply { isFillViewport = true; addView(col, ViewGroup.LayoutParams(-1, -1)) })
    }

    private fun step1() {
        val f = EditText(ctx).apply {
            hint = "webcal://… oder https://…"; setText(link); setSingleLine(); setTextColor(TX); setHintTextColor(MU)
            setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(CARD, 14)
        }
        frame(1, R.drawable.i_cal, "Dein Stundenplan", "Füge den iCloud-Export-Link deiner Hochschule ein. Daraus plane ich deine Übungszeiten.",
            f, "Weiter", "Ohne Link fortfahren" to { link = ""; step2() }) { link = f.text.toString().trim(); step2() }
    }

    private fun step2() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(chips(DR.map { "$it" }, drI) { drI = it })
        b.addView(tv("Minuten pro Vorlesung", 13f, MU).apply { setPadding(dp(4), dp(10), 0, 0) })
        frame(2, R.drawable.i_pencil, "Übungszeit", "Wie lange brauchst du nach jeder Vorlesung zum Üben? Ich trage den Block automatisch direkt danach ein – passt er nicht in den Tag, rutscht er so früh wie möglich auf den nächsten.",
            b, "Weiter") { step3() }
    }

    private fun step3() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(tv("Wann hast du am liebsten Freizeit?", 15f, MU).apply { setPadding(0, 0, 0, dp(10)) })
        b.addView(chips(FT, ft) { ft = it })
        b.addView(tv("Wie viel pro Tag?", 15f, MU).apply { setPadding(0, dp(28), 0, dp(10)) })
        b.addView(chips(FD.map { if (it % 60 == 0) "${it / 60} Std" else "${it / 60},5 Std" }, fdI) { fdI = it })
        frame(3, R.drawable.i_sun, "Deine Freizeit", "Ich blocke sie jeden Tag fest ein – Übungen kommen dir nie dazwischen.", b, "Weiter") { step4() }
    }

    private fun step4() {
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(tv("Dein Tag beginnt um …", 15f, MU).apply { setPadding(0, 0, 0, dp(10)) })
        b.addView(chips(DS.map { "$it:00" }, dsI) { dsI = it })
        b.addView(tv("… und endet um", 15f, MU).apply { setPadding(0, dp(28), 0, dp(10)) })
        b.addView(chips(DE.map { "$it:00" }, deI) { deI = it })
        frame(4, R.drawable.i_clock, "Dein Tag", "Alles wird zwischen diesen Zeiten geplant – frühestens 6, spätestens 22 Uhr.", b, "Plan erstellen") { finishOnboarding() }
    }

    private fun finishOnboarding() {
        Prefs.put(ctx, "url", link); Prefs.put(ctx, "ft", ft.toString()); Prefs.put(ctx, "fd", FD[fdI].toString())
        Prefs.put(ctx, "ds", DS[dsI].toString()); Prefs.put(ctx, "de", DE[deI].toString())
        Prefs.put(ctx, "dur", DR[drI].toString()); Prefs.put(ctx, "onb", "1")
        replan()
    }

    private fun replan() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(ProgressBar(ctx))
        col.addView(tv("Dein Plan wird erstellt …", 16f, MU).apply { setPadding(0, dp(16), 0, 0) })
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
            Planner.autoPlan(ctx, all); Store.save(ctx, all); Rem.schedule(ctx, all); Prefs.put(ctx, "plan_t", System.currentTimeMillis().toString())
            runOnUiThread { main(); err?.let { toast(it) } }
        }.start()
    }

    // ---------- Hauptscreen ----------
    private fun main() {
        val all = Store.load(ctx)
        val from = Planner.at(System.currentTimeMillis(), 0)
        val root = FrameLayout(ctx)
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(52), dp(20), dp(12)) }
        val tcol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = lp(0, -2, 1f) }
        tcol.addView(tv("Heute", 32f, TX, true))
        tcol.addView(tv(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date()), 14f, MU))
        head.addView(tcol)
        head.addView(icon(R.drawable.i_refresh, TX, 44, 11, shape(CARD, 22)).apply { setOnClickListener { replan() }; (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8) })
        head.addView(icon(R.drawable.i_sliders, TX, 44, 11, shape(CARD, 22)).apply { setOnClickListener { step1() }; (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8) })
        content.addView(head)

        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(110)) }
        content.addView(ScrollView(ctx).apply { addView(box); layoutParams = lp(-1, 0, 1f) })

        val doable = Planner.today(all).filter { it.kind in DOABLE }
        if (doable.isNotEmpty()) {
            val done = doable.count { it.done }
            val sum = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; background = shape(CARD, 22); setPadding(dp(18), dp(16), dp(18), dp(18))
                layoutParams = lp(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4) }
            }
            sum.addView(tv("$done von ${doable.size} erledigt", 16f, TX, true).apply { setPadding(0, 0, 0, dp(12)) })
            val bar = LinearLayout(ctx).apply { background = shape(LINE, 4); layoutParams = lp(-1, dp(8)) }
            bar.addView(View(ctx).apply { background = grad(4); layoutParams = lp(0, -1, done.toFloat()) })
            bar.addView(View(ctx).apply { layoutParams = lp(0, -1, (doable.size - done).toFloat()) })
            sum.addView(bar); box.addView(sum)
        }

        val shown = all.filter { (it.end >= from || (it.kind == "TODO" && !it.done)) && Planner.eff(it, from) < from + 7 * 86400000L }
            .sortedWith(compareBy({ Planner.at(Planner.eff(it, from), 0) }, { if (it.kind == "TODO") 1 else 0 }, { it.start }))
        if (shown.isEmpty()) box.addView(tv("Noch nichts geplant.\nTippe auf ＋ oder importiere deinen Stundenplan mit ↻.", 15f, MU).apply { setPadding(0, dp(32), 0, 0) })
        else box.addView(tv("Tippen = erledigt · Lange drücken = löschen", 12f, MU).apply { setPadding(dp(4), dp(12), 0, 0) })
        var last = ""
        for (i in shown) {
            val ed = Planner.eff(i, from)
            val dn = ((Planner.at(ed, 0) - from + 3600000L) / 86400000L).toInt()
            val d = when (dn) { 0 -> "Heute"; 1 -> "Morgen"; else -> df.format(Date(ed)) }
            if (d != last) { last = d; box.addView(tv(d.uppercase(Locale.GERMANY), 13f, MU, true).apply { setPadding(dp(4), dp(24), 0, dp(10)) }) }
            box.addView(card(i, all))
        }

        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(icon(R.drawable.i_plus, Color.WHITE, 62, 17, oval()).apply { elevation = dp(10).toFloat(); setOnClickListener { menu() } },
            FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(24), dp(28)) })
        show(root)
    }

    private fun card(i: Item, all: MutableList<Item>): View {
        val kc = Look.color(i.kind)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(CARD, 20)
            setPadding(dp(14), dp(12), dp(16), dp(12)); alpha = if (i.done) 0.5f else 1f
            layoutParams = lp(-1, -2).apply { bottomMargin = dp(10) }
        }
        row.addView(icon(Look.icon(i.kind), kc, 46, 11, shape((kc and 0x00FFFFFF) or 0x2E000000, 15)))
        val txt = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = lp(0, -2, 1f).apply { marginStart = dp(14) } }
        txt.addView(tv(Look.label(i), 12f, MU))
        txt.addView(tv(i.title, 16f, TX, true).apply { if (i.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG })
        row.addView(txt)
        if (i.kind in DOABLE) {
            row.addView(icon(if (i.done) R.drawable.i_check else R.drawable.i_ring, if (i.done) kc else MU, 28))
            row.setOnClickListener { i.done = !i.done; Store.save(ctx, all); main() }
        }
        row.setOnLongClickListener { all.remove(i); Store.save(ctx, all); main(); true }
        return row
    }

    private fun menu() {
        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert).setTitle("Hinzufügen")
            .setItems(arrayOf("Aufgabe mit Zeit", "Sport / Training", "Freizeit", "To-do")) { _, w ->
                addDialog(when (w) { 0 -> "TASK"; 1 -> "SPORT"; 2 -> "FREE"; else -> "TODO" })
            }.show()
    }

    private fun addDialog(kind: String) {
        val lay = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun et(h: String, v: String = "") = EditText(ctx).apply { hint = h; setText(v); setSingleLine(); setTextColor(TX); setHintTextColor(MU) }
        val t = et(when (kind) { "TASK" -> "Aufgabe"; "SPORT" -> "Training / Veranstaltung"; "FREE" -> "Freizeit (z. B. Kino)"; else -> "To-do (z. B. Wäsche waschen)" })
        val tm = et("Start: HH:mm oder TT.MM. HH:mm"); val len = et("Dauer (Min)", "60")
        val weekly = CheckBox(ctx).apply { text = "Jede Woche wiederholen (8 Wochen)"; setTextColor(TX) }
        lay.addView(t)
        if (kind != "TODO") { lay.addView(tm); lay.addView(len) }
        if (kind == "SPORT") lay.addView(weekly)
        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(when (kind) { "TASK" -> "Neue Aufgabe"; "SPORT" -> "Neuer Sport-Termin"; "FREE" -> "Neue Freizeit"; else -> "Neues To-do" }).setView(lay)
            .setPositiveButton("Hinzufügen") { _, _ -> add(kind, t.text.toString(), tm.text.toString(), len.text.toString(), weekly.isChecked) }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun add(kind: String, title: String, time: String, len: String, weekly: Boolean) {
        if (title.isBlank()) { toast("Bitte einen Titel eingeben"); return }
        val all = Store.load(ctx)
        if (kind == "TODO") {
            val d0 = Planner.at(System.currentTimeMillis(), 0)
            all.add(Item(Planner.id(), title.trim(), d0, d0, "TODO")); Store.save(ctx, all); main(); return
        }
        val st = parse(time); val m = len.toIntOrNull()
        if (st == null || m == null) { toast("Startzeit und Dauer prüfen"); return }
        val fixed = all.filter { it.kind != "AFREE" && !(it.kind == "AUTO" && !it.done) }
        var n = 0
        for (k in 0 until (if (weekly) 8 else 1)) {
            val s = Calendar.getInstance().apply { timeInMillis = st; add(Calendar.DAY_OF_YEAR, 7 * k) }.timeInMillis
            val e = s + m * 60000L
            if (s < Planner.at(s, DS[dsI]) || e > Planner.at(s, DE[deI]) || Planner.overlaps(fixed, s, e)) continue
            all.add(Item(Planner.id(), title.trim(), s, e, kind)); n++
        }
        if (n == 0) { toast("Passt nicht ins Tagesfenster oder überschneidet sich"); return }
        Planner.autoPlan(ctx, all)   // Übungen und Freizeit rücken um den neuen Termin herum
        Store.save(ctx, all); Rem.schedule(ctx, all)
        if (weekly && n < 8) toast("$n von 8 Terminen eingetragen (Rest kollidiert)")
        main()
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
