package org.plos.edu.lang.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LanguageEntity::class,
        StyleEntity::class,
        TrackEntity::class,
        AffixRuleEntity::class,
        RootEntity::class,
        LexemeEntity::class,
        UnitEntity::class,
        PassageEntity::class,
        PassageLineEntity::class,
        GrammarRuleEntity::class,
        CardStateEntity::class,
        ReviewLogEntity::class,
        WordDispositionEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = false,
)

abstract class MainDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
}
