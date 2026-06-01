package com.cengyi.wifitimer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cengyi.wifitimer.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS target_config (
                    id INTEGER PRIMARY KEY NOT NULL,
                    targetMinutes INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    enabled INTEGER NOT NULL
                )
            """)
            // Insert default config
            db.execSQL("INSERT INTO target_config (id, targetMinutes, label, enabled) VALUES (1, 570, '工作日目标', 1)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wifitimer.db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideWhitelistDao(db: AppDatabase): WhitelistDao = db.whitelistDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideIgnoreWindowDao(db: AppDatabase): IgnoreWindowDao = db.ignoreWindowDao()

    @Provides
    fun provideDailyStatsDao(db: AppDatabase): DailyStatsDao = db.dailyStatsDao()

    @Provides
    fun provideTargetConfigDao(db: AppDatabase): TargetConfigDao = db.targetConfigDao()
}
