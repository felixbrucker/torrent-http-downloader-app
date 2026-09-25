package com.felixbrucker.torrenthttpdownloader.data.repository

import com.felixbrucker.torrenthttpdownloader.data.database.DownloadDao
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    val tasksFlow: Flow<List<DownloadTask>>
        get() = downloadDao.getTasksFlow().map { list ->
            list.map { details -> details.toDomainModel() }
        }

    suspend fun getTaskById(id: String): DownloadTask? {
        val details = downloadDao.getTaskById(id) ?: return null
        return details.toDomainModel()
    }

    suspend fun saveTask(task: DownloadTask) {
        downloadDao.upsertTask(task)
    }

    suspend fun saveTasks(tasks: List<DownloadTask>) {
        for (task in tasks) {
            downloadDao.upsertTask(task)
        }
    }

    suspend fun deleteTask(id: String) {
        downloadDao.deleteTaskById(id)
    }

    suspend fun deleteAllTasks() {
        downloadDao.deleteAllTasks()
    }
}
