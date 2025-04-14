package core.rocket.player.di

import androidx.room.Room
import core.rocket.player.database.Migrations
import core.rocket.player.database.MpvKtDatabase
import core.rocket.player.database.repository.CustomButtonRepositoryImpl
import core.rocket.player.database.repository.PlaybackStateRepositoryImpl
import core.rocket.player.domain.custombuttons.repository.CustomButtonRepository
import core.rocket.player.domain.playbackstate.repository.PlaybackStateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val DatabaseModule = module {
  single<MpvKtDatabase> {
    Room
      .databaseBuilder(androidContext(), MpvKtDatabase::class.java, "mpvKt.db")
      .addMigrations(migrations = Migrations)
      .build()
  }

  singleOf(::CustomButtonRepositoryImpl).bind(CustomButtonRepository::class)
  singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)
}
