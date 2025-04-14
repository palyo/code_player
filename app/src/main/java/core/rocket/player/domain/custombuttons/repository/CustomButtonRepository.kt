package core.rocket.player.domain.custombuttons.repository

import core.rocket.player.database.entities.*
import kotlinx.coroutines.flow.*

interface CustomButtonRepository {
  fun getCustomButtons(): Flow<List<CustomButtonEntity>>

  suspend fun upsert(customButtonEntity: CustomButtonEntity)

  suspend fun deleteAndReindex(customButtonEntity: CustomButtonEntity)

  suspend fun increaseIndex(customButtonEntity: CustomButtonEntity)

  suspend fun decreaseIndex(customButtonEntity: CustomButtonEntity)

  suspend fun updateButton(customButtonEntity: CustomButtonEntity)
}
