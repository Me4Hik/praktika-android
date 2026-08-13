package com.me4hik.praktika.data.backup.write

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.me4hik.praktika.data.backup.storage.BackupTreePreferencesKeys
import com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BackupWriteStateTestHarness {
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
    private var dataStore: DataStore<Preferences>? = null

    fun createDataStore(temporaryFolder: TemporaryFolder): DataStore<Preferences> {
        val file = File(temporaryFolder.root, "backup_write_state_${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        dataStore = store
        return store
    }

    fun writeStateRepository(store: DataStore<Preferences>): DataStoreBackupWriteStateRepository {
        return DataStoreBackupWriteStateRepository(store)
    }

    fun treeConfigRepository(store: DataStore<Preferences>): DataStoreBackupTreeConfigRepository {
        return DataStoreBackupTreeConfigRepository(store)
    }

    fun failingEditDataStore(
        temporaryFolder: TemporaryFolder,
        failOnEdit: () -> Boolean,
    ): DataStore<Preferences> {
        val delegate = createDataStore(temporaryFolder)
        return object : DataStore<Preferences> by delegate {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                if (failOnEdit()) {
                    throw IllegalStateException("injected DataStore edit failure")
                }
                return delegate.updateData(transform)
            }
        }
    }

    suspend fun seedHintOnly(
        store: DataStore<Preferences>,
        hint: String,
    ) {
        store.edit { prefs ->
            prefs[BackupTreePreferencesKeys.TREE_URI_HINT] = hint
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        const val FOLDER_A = "content://com.test.provider/tree/folder-a"
        const val FOLDER_B = "content://com.test.provider/tree/folder-b"
        const val MALFORMED = "   "
        const val TA = 1_700_000_000_000L
        const val TB = 1_700_000_100_000L
        const val T2 = 1_700_000_200_000L
    }
}
