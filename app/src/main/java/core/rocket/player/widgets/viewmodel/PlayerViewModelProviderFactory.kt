package core.rocket.player.widgets.viewmodel

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import core.rocket.player.R
import core.rocket.player.activities.PlayerActivity
import core.rocket.player.database.MpvKtDatabase
import core.rocket.player.database.entities.CustomButtonEntity
import core.rocket.player.widgets.enums.CustomKeyCodes
import core.rocket.player.widgets.enums.Decoder
import core.rocket.player.widgets.enums.Panels
import core.rocket.player.widgets.enums.PlayerOrientation
import core.rocket.player.widgets.enums.PlayerUpdates
import core.rocket.player.widgets.enums.Sheets
import core.rocket.player.widgets.enums.SingleActionGesture
import core.rocket.player.widgets.enums.VideoAspect
import core.rocket.player.widgets.enums.getDecoderFromValue
import core.rocket.player.widgets.preferences.GesturePreferences
import core.rocket.player.widgets.preferences.PlayerPreferences
import core.rocket.player.widgets.utils.TinyDB
import core.rocket.player.widgets.utils.openContentFd
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue
import kotlin.text.get
import kotlin.text.set

class PlayerViewModelProviderFactory(
    private val activity: PlayerActivity,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return PlayerViewModel(activity) as T
    }
}

