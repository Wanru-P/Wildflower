package com.floralens.data.season

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.floralens.data.db.ObservationDao
import com.floralens.data.db.entity.toDomain
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.season.SeasonArchive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface SeasonSource { fun observe(): Flow<SeasonArchive> }

@Singleton
class SeasonRepository @Inject constructor(private val plants: PlantRepository, private val observations: ObservationDao,
    @param:ApplicationContext private val context: Context) : SeasonSource {
    private fun zones(): Flow<ZoneId> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(ZoneId.systemDefault()) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        trySend(ZoneId.systemDefault())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
    override fun observe(): Flow<SeasonArchive> = combine(plants.observeAll(), observations.observeNatural(), zones()) { p, o, zone ->
        SeasonArchive.build(p, o.map { it.toDomain() }, zone)
    }.flowOn(Dispatchers.IO)
}
