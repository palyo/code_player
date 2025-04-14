package core.rocket.player.widgets.preferences

import core.rocket.player.widgets.utils.TinyDB
import core.rocket.player.widgets.enums.Debanding

class DecoderPreferences(preferenceStore: TinyDB) {

    val tryHWDecoding = preferenceStore.getBoolean("try_hw_dec", true)
    val gpuNext = preferenceStore.getBoolean("gpu_next", false)
    val debanding = preferenceStore.getEnum("debanding", Debanding.None)
    val useYUV420P = preferenceStore.getBoolean("use_yuv420p", true)

    val brightnessFilter = preferenceStore.getInt("filter_brightness", 0)
    val saturationFilter = preferenceStore.getInt("filter_saturation", 0)
    val gammaFilter = preferenceStore.getInt("filter_gamma", 0)
    val contrastFilter = preferenceStore.getInt("filter_contrast", 0)
    val hueFilter = preferenceStore.getInt("filter_hue", 0)
}
