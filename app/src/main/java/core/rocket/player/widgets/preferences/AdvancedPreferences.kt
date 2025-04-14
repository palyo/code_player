package core.rocket.player.widgets.preferences

import coder.apps.space.library.BuildConfig
import core.rocket.player.widgets.utils.TinyDB

class AdvancedPreferences(preferenceStore: TinyDB) {

    val mpvConfStorageUri = preferenceStore.getString("mpv_conf_storage_location_uri", "")
    val mpvConf = preferenceStore.getString("mpv.conf", "")
    val inputConf = preferenceStore.getString("input.conf", "")

    val verboseLogging = preferenceStore.getBoolean("verbose_logging", BuildConfig.BUILD_TYPE != "release")

    val enabledStatisticsPage = preferenceStore.getInt("enabled_stats_page", 0)
}
