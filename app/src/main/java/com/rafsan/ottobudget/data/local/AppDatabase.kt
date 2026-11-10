package com.rafsan.ottobudget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        KeywordRuleEntity::class,
        TransactionEntity::class,
        VaultGoalEntity::class,
        RecurringRuleEntity::class,
        PendingSmsDetectionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ottobudget_v8_release.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).appDao()
                                dao.insertWallets(SeedData.defaultWallets)
                                dao.insertCategories(SeedData.defaultCategories)
                                dao.insertKeywordRules(SeedData.defaultKeywordRules)
                                dao.insertVaultGoals(SeedData.defaultVaultGoals)
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
