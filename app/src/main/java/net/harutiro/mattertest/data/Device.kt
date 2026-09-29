package net.harutiro.mattertest.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow


@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val nodeId: Long,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val createdAt: Long,
)

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY createdAt")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM devices WHERE nodeId = :nodeId)")
    suspend fun exists(nodeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("UPDATE devices SET name = :name WHERE nodeId = :nodeId")
    suspend fun updateName(nodeId: Long, name: String)

    @Query("DELETE FROM devices WHERE nodeId = :nodeId")
    suspend fun delete(nodeId: Long)
}

@Database(entities = [DeviceEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "matter.db")
                    .build().also { instance = it }
            }
    }
}
