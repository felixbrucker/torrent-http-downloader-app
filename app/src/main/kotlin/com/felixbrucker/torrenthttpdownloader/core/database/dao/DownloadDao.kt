package com.felixbrucker.torrenthttpdownloader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFinalFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        LEFT JOIN download_files ON download_tasks.id = download_files.taskId
        ORDER BY download_tasks.displayOrder ASC, download_files.fileName ASC
        """
    )
    fun getTasksWithFilesFlow(): Flow<Map<DownloadTaskEntity, List<DownloadFileEntity>>>

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        LEFT JOIN download_files ON download_tasks.id = download_files.taskId
        ORDER BY download_tasks.displayOrder ASC, download_files.fileName ASC
        """
    )
    suspend fun getTasksWithFiles(): Map<DownloadTaskEntity, List<DownloadFileEntity>>

    @Query("SELECT id FROM download_tasks ORDER BY displayOrder ASC")
    suspend fun getTaskIds(): List<String>

    @Query("SELECT MAX(displayOrder) FROM download_tasks")
    suspend fun getMaxDisplayOrder(): Double?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM download_tasks WHERE state != 'ERROR' LIMIT 1)"
    )
    suspend fun hasTasksWhichNeedProcessing(): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM download_tasks WHERE state != 'ERROR' LIMIT 1)"
    )
    fun hasTasksWhichNeedProcessingFlow(): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE id = :id)")
    suspend fun hasTask(id: String): Boolean

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        LEFT JOIN download_files ON download_tasks.id = download_files.taskId
        WHERE download_tasks.id = :id
        ORDER BY download_files.fileName ASC
        """
    )
    suspend fun getTaskWithFiles(id: String): Map<DownloadTaskEntity, List<DownloadFileEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTask(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_files WHERE taskId = :taskId AND link = :fileLink")
    suspend fun getFile(taskId: String, fileLink: String): DownloadFileEntity?

    @Query("SELECT * FROM download_files WHERE taskId = :taskId")
    suspend fun getFilesForTask(taskId: String): List<DownloadFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinalFiles(files: List<DownloadFinalFileEntity>)

    @Query("SELECT filePath FROM download_final_files WHERE taskId = :taskId")
    suspend fun getFinalFilesForTask(taskId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DownloadFileEntity>)

    @Transaction
    suspend fun insertTaskWithFiles(task: DownloadTaskEntity, files: List<DownloadFileEntity>) {
        insertTask(task)
        if (files.isNotEmpty()) {
            insertFiles(files)
        }
    }

    @Query("UPDATE download_tasks SET displayOrder = :displayOrder WHERE id = :taskId")
    suspend fun updateTaskDisplayOrder(taskId: String, displayOrder: Double)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("UPDATE download_files SET state = :state, totalBytes = :totalBytes WHERE taskId = :taskId AND link = :fileLink")
    suspend fun updateFileDownloadingState(
        taskId: String,
        fileLink: String,
        state: LocalDownloadState = LocalDownloadState.DOWNLOADING,
        totalBytes: Long
    )

    @Query("UPDATE download_files SET state = :state, stateDescription = :stateDescription WHERE taskId = :taskId AND link = :fileLink")
    suspend fun updateFileState(
        taskId: String,
        fileLink: String,
        state: LocalDownloadState,
        stateDescription: String?
    )

    @Query("UPDATE download_tasks SET state = :state WHERE id = :taskId")
    suspend fun updateTaskState(taskId: String, state: TorrentState)

    @Query("UPDATE download_tasks SET state = :state, errorMessage = :errorMessage WHERE id = :taskId")
    suspend fun updateTaskError(
        taskId: String,
        errorMessage: String?,
        state: TorrentState = TorrentState.ERROR
    )

    @Query("UPDATE download_files SET totalBytes = :totalBytes, filePath = :filePath, fileName = :fileName, unrestrictedLink = :unrestrictedLink WHERE taskId = :taskId AND link = :fileLink")
    suspend fun updateTaskFileInfo(
        taskId: String,
        fileLink: String,
        totalBytes: Long,
        filePath: String,
        fileName: String,
        unrestrictedLink: String
    )

    @Query("UPDATE download_tasks SET providerId = :providerId, state = :state WHERE id = :taskId")
    suspend fun updateTaskProviderIdAndState(taskId: String, providerId: String, state: TorrentState)

    @Query("DELETE FROM download_files WHERE taskId = :taskId")
    suspend fun deleteFilesForTask(taskId: String)

    @Query("UPDATE download_tasks SET providerId = NULL, state = :state, errorMessage = NULL WHERE id = :taskId")
    suspend fun resetTaskStateForRetry(
        taskId: String,
        state: TorrentState = TorrentState.ADDING_TO_PROVIDER
    )

    @Transaction
    suspend fun resetTaskForRetry(
        taskId: String,
        state: TorrentState = TorrentState.ADDING_TO_PROVIDER
    ) {
        deleteFilesForTask(taskId)
        resetTaskStateForRetry(taskId, state)
    }

    @Query("UPDATE download_tasks SET name = :name WHERE id = :taskId")
    suspend fun updateTaskName(taskId: String, name: String)

    @Query("UPDATE download_tasks SET name = :torrentName WHERE id = :taskId AND name = torrent_uri")
    suspend fun updateTaskNameIfDefault(taskId: String, torrentName: String)
}
