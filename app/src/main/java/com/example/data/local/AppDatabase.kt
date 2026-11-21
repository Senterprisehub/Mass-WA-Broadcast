package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BroadcastLogEntry
import com.example.data.model.Campaign
import com.example.data.model.CampaignStatus
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageTemplate

class EnumTypeConverters {
    @TypeConverter
    fun fromCampaignStatus(status: CampaignStatus): String = status.name

    @TypeConverter
    fun toCampaignStatus(value: String): CampaignStatus =
        runCatching { CampaignStatus.valueOf(value) }.getOrDefault(CampaignStatus.DRAFT)

    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String = status.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus =
        runCatching { DeliveryStatus.valueOf(value) }.getOrDefault(DeliveryStatus.PENDING)
}

@Database(
    entities = [
        ContactGroup::class,
        Contact::class,
        Campaign::class,
        BroadcastLogEntry::class,
        MessageTemplate::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(EnumTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun campaignDao(): CampaignDao
    abstract fun broadcastLogDao(): BroadcastLogDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val DATABASE_NAME = "mass_wa_broadcast_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN targetPackage TEXT NOT NULL DEFAULT 'com.whatsapp'")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN targetUserSerial INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN isCloneTarget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN failureDiagnostic TEXT")
                db.execSQL("ALTER TABLE broadcast_logs ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_campaigns_status_scheduledTimeEpoch ON campaigns(status, scheduledTimeEpoch)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_broadcast_logs_campaignId_status ON broadcast_logs(campaignId, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_broadcast_logs_timestamp ON broadcast_logs(timestamp)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
