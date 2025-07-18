package com.zsolutions.peerlinkyz

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Database(entities = [Friend::class, Message::class, Setting::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun messageDao(): MessageDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "peerlinkyz_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // This is called when the database is created for the first time.
                            // We can't migrate SharedPreferences here because the database is empty.
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // This is called when the database has been opened.
                            // Check if the migration from version 3 to 4 just happened.
                            if (db.version == 4) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val sharedPrefs = context.getSharedPreferences("peerlinkyz_preferences", Context.MODE_PRIVATE)
                                    val settingDao = getDatabase(context).settingDao()

                                    val username = sharedPrefs.getString("username_preference", null)
                                    username?.let { settingDao.insert(Setting("username_preference", it)) }

                                    val qrCodeContent = sharedPrefs.getString("my_qr_code_content", null)
                                    qrCodeContent?.let { settingDao.insert(Setting("my_qr_code_content", it)) }

                                    val theme = sharedPrefs.getString("theme_preference", null)
                                    theme?.let { settingDao.insert(Setting("theme_preference", it)) }

                                    val retentionDays = sharedPrefs.getInt("message_retention_days", 30)
                                    settingDao.insert(Setting("message_retention_days", retentionDays.toString()))

                                    // Clear SharedPreferences after migration
                                    sharedPrefs.edit().clear().apply()
                                    Log.d("AppDatabase", "SharedPreferences migrated to Room database.")
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `friendId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isSent` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // This migration is empty as the SharedPreferences data transfer will happen in a callback
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `friends` ADD COLUMN `onionAddress` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` RENAME TO `messages_old`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `friendId` INTEGER NOT NULL, `data` BLOB NOT NULL, `isSent` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `messages` (id, friendId, data, isSent, timestamp) SELECT id, friendId, CAST(text AS BLOB), isSent, timestamp FROM `messages_old`")
                database.execSQL("DROP TABLE `messages_old`")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `outbox` RENAME TO `outbox_old`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `peerId` TEXT NOT NULL, `recipientAddress` TEXT NOT NULL, `message` BLOB NOT NULL, `sent` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `outbox` (id, peerId, recipientAddress, message, sent, timestamp) SELECT id, peerId, recipientAddress, CAST(message AS BLOB), sent, timestamp FROM `outbox_old`")
                database.execSQL("DROP TABLE `outbox_old`")
            }
        }

        

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // This migration is empty as the OutboxMessage table was already updated in MIGRATION_6_7
                // This is just to increment the version number.
            }
        }

        fun cleanupOldMessages(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                val retentionDaysString = database.settingDao().get("message_retention_days")
                val retentionDays = retentionDaysString?.toIntOrNull() ?: 30 // Default to 30 days

                Log.d("AppDatabase", "Cleaning up old messages with retention: $retentionDays days")
                val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
                val deletedRows = database.messageDao().deleteOldMessages(cutoffTime)
                Log.d("AppDatabase", "Deleted $deletedRows old messages.")
            }
        }
    }
}