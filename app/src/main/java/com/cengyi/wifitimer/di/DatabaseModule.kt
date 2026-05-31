package com.cengyi.wifitimer.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wifitimer.db"
        ).build()
    }

    @Provides
    fun provideWhitelistDao(db: AppDatabase): WhitelistDao = db.whitelistDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideIgnoreWindowDao(db: AppDatabase): IgnoreWindowDao = db.ignoreWindowDao()

    @Provides
    fun provideDailyStatsDao(db: AppDatabase): DailyStatsDao = db.dailyStatsDao()
}
