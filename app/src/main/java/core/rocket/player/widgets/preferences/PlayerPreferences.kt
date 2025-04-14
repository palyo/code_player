package core.rocket.player.widgets.preferences

import core.rocket.player.widgets.enums.PlayerOrientation
import core.rocket.player.widgets.enums.VideoAspect
import core.rocket.player.widgets.utils.TinyDB

class PlayerPreferences(preferenceStore: TinyDB) {

    val orientation = preferenceStore.getEnum("player_orientation", PlayerOrientation.SensorLandscape)
    val invertDuration = preferenceStore.getBoolean("invert_duration", false)
    val drawOverDisplayCutout = preferenceStore.getBoolean("draw_over_cutout", true)

    val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
    val horizontalSeekGesture = preferenceStore.getBoolean("horizontal_seek_gesture", true)
    val showSeekBarWhenSeeking = preferenceStore.getBoolean("show_seekbar_when_seeking", false)
    val preciseSeeking = preferenceStore.getBoolean("precise_seeking", false)
    val showDoubleTapOvals = preferenceStore.getBoolean("show_double_tap_ovals", true)
    val showSeekIcon = preferenceStore.getBoolean("show_seek_icons", true)
    val showSeekTimeWhileSeeking = preferenceStore.getBoolean("show_seek_time_while_seeking", true)

    val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
    val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)

    val videoAspect = preferenceStore.getEnum("video_aspect", VideoAspect.Fit)
    val currentChaptersIndicator = preferenceStore.getBoolean("show_video_chapter_indicator", true)
    val showChaptersButton = preferenceStore.getBoolean("show_video_chapters_button", false)

    val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
    val speedPresets = preferenceStore.getStringList(
        "default_speed_presets",
        mutableListOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0")
    )
    val displayVolumeAsPercentage = preferenceStore.getBoolean("display_volume_as_percentage", true)
    val swapVolumeAndBrightness = preferenceStore.getBoolean("display_volume_on_right", false)
    val showLoadingCircle = preferenceStore.getBoolean("show_loading_circle", true)
    val savePositionOnQuit = preferenceStore.getBoolean("save_position", true)

    val automaticallyEnterPip = preferenceStore.getBoolean("automatic_pip", false)
    val closeAfterReachingEndOfVideo = preferenceStore.getBoolean("close_after_eof", false)

    val rememberBrightness = preferenceStore.getBoolean("remember_rightness", false)
    var defaultBrightness = preferenceStore.getFloat("default_brightness", -1f)

    val allowGesturesInPanels = preferenceStore.getBoolean("allow_gestures_in_panels", false)
    val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar", false)
    val reduceMotion = preferenceStore.getBoolean("reduce_motion", false)
    val playerTimeToDisappear = preferenceStore.getInt("player_time_to_disappear", 4000)

    val panelTransparency = preferenceStore.getFloat("panel_transparency", 0.6f)

    val primaryCustomButtonId = preferenceStore.getInt("player_custom_button_id", 0)
}
