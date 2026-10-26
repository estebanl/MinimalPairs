package com.minimalpairs.app.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val directory = File(System.getProperty("user.home"), ".minimalpairs")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create progress storage directory" }
    val dbFile = File(directory, "minimal_pairs.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
}
