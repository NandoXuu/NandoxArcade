package com.nandox.arcade.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platformId: String,
    val uri: String,
)

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title")
    fun observeAll(): Flow<List<GameEntity>>
}

@Database(entities = [GameEntity::class], version = 1, exportSchema = true)
abstract class ArcadeDb : RoomDatabase() {
    abstract fun games(): GameDao

    companion object {
        @Volatile private var instance: ArcadeDb? = null

        fun get(context: Context): ArcadeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ArcadeDb::class.java, "arcade.db")
                .build().also { instance = it }
        }
    }
}
