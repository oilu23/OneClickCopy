package com.oneclickcopy

import android.content.Context
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
import kotlinx.coroutines.Dispatchers

/**
 * Minimal manual dependency container.
 *
 * Deliberately not Hilt: the graph is three objects deep, and manual construction
 * keeps the build free of annotation processing for DI while remaining trivially
 * swappable in tests.
 */
interface AppContainer {
    val documentRepository: DocumentRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val database by lazy { AppDatabase.get(context) }

    override val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            dao = database.documentDao(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
