package com.minimalpairs.app.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val dbFilePath = documentDirectory?.path + "/minimal_pairs.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}
