package de.studyplan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.text.SimpleDateFormat
import java.util.*

object Widget {
    fun refresh(c: Context) {
        try {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, TodayWidget::class.java))
            if (ids.isNotEmpty()) TodayWidget.update(c, m, ids)
        } catch (e: Exception) { }
    }
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = update(c, m, ids)

    override fun onReceive(c: Context, i: Intent) {
        if (i.action == "TOGGLE") {
            val id = i.getLongExtra("id", 0); val all = Store.load(c)
            all.find { x -> x.id == id }?.let { x -> x.done = !x.done; Store.save(c, all) }
        } else super.onReceive(c, i)
    }

    companion object {
        fun update(c: Context, m: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                val rv = RemoteViews(c.packageName, R.layout.widget)
                rv.setTextViewText(R.id.w_date, SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date()))
                rv.setRemoteAdapter(R.id.w_list, Intent(c, WidgetService::class.java))
                rv.setEmptyView(R.id.w_list, R.id.w_empty)
                rv.setPendingIntentTemplate(R.id.w_list, PendingIntent.getBroadcast(c, 0,
                    Intent(c, TodayWidget::class.java).setAction("TOGGLE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
                rv.setOnClickPendingIntent(R.id.w_title, PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                m.updateAppWidget(id, rv)
            }
            m.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(i: Intent): RemoteViewsFactory = Factory(applicationContext)

    class Factory(private val c: Context) : RemoteViewsFactory {
        private var items = listOf<Item>()
        override fun onCreate() {}
        override fun onDataSetChanged() { items = Planner.today(Store.load(c)) }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getViewAt(p: Int): RemoteViews {
            val i = items[p]
            val rv = RemoteViews(c.packageName, R.layout.widget_item)
            rv.setImageViewResource(R.id.wi_icon, Look.icon(i.kind))
            rv.setInt(R.id.wi_icon, "setColorFilter", if (i.done) 0xFF6B7385.toInt() else Look.color(i.kind))
            rv.setTextViewText(R.id.wi_time, Look.label(i))
            rv.setTextViewText(R.id.wi_title, (if (i.done) "✓ " else "") + i.title)
            rv.setTextColor(R.id.wi_title, if (i.done) 0xFF6B7385.toInt() else 0xFFF2F4F8.toInt())
            rv.setOnClickFillInIntent(R.id.wi_root, Intent().putExtra("id", i.id))
            return rv
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(p: Int) = items[p].id
        override fun hasStableIds() = true
    }
}