class PlayerViewModel(
    private val activity: PlayerActivity,
) : ViewModel() {

    private var playerPreferences: PlayerPreferences? = null
    private var gesturePreferences: GesturePreferences? = null
    private var mpvKtDatabase: MpvKtDatabase? = null
    //private val _customButtons = MutableStateFlow<CustomButtonsUiState>(CustomButtonsUiState.Loading)
    //val customButtons = _customButtons.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButtonEntity?>(null)
    val primaryButton = _primaryButton.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    init {
        val tinyDB = TinyDB(activity)
        playerPreferences = PlayerPreferences(tinyDB)
        gesturePreferences = GesturePreferences(tinyDB)
        mpvKtDatabase = MpvKtDatabase.getDatabase(activity)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                //val buttons = mpvKtDatabase?.customButtonDao()?.getCustomButtons()?.first()
                //buttons.firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
                //  _primaryButton.update { _ -> it }
                //  // If the button text is not empty, it has been set buy a lua script in which
                //  // case we don't want to override it
                //  if (_primaryButtonTitle.value.isEmpty()) {
                //    setPrimaryCustomButtonTitle(it)
                //  }
                //}
                //activity.setupCustomButtons(buttons)
                //_customButtons.update { _ -> CustomButtonsUiState.Success(buttons) }
            } catch (e: Exception) {
                //Log.e(TAG, e.message ?: "Unable to fetch buttons")
                //_customButtons.update { _ -> CustomButtonsUiState.Error(e.message ?: "Unable to fetch buttons") }
            }
        }
    }

    private val _currentDecoder = MutableStateFlow(getDecoderFromValue(MPVLib.getPropertyString("hwdec")))
    val currentDecoder = _currentDecoder.asStateFlow()

    val mediaTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val playbackSpeed = MutableStateFlow(playerPreferences?.defaultSpeed ?: 0F)

    private val _subtitleTracks = MutableStateFlow<List<Track>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()
    private val _selectedSubtitles = MutableStateFlow(Pair(-1, -1))
    val selectedSubtitles = _selectedSubtitles.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<Track>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()
    private val _selectedAudio = MutableStateFlow(-1)
    val selectedAudio = _selectedAudio.asStateFlow()

    var chapters: List<Segment> = listOf()
    private val _currentChapter = MutableStateFlow<Segment?>(null)
    val currentChapter = _currentChapter.asStateFlow()

    private val _pos = MutableStateFlow(0f)
    val pos = _pos.asStateFlow()

    val duration = MutableStateFlow(0f)

    private val _readAhead = MutableStateFlow(0f)
    val readAhead = _readAhead.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()

    private val _controlsShown = MutableStateFlow(true)
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(true)
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(
        runCatching {
            Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f },
    )
    val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val currentMPVVolume = MutableStateFlow(MPVLib.getPropertyInt("volume"))
    var volumeBoostCap: Int = MPVLib.getPropertyInt("volume-max")

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            pause()
            Toast.makeText(activity, activity.getString(R.string.toast_sleep_timer_ended), Toast.LENGTH_SHORT).show()
        }
    }

    fun getDecoder() {
        _currentDecoder.update { getDecoderFromValue(activity.player?.hwdecActive ?: "no") }
    }

    fun cycleDecoders() {
        MPVLib.setPropertyString(
            "hwdec",
            when (currentDecoder.value) {
                Decoder.HWPlus -> Decoder.HW.value
                Decoder.HW -> Decoder.SW.value
                Decoder.SW -> Decoder.HWPlus.value
                Decoder.AutoCopy -> Decoder.SW.value
                Decoder.Auto -> Decoder.SW.value
            },
        )
        val newDecoder = activity.player?.hwdecActive ?: "no"
        if (newDecoder != currentDecoder.value.value) {
            _currentDecoder.update { getDecoderFromValue(newDecoder) }
        }
    }

    fun updateDecoder(decoder: Decoder) {
        MPVLib.setPropertyString("hwdec", decoder.value)
    }

    val getTrackLanguage: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/lang") ?: ""
        } else {
            activity.getString(R.string.player_sheets_tracks_off)
        }
    }
    val getTrackTitle: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/title") ?: ""
        } else {
            activity.getString(R.string.player_sheets_tracks_off)
        }
    }
    val getTrackMPVId: (Int) -> Int = {
        if (it != -1) {
            MPVLib.getPropertyInt("track-list/$it/id")
        } else {
            -1
        }
    }
    val getTrackType: (Int) -> String? = {
        MPVLib.getPropertyString("track-list/$it/type")
    }

    private var trackLoadingJob: Job? = null
    fun loadTracks() {
        trackLoadingJob?.cancel()
        trackLoadingJob = viewModelScope.launch {
            val possibleTrackTypes = listOf("video", "audio", "sub")
            val vidTracks = mutableListOf<Track>()
            val subTracks = mutableListOf<Track>()
            val audioTracks = mutableListOf(Track(-1, activity.getString(R.string.player_sheets_tracks_off), null))
            try {
                val tracksCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                for (i in 0..<tracksCount) {
                    val type = getTrackType(i)
                    if (!possibleTrackTypes.contains(type) || type == null) continue
                    when (type) {
                        "sub" -> subTracks.add(Track(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                        "audio" -> audioTracks.add(Track(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                        "video" -> vidTracks.add(Track(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                        else -> error("Unrecognized track type")
                    }
                }
            } catch (e: NullPointerException) {
                Log.e("TAG", "Couldn't load tracks, probably cause mpv was destroyed")
                return@launch
            }
            _subtitleTracks.update { subTracks }
            _audioTracks.update { audioTracks }
        }
    }

    fun loadChapters() {
        val chapters = mutableListOf<Segment>()
        val count = MPVLib.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyInt("chapter-list/$i/time")!!
            //chapters.add(
            //    Segment(
            //        name = title,
            //        start = time.toFloat(),
            //    ),
            //)
        }
        this.chapters = chapters.sortedBy { it.start }
    }

    fun selectChapter(index: Int) {
        val time = chapters[index].start
        seekTo(time.toInt())
    }

    fun updateChapter(index: Long) {
        if (chapters.isEmpty() || index == -1L) return
        _currentChapter.update { chapters.getOrNull(index.toInt()) ?: return }
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val path = if (url.startsWith("content://")) {
            url.toUri().openContentFd(activity)
        } else {
            url
        } ?: return
        MPVLib.command(arrayOf("audio-add", path, "cached"))
    }

    fun selectAudio(id: Int) {
        activity.player?.aid = id
    }

    fun updateAudio(id: Int) {
        _selectedAudio.update { id }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val path = if (url.startsWith("content://")) {
            url.toUri().openContentFd(activity)
        } else {
            url
        } ?: return
        MPVLib.command(arrayOf("sub-add", path, "cached"))
    }

    fun selectSub(id: Int) {
        val selectedSubs = selectedSubtitles.value
        _selectedSubtitles.update {
            when (id) {
                selectedSubs.first -> Pair(selectedSubs.second, -1)
                selectedSubs.second -> Pair(selectedSubs.first, -1)
                else -> {
                    if (selectedSubs.first != -1) {
                        Pair(selectedSubs.first, id)
                    } else {
                        Pair(id, -1)
                    }
                }
            }
        }
        activity.player?.secondarySid = _selectedSubtitles.value.second
        activity.player?.sid = _selectedSubtitles.value.first
    }

    fun updateSubtitle(sid: Int, secondarySid: Int) {
        _selectedSubtitles.update { Pair(sid, secondarySid) }
    }

    fun updatePlayBackPos(pos: Float) {
        _pos.update { pos }
    }

    fun updateReadAhead(value: Long) {
        _readAhead.update { value.toFloat() }
    }

    fun pauseUnpause() {
        if (paused.value) {
            unpause()
        } else {
            pause()
        }
    }

    fun pause() {
        activity.player?.paused = true
        _paused.update { true }
    }

    fun unpause() {
        activity.player?.paused = false
        _paused.update { false }
    }

    private val showStatusBar = playerPreferences?.showSystemStatusBar == true
    fun showControls() {
        if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
        if (showStatusBar) {
            activity.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        activity.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        MPVLib.command(arrayOf("seek", offset.toString(), if (precise) "relative+exact" else "relative"))
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        if (position !in 0..(activity.player?.duration ?: 0)) return
        MPVLib.command(arrayOf("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes"))
    }

    fun changeBrightnessBy(change: Float) {
        changeBrightnessTo(currentBrightness.value + change)
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = brightness.coerceIn(0f, 1f).also {
                currentBrightness.update { _ -> it }
            }
        }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun changeVolumeBy(change: Int) {
        val mpvVolume = MPVLib.getPropertyInt("volume")
        if (volumeBoostCap > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
            if (finalMPVVolume in 100..volumeBoostCap + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        activity.audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0,
        )
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    fun setMPVVolume(volume: Int) {
        if (volume != currentMPVVolume.value) displayVolumeSlider()
        currentMPVVolume.update { volume }
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun changeVideoAspect(aspect: VideoAspect) {
        var ratio = -1.0
        var pan = 1.0
        when (aspect) {
            VideoAspect.Crop -> {
                pan = 1.0
            }

            VideoAspect.Fit -> {
                pan = 0.0
                MPVLib.setPropertyDouble("panscan", 0.0)
            }

            VideoAspect.Stretch -> {
                val dm = DisplayMetrics()
                activity.windowManager.defaultDisplay.getRealMetrics(dm)
                ratio = dm.widthPixels / dm.heightPixels.toDouble()
                pan = 0.0
            }
        }
        MPVLib.setPropertyDouble("panscan", pan)
        MPVLib.setPropertyDouble("video-aspect-override", ratio)
        TinyDB(context = activity).putEnum("video_aspect", aspect)
        playerUpdate.update { PlayerUpdates.AspectRatio }
    }

    fun cycleScreenRotations() {
        activity.requestedOrientation = when (activity.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                -> {
                TinyDB(context = activity).putEnum("player_orientation", PlayerOrientation.SensorPortrait)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            else -> {
                TinyDB(context = activity).putEnum("player_orientation", PlayerOrientation.SensorLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }
                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> panelShown.update { Panels.SubtitleSettings }
                    "subtitle_delay" -> panelShown.update { Panels.SubtitleDelay }
                    "audio_delay" -> panelShown.update { Panels.AudioDelay }
                    "video_filters" -> panelShown.update { Panels.VideoFilters }
                }
            }
            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }
            "reset_button_title" -> {
                //_customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
                //  setPrimaryCustomButtonTitle(it)
                //}
            }
            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }
            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }
            "seek_by" -> seekByWithText(data.toInt(), null)
            "seek_to" -> seekToWithText(data.toInt(), null)
            "toggle_button" -> {
                fun showButton() {
                    if (_primaryButton.value == null) {
                        //_primaryButton.update {
                        //  customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }
                        //}
                    }
                }
                when (data) {
                    "show" -> showButton()
                    "hide" -> _primaryButton.update { null }
                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }
                }
            }
            "software_keyboard" -> when (data) {
                "show" -> forceShowSoftwareKeyboard()
                "hide" -> forceHideSoftwareKeyboard()
                "toggle" -> if (inputMethodManager.isActive) forceHideSoftwareKeyboard() else forceShowSoftwareKeyboard()
            }
        }

        MPVLib.setPropertyString(property, "")
    }

    private val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private fun forceShowSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun forceHideSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private fun seekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = seekValue > 0
        _doubleTapSeekAmount.value = seekValue - pos.value.toInt()
        _seekText.update { text }
        seekTo(seekValue, playerPreferences?.preciseSeeking == true)
        if (playerPreferences?.showSeekBarWhenSeeking == true) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        _doubleTapSeekAmount.update { if (value < 0 && it < 0 || pos.value + value > duration.value) 0 else it + value }
        _seekText.update { text }
        _isSeekingForwards.value = value > 0
        seekBy(value, playerPreferences?.preciseSeeking == true)
        if (playerPreferences?.showSeekBarWhenSeeking == true) showSeekBar()
    }

    private val doubleTapToSeekDuration = gesturePreferences?.doubleTapToSeekDuration?:10

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(text: String?) {
        _seekText.update { text }
    }

    fun leftSeek() {
        if (pos.value > 0) {
            _doubleTapSeekAmount.value -= doubleTapToSeekDuration
        }
        _isSeekingForwards.value = false
        seekBy(-doubleTapToSeekDuration, playerPreferences?.preciseSeeking == true)
        if (playerPreferences?.showSeekBarWhenSeeking == true) showSeekBar()
    }

    fun rightSeek() {
        if (pos.value < duration.value) {
            _doubleTapSeekAmount.value += doubleTapToSeekDuration
        }
        _isSeekingForwards.value = true
        seekBy(doubleTapToSeekDuration, playerPreferences?.preciseSeeking == true)
        if (playerPreferences?.showSeekBarWhenSeeking == true) showSeekBar()
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences?.leftSingleActionGesture?:SingleActionGesture.Seek) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapLeft.keyCode))
            }
            SingleActionGesture.None -> {}
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences?.centerSingleActionGesture?:SingleActionGesture.PlayPause) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapCenter.keyCode))
            }
            SingleActionGesture.Seek -> {}
            SingleActionGesture.None -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences?.rightSingleActionGesture?:SingleActionGesture.Seek) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapRight.keyCode))
            }
            SingleActionGesture.None -> {}
        }
    }

    fun setPrimaryCustomButtonTitle(button: CustomButtonEntity) {
        _primaryButtonTitle.update { _ -> button.title }
    }
}

data class Track(
    val id: Int,
    val name: String,
    val language: String?,
)

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}

fun CustomButtonEntity.execute() {
    MPVLib.command(arrayOf("script-message", "call_button_$id"))
}

fun CustomButtonEntity.executeLongClick() {
    MPVLib.command(arrayOf("script-message", "call_button_${id}_long"))
}