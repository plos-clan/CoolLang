package org.plos.edu.lang.modules

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.plos.edu.lang.Constants
import org.plos.edu.lang.database.MainDatabase
import org.plos.edu.lang.database.StudyDao
import org.plos.edu.lang.engine.FsrsScheduler
import org.plos.edu.lang.engine.MemoryScheduler
import org.plos.edu.lang.repository.ArchiveCodec
import org.plos.edu.lang.repository.ArchiveGateway
import org.plos.edu.lang.repository.CorpusGateway
import org.plos.edu.lang.repository.CorpusRepository
import org.plos.edu.lang.repository.JsonArchiveCodec
import org.plos.edu.lang.repository.StudyArchiver
import org.plos.edu.lang.repository.StudyGateway
import org.plos.edu.lang.repository.StudyRepository

@Module
@InstallIn(SingletonComponent::class)
object Database {
    @Provides
    @Singleton
    fun provideMainDatabase(
        @ApplicationContext context: Context,
    ): MainDatabase = Room.databaseBuilder(
        context,
        MainDatabase::class.java,
        Constants.DATABASE_NAME,
    ).build()

    @Provides
    fun provideStudyDao(database: MainDatabase): StudyDao =
        database.studyDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {
    @Binds
    @Singleton
    abstract fun bindMemoryScheduler(impl: FsrsScheduler): MemoryScheduler

    @Binds
    @Singleton
    abstract fun bindSpeechEngine(impl: SystemSpeechEngine): SpeechEngine

    @Binds
    @Singleton
    abstract fun bindArchiveCodec(impl: JsonArchiveCodec): ArchiveCodec

    @Binds
    @Singleton
    abstract fun bindStudyGateway(impl: StudyRepository): StudyGateway

    @Binds
    @Singleton
    abstract fun bindCorpusGateway(impl: CorpusRepository): CorpusGateway

    @Binds
    @Singleton
    abstract fun bindArchiveGateway(impl: StudyArchiver): ArchiveGateway
}
