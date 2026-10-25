package com.minimalpairs.app.data.local

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

object AndroidContextHolder {
    var context: Context? = null
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = AndroidContextHolder.context
        ?: throw IllegalStateException("AndroidContextHolder.context must be initialized")
    val appContext = ctx.applicationContext
    val dbFile = appContext.getDatabasePath("minimal_pairs.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
