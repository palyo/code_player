package core.rocket.player.widgets.enums

import androidx.annotation.StringRes
import core.rocket.player.R

enum class SubtitlesBorderStyle(
  val value: String,
  @StringRes val titleRes: Int,
) {
  OutlineAndShadow("outline-and-shadow", R.string.player_sheets_subtitles_border_style_outline_and_shadow),
  OpaqueBox("opaque-box", R.string.player_sheets_subtitles_border_style_opaque_box),
  BackgroundBox("background-box", R.string.player_sheets_subtitles_border_style_background_box)
}