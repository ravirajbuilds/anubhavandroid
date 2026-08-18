package com.anubhav.app.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.ColumnInfo
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
import androidx.room.Transaction

/**
 * One catalog test as cached on the phone.
 *
 * `nameLower` / `categoryLower` are stored rather than computed at query time so
 * the indexes can actually be used — `LOWER(name) LIKE ?` would force a full
 * table scan on every keystroke, which is exactly what this cache exists to avoid.
 */
@Entity(
    tableName = "catalog_test",
    indices = [Index("name_lower"), Index("category_lower")],
)
data class CatalogTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Double,
    @ColumnInfo(name = "name_lower") val nameLower: String,
    @ColumnInfo(name = "category_lower") val categoryLower: String,
)

@Dao
interface CatalogDao {

    @Query("SELECT COUNT(*) FROM catalog_test")
    suspend fun count(): Int

    @Query("SELECT * FROM catalog_test ORDER BY name_lower LIMIT :limit")
    suspend fun listAll(limit: Int): List<CatalogTestEntity>

    @Query(
        """
        SELECT * FROM catalog_test
        WHERE name_lower LIKE :pattern ESCAPE '\'
           OR category_lower LIKE :pattern ESCAPE '\'
        ORDER BY name_lower
        LIMIT :limit
        """
    )
    suspend fun search(pattern: String, limit: Int): List<CatalogTestEntity>

    @Query(
        """
        SELECT COUNT(*) FROM catalog_test
        WHERE name_lower LIKE :pattern ESCAPE '\'
           OR category_lower LIKE :pattern ESCAPE '\'
        """
    )
    suspend fun countMatching(pattern: String): Int

    @Query("SELECT * FROM catalog_test WHERE name_lower = :nameLower LIMIT 1")
    suspend fun findByName(nameLower: String): CatalogTestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CatalogTestEntity>)

    @Query("DELETE FROM catalog_test")
    suspend fun clear()

    /** Swap the whole catalog in one transaction so a failed sync cannot half-empty it. */
    @Transaction
    suspend fun replaceAll(rows: List<CatalogTestEntity>) {
        clear()
        insertAll(rows)
    }
}

@Database(entities = [CatalogTestEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anubhav-cache.db",
                )
                    // The catalog is a cache of the server's copy, never the source of
                    // truth, so throwing it away on a schema change is safe and beats
                    // shipping migrations for data we can re-download.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        /**
         * Drop the singleton so the next [get] opens a fresh database.
         *
         * Robolectric tears its SQLite environment down between test instances while this
         * object survives, leaving the cached handle pointing at a closed connection.
         */
        @VisibleForTesting
        fun closeForTests() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
