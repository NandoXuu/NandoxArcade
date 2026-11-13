package com.nandox.arcade.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games", indices = [Index(value = ["uri"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platformId: String,
    val uri: String,
)

@Entity(tableName = "sources", indices = [Index(value = ["uri"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
)

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title")
    fun observeAll(): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGames(games: List<GameEntity>): List<Long>

    @Query("SELECT * FROM sources ORDER BY id")
    fun observeSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: SourceEntity): Long
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sources_uri` ON `sources` (`uri`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_uri` ON `games` (`uri`)")
    }
}

@Database(entities = [GameEntity::class, SourceEntity::class], version = 2, exportSchema = true)
abstract class ArcadeDb : RoomDatabase() {
    abstract fun games(): GameDao

    companion object {
        @Volatile private var instance: ArcadeDb? = null

        fun get(context: Context): ArcadeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ArcadeDb::class.java, "arcade.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
