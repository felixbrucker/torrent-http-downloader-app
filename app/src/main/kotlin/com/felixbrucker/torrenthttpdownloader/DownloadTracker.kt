package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DownloadTracker {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val gson = Gson()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val sharedPreferences = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("tasks", null)
        if (json != null) {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            _tasks.value = gson.fromJson(json, type)
        }
    }

    fun saveTasks() {
        val sharedPreferences = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val json = gson.toJson(_tasks.value)
        sharedPreferences.edit { putString("tasks", json) }
    }

    fun getTasks(): List<DownloadTask> {
        return _tasks.value
    }

    fun addTask(task: DownloadTask) {
        _tasks.update { it + task }
        saveTasks()
    }

    fun findTask(id: String): DownloadTask? {
        return _tasks.value.find { it.id == id }
    }

    fun removeTask(id: String) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
        saveTasks()
    }

    fun replaceTask(oldId: String, newTask: DownloadTask) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == oldId) newTask else it }
        }
        saveTasks()
    }

    fun updateTask(id: String, update: (DownloadTask) -> DownloadTask) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) update(it) else it }
        }
        saveTasks()
    }
}