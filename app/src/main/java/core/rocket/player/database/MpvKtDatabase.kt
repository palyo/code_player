package core.rocket.player.database

import android.content.Context
import androidx.room.*
import core.rocket.player.database.dao.*
import core.rocket.player.database.entities.*
import org.koin.android.ext.koin.androidContext

@Database(entities = [PlaybackStateEntity::class, CustomButtonEntity::class], version = 5)
abstract class MpvKtDatabase : RoomDatabase() {

    abstract fun videoDataDao(): PlaybackStateDao
    abstract fun customButtonDao(): CustomButtonDao

    companion object {

        @Volatile
        private var INSTANCE: MpvKtDatabase? = null
        fun getDatabase(context: Context): MpvKtDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room
                    .databaseBuilder(context, MpvKtDatabase::class.java, "mpvKt.db")
                    .addMigrations(migrations = Migrations)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
