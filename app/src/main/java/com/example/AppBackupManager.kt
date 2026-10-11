package com.example

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppBackupManager {

    private const val TAG = "AppBackupManager"

    /**
     * Automatically backs up the running application's APK to the public Downloads folder.
     * On Android 10+ (API 29+), uses MediaStore.Downloads.
     * On legacy Android, uses Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).
     */
    suspend fun performAutoBackup(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Source APK file of the installed app
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceApk = File(sourceApkPath)

            if (!sourceApk.exists() || sourceApk.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Source APK file not found at $sourceApkPath"))
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFileName = "HeadUnitGPS_Backup_${timestamp}.apk"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore API for Android 10+ (No WRITE_EXTERNAL_STORAGE required for app's own MediaStore files)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HeadUnitGPS")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaStore entry in Downloads"))

                resolver.openOutputStream(itemUri)?.use { outputStream ->
                    FileInputStream(sourceApk).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Failed to open output stream to MediaStore URI"))

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                val displayPath = "Downloads/HeadUnitGPS/$backupFileName"
                Log.i(TAG, "Successfully backed up app APK to $displayPath")
                Result.success(displayPath)
            } else {
                // Android 9 and lower
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFolder = File(downloadsDir, "HeadUnitGPS")
                if (!targetFolder.exists()) {
                    targetFolder.mkdirs()
                }

                val targetFile = File(targetFolder, backupFileName)
                FileInputStream(sourceApk).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                val displayPath = targetFile.absolutePath
                Log.i(TAG, "Successfully backed up app APK to $displayPath")
                Result.success(displayPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing auto backup of APK", e)
            Result.failure(e)
        }
    }
}
