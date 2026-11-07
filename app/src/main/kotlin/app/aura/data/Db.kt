package app.aura.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "battery_samples", indices = [Index("ts")])
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val level: Int, val charging: Boolean, val plugged: Int,
    val voltageMv: Int, val currentMa: Float?, val powerMw: Float?, val tempC: Float, val chargeMah: Float?,
)

@Entity(tableName = "system_samples", indices = [Index("ts")])
data class SystemSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val cpuLoadPct: Float?, val cpuLoadKind: String?, val ramUsedPct: Float,
    val storageUsedPct: Float, val thermalHeadroom: Float?, val appCpuPct: Float?,
)

@Entity(tableName = "speed_results", indices = [Index("ts")])
data class SpeedResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val server: String, val network: String,
    val downMbps: Float?, val upMbps: Float?, val pingMs: Float?, val jitterMs: Float?, val lossPct: Float?,
)

@Entity(tableName = "charge_sessions", indices = [Index("startTs")])
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long, val endTs: Long?, val startLevel: Int, val endLevel: Int, val plugged: Int,
    val sumPowerMw: Double, val powerSamples: Int, val peakPowerMw: Float, val maxTempC: Float,
    val startChargeMah: Float?, val endChargeMah: Float?,
) {
    val avgPowerMw: Float? get() = if (powerSamples > 0) (sumPowerMw / powerSamples).toFloat() else null
}

data class BatteryBucket(
    val bucket: Long, val avgTempC: Float, val maxTempC: Float, val avgVoltageMv: Float,
    val avgPowerMw: Float?, val avgLevel: Float, val n: Int,
)

data class SystemBucket(
    val bucket: Long, val avgCpu: Float?, val maxCpu: Float?, val avgRam: Float, val maxRam: Float, val n: Int,
)

data class HourlyCapacity(val h: Long, val capMah: Float)
data class HourOfDayDrain(val hour: Int, val avgPowerMw: Float, val n: Int)

@Dao
interface AuraDao {
    @Insert suspend fun insert(e: BatterySampleEntity)
    @Insert suspend fun insert(e: SystemSampleEntity)
    @Insert suspend fun insert(e: SpeedResultEntity): Long
    @Insert suspend fun insert(e: ChargeSessionEntity): Long
    @Update suspend fun update(e: ChargeSessionEntity)

    @Query("SELECT * FROM charge_sessions WHERE endTs IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun openSession(): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions WHERE startTs BETWEEN :from AND :to ORDER BY startTs DESC")
    fun sessions(from: Long, to: Long): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions WHERE startTs BETWEEN :from AND :to ORDER BY startTs")
    suspend fun sessionsOnce(from: Long, to: Long): List<ChargeSessionEntity>

    @Query("SELECT * FROM speed_results WHERE ts BETWEEN :from AND :to ORDER BY ts")
    fun speedResults(from: Long, to: Long): Flow<List<SpeedResultEntity>>

    @Query("SELECT * FROM speed_results WHERE ts BETWEEN :from AND :to ORDER BY ts")
    suspend fun speedResultsOnce(from: Long, to: Long): List<SpeedResultEntity>

    @Query("SELECT * FROM speed_results ORDER BY ts DESC LIMIT 1")
    suspend fun lastSpeed(): SpeedResultEntity?

    @Query(
        "SELECT (ts / :bucketMs) * :bucketMs AS bucket, AVG(tempC) AS avgTempC, MAX(tempC) AS maxTempC, " +
            "AVG(voltageMv) AS avgVoltageMv, AVG(CASE WHEN charging = 1 THEN powerMw END) AS avgPowerMw, " +
            "AVG(level) AS avgLevel, COUNT(*) AS n FROM battery_samples " +
            "WHERE ts BETWEEN :from AND :to GROUP BY ts / :bucketMs ORDER BY bucket"
    )
    fun batteryBuckets(from: Long, to: Long, bucketMs: Long): Flow<List<BatteryBucket>>

    @Query(
        "SELECT (ts / :bucketMs) * :bucketMs AS bucket, AVG(cpuLoadPct) AS avgCpu, MAX(cpuLoadPct) AS maxCpu, " +
            "AVG(ramUsedPct) AS avgRam, MAX(ramUsedPct) AS maxRam, COUNT(*) AS n FROM system_samples " +
            "WHERE ts BETWEEN :from AND :to GROUP BY ts / :bucketMs ORDER BY bucket"
    )
    fun systemBuckets(from: Long, to: Long, bucketMs: Long): Flow<List<SystemBucket>>

    @Query("SELECT * FROM battery_samples WHERE ts BETWEEN :from AND :to ORDER BY ts")
    suspend fun batteryRaw(from: Long, to: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM system_samples WHERE ts BETWEEN :from AND :to ORDER BY ts")
    suspend fun systemRaw(from: Long, to: Long): List<SystemSampleEntity>

    /** Hourly estimates of full capacity = coulomb counter / level, only where both are usable. */
    @Query(
        "SELECT ts / 3600000 AS h, AVG(chargeMah * 100.0 / level) AS capMah FROM battery_samples " +
            "WHERE chargeMah IS NOT NULL AND chargeMah > 0 AND level BETWEEN 20 AND 95 AND ts >= :from " +
            "GROUP BY ts / 3600000 ORDER BY h"
    )
    suspend fun hourlyCapacity(from: Long): List<HourlyCapacity>

    @Query(
        "SELECT ((ts + :tzOffsetMs) / 3600000) % 24 AS hour, AVG(powerMw) AS avgPowerMw, COUNT(*) AS n " +
            "FROM battery_samples WHERE charging = 0 AND powerMw IS NOT NULL AND ts >= :from GROUP BY hour ORDER BY hour"
    )
    suspend fun drainByHourOfDay(from: Long, tzOffsetMs: Long): List<HourOfDayDrain>

    @Query("SELECT MAX(tempC) FROM battery_samples WHERE ts >= :from")
    suspend fun maxTempSince(from: Long): Float?

    @Query("SELECT COUNT(*) FROM battery_samples")
    suspend fun batteryCount(): Int

    @Query("DELETE FROM battery_samples WHERE ts < :before") suspend fun pruneBattery(before: Long)
    @Query("DELETE FROM system_samples WHERE ts < :before") suspend fun pruneSystem(before: Long)
    @Query("DELETE FROM speed_results WHERE ts < :before") suspend fun pruneSpeed(before: Long)
    @Query("DELETE FROM charge_sessions WHERE startTs < :before AND endTs IS NOT NULL") suspend fun pruneSessions(before: Long)
}

@Database(
    entities = [BatterySampleEntity::class, SystemSampleEntity::class, SpeedResultEntity::class, ChargeSessionEntity::class],
    version = 1, exportSchema = true,
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun dao(): AuraDao

    companion object {
        @Volatile private var inst: AuraDatabase? = null
        fun get(ctx: Context): AuraDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AuraDatabase::class.java, "aura.db").build().also { inst = it }
        }
    }
}
