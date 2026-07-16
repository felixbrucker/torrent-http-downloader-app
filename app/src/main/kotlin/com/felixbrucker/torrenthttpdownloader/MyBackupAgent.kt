package com.felixbrucker.torrenthttpdownloader

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import java.io.File

class MyBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        // We use full backup as defined in the manifest
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        // We use full backup as defined in the manifest
    }

    override fun onFullBackup(data: FullBackupDataOutput?) {
        val settingsFile = File(dataDir, "shared_prefs/settings.xml")
        val size = if (settingsFile.exists()) settingsFile.length() else 0L

        getSharedPreferences("settings", MODE_PRIVATE).edit(commit = true) {
            putLong("last_backup_time", System.currentTimeMillis())
            putLong("last_backup_size", size)
        }
        super.onFullBackup(data)
    }
}
