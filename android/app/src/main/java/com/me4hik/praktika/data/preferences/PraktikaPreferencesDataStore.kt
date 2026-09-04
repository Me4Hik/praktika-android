package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.praktikaPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "praktika_preferences",
)
