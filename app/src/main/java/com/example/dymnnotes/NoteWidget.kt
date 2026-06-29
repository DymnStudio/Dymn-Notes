package com.example.dymnnotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

class NoteWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_TODO) {
            val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
            val lineIndex = intent.getIntExtra(EXTRA_LINE_INDEX, -1)
            if (lineIndex >= 0) toggleTodo(context, noteId, lineIndex)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateAppWidget(context, manager, id)
    }

    private fun toggleTodo(context: Context, noteId: String, lineIndex: Int) {
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(NotesKey, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                if (note.optString("id") == noteId) {
                    val lines = note.optString("body").lines().toMutableList()
                    if (lineIndex in lines.indices) {
                        val line = lines[lineIndex]
                        lines[lineIndex] = when {
                            line.startsWith("- [ ]") -> "- [x] " + line.substring(5).trimStart()
                            line.startsWith("- [x]", ignoreCase = true) -> "- [ ] " + line.substring(5).trimStart()
                            else -> line
                        }
                        note.put("body", lines.joinToString("\n"))
                        note.put("updatedAt", System.currentTimeMillis())
                        prefs.edit().putString(NotesKey, array.toString()).apply()
                        refreshWidgets(context)
                    }
                    break
                }
            }
        }
    }

    companion object {
        private const val ActionTogglePrefix = "com.example.dymnnotes.widget.TOGGLE_TODO"
        const val ACTION_TOGGLE_TODO = ActionTogglePrefix
        private const val EXTRA_NOTE_ID = "note_id"
        private const val EXTRA_LINE_INDEX = "line_index"
        private const val PrefsName = "dymn_notes"
        private const val NotesKey = "notes"
        private val itemIds = intArrayOf(R.id.widget_item_0, R.id.widget_item_1, R.id.widget_item_2, R.id.widget_item_3, R.id.widget_item_4)

        fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.note_widget_layout)
            val note = findWidgetNote(context)

            if (note == null) {
                bindEmpty(views)
            } else {
                bindNote(context, views, id, note)
            }

            val openIntent = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            views.setOnClickPendingIntent(R.id.widget_title, openIntent)
            views.setOnClickPendingIntent(R.id.widget_edit_button, openIntent)
            manager.updateAppWidget(id, views)
        }

        private fun findWidgetNote(context: Context): JSONObject? {
            val raw = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).getString(NotesKey, null)
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val array = JSONArray(raw)
                val notes = List(array.length()) { array.getJSONObject(it) }
                    .filter { !it.optBoolean("archived") }
                notes.firstOrNull { it.optString("kind") == "list" && it.optString("body").isNotBlank() }
                    ?: notes.firstOrNull()
            }.getOrNull()
        }

        private fun bindEmpty(views: RemoteViews) {
            views.setTextViewText(R.id.widget_title, "Dymn Notes")
            views.setTextViewText(R.id.widget_info, "Немає нотаток")
            views.setTextViewText(R.id.widget_edit_button, "Створити")
            itemIds.forEachIndexed { index, viewId ->
                views.setViewVisibility(viewId, if (index == 0) View.VISIBLE else View.GONE)
                if (index == 0) views.setTextViewText(viewId, "Відкрий додаток і створи першу нотатку")
            }
        }

        private fun bindNote(context: Context, views: RemoteViews, widgetId: Int, note: JSONObject) {
            val noteId = note.optString("id")
            val title = note.optString("title").ifBlank { "Без назви" }
            val bodyLines = note.optString("body").lines()
            val visibleLines = bodyLines
                .mapIndexedNotNull { index, line -> line.takeIf { it.isNotBlank() }?.let { index to it } }
                .take(itemIds.size)
            val totalTasks = bodyLines.count { it.startsWith("- [ ]") || it.startsWith("- [x]", ignoreCase = true) }
            val doneTasks = bodyLines.count { it.startsWith("- [x]", ignoreCase = true) }

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_info, if (totalTasks > 0) "Список · $doneTasks/$totalTasks" else "Нотатка")
            views.setTextViewText(R.id.widget_edit_button, "Редагувати")

            itemIds.forEachIndexed { index, viewId ->
                if (index < visibleLines.size) {
                    val (lineIndex, rawLine) = visibleLines[index]
                    val done = rawLine.startsWith("- [x]", ignoreCase = true)
                    val isTask = rawLine.startsWith("- [ ]") || done
                    val cleanText = if (isTask) rawLine.substring(5).trim() else rawLine.trim()
                    val marker = when {
                        done -> "✓ "
                        isTask -> "○ "
                        else -> ""
                    }

                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, marker + cleanText)

                    if (isTask) {
                        val toggleIntent = Intent(context, NoteWidget::class.java).apply {
                            action = ACTION_TOGGLE_TODO
                            putExtra(EXTRA_NOTE_ID, noteId)
                            putExtra(EXTRA_LINE_INDEX, lineIndex)
                        }
                        views.setOnClickPendingIntent(
                            viewId,
                            PendingIntent.getBroadcast(
                                context,
                                widgetId * 100 + index,
                                toggleIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                    }
                } else {
                    views.setViewVisibility(viewId, View.GONE)
                }
            }
        }

        private fun refreshWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
            ids.forEach { updateAppWidget(context, manager, it) }
        }
    }
}
