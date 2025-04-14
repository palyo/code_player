package core.rocket.player.widgets.preferences

import android.graphics.Color
import core.rocket.player.widgets.enums.SubtitlesBorderStyle
import core.rocket.player.widgets.utils.TinyDB

class SubtitlesPreferences(preferenceStore: TinyDB) {

    val preferredLanguages = preferenceStore.getString("sub_preferred_languages", "")

    val fontsFolder = preferenceStore.getString("sub_fonts_folder", "")
    val font = preferenceStore.getString("sub_font", "Sans Serif")
    val fontSize = preferenceStore.getInt("sub_font_size", 55)
    val subScale = preferenceStore.getFloat("sub_scale", 1f)
    val borderSize = preferenceStore.getInt("sub_border_size", 3)
    val bold = preferenceStore.getBoolean("sub_bold", false)
    val italic = preferenceStore.getBoolean("sub_italic", false)

    val textColor = preferenceStore.getInt("sub_color_text", Color.WHITE)

    val borderColor = preferenceStore.getInt("sub_color_border", Color.BLACK)
    val borderStyle = preferenceStore.getEnum("sub_border_style", SubtitlesBorderStyle.OutlineAndShadow)
    val shadowOffset = preferenceStore.getInt("sub_shadow_offset",0)
    val backgroundColor = preferenceStore.getInt("sub_color_bg", Color.TRANSPARENT)

    val justification = preferenceStore.getEnum("sub_justify", SubtitleJustification.Auto)
    val subPos = preferenceStore.getInt("sub_pos", 100)

    val overrideAssSubs = preferenceStore.getBoolean("sub_override_ass",false)

    val defaultSubDelay = preferenceStore.getInt("sub_default_delay",0)
    val defaultSubSpeed = preferenceStore.getFloat("sub_default_speed", 1f)
    val defaultSecondarySubDelay = preferenceStore.getInt("sub_default_secondary_delay",0)
}

enum class SubtitleJustification(
    val value: String,
    val icon: Int,
) {

    Left("left", 0),
    Center("center", 0),
    Right("right", 0),
    Auto("auto", 0)
}
