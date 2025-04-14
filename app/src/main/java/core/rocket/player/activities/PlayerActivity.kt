package core.rocket.player.activities

import android.annotation.*
import android.app.*
import android.content.*
import android.content.IntentFilter
import android.content.pm.*
import android.content.res.*
import android.media.*
import android.media.session.*
import android.net.*
import android.os.*
import android.provider.*
import android.util.*
import android.view.*
import androidx.activity.*
import androidx.core.net.*
import androidx.core.text.*
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.media.*
import coder.apps.space.library.base.*
import com.github.k1rakishou.fsaf.*
import core.rocket.player.database.entities.*
import core.rocket.player.databinding.*
import core.rocket.player.domain.playbackstate.repository.*
import core.rocket.player.widgets.controls.createPipActions
import core.rocket.player.widgets.enums.*
import core.rocket.player.widgets.extension.PIP_FF
import core.rocket.player.widgets.extension.PIP_FR
import core.rocket.player.widgets.extension.PIP_INTENTS_FILTER
import core.rocket.player.widgets.extension.PIP_INTENT_ACTION
import core.rocket.player.widgets.extension.PIP_PAUSE
import core.rocket.player.widgets.extension.PIP_PLAY
import core.rocket.player.widgets.observer.*
import core.rocket.player.widgets.preferences.*
import core.rocket.player.widgets.utils.*
import core.rocket.player.widgets.viewmodel.*
import `is`.xyz.mpv.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.*
import java.io.*
import kotlin.text.get

const val TAG = "mpvKt"

class PlayerActivity : BaseActivity<ActivityPlayerBinding>(ActivityPlayerBinding::inflate) {

    private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> { PlayerViewModelProviderFactory(this) }
    val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    val player by lazy { binding?.player }
    val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }

    private val playerObserver by lazy { PlayerObserver(this) }
    private var mediaSession: MediaSession? = null
    private var playerPreferences: PlayerPreferences? = null
    private var audioPreferences: AudioPreferences? = null
    private var subtitlesPreferences: SubtitlesPreferences? = null
    private var advancedPreferences: AdvancedPreferences? = null
    private val gesturePreferences: GesturePreferences? = null
    private var fileManager: FileManager? = null

    private val playbackStateRepository: PlaybackStateRepository by inject()

    private var fileName = ""

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: android.graphics.Rect? = null
    val isPipSupported by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            false
        } else {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }
    }
    private var pipReceiver: BroadcastReceiver? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun ActivityPlayerBinding.initExtra() {
        fileManager = FileManager(this@PlayerActivity)
        TinyDB(this@PlayerActivity).let {
            audioPreferences = AudioPreferences(it)
            playerPreferences = PlayerPreferences(it)
            advancedPreferences = AdvancedPreferences(it)
            subtitlesPreferences = SubtitlesPreferences(it)
        }
        setupMPV()
        setupAudio()
        setupMediaSession()
        getPlayableUri(intent)?.let(player::playFile)
        setOrientation()
    }

    private fun getPlayableUri(intent: Intent): String? {
        val uri = parsePathFromIntent(intent)
        return if (uri?.startsWith("content://") == true) uri.toUri().openContentFd(this) else uri
    }

    override fun onDestroy() {
        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null
        mediaSession?.release()
        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        player?.isExiting = true
        if (isFinishing) {
            MPVLib.command(arrayOf("stop"))
        }
        MPVLib.removeObserver(playerObserver)
        MPVLib.destroy()

        super.onDestroy()
    }

    override fun onPause() {
        if (!isInPictureInPictureMode) {
            viewModel.pause()
        }
        saveVideoPlaybackState(fileName)
        super.onPause()
    }

    override fun finish() {
        setReturnIntent()
        super.finish()
    }

    override fun onStop() {
        viewModel.pause()
        saveVideoPlaybackState(fileName)
        window.attributes.screenBrightness.let {
            if (playerPreferences?.rememberBrightness == true && it != -1f) {
                TinyDB(this@PlayerActivity).putFloat("default_brightness", it)
            }
        }
        super.onStop()
    }

    @SuppressLint("NewApi")
    override fun onUserLeaveHint() {
        if (isPipSupported && player?.paused == false && playerPreferences?.automaticallyEnterPip == true) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    @SuppressLint("NewApi")
    override fun onBackPressed() {
        if (isPipSupported && player?.paused == false && playerPreferences?.automaticallyEnterPip == true) {
            if (viewModel.sheetShown.value == Sheets.None && viewModel.panelShown.value == Panels.None) {
                enterPictureInPictureMode()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding?.root?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (playerPreferences?.drawOverDisplayCutout == true) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (playerPreferences?.rememberBrightness == true) {
            playerPreferences?.defaultBrightness?.let {
                if (it != -1f) viewModel.changeBrightnessTo(it)
            }
        }
    }

    private fun setupMPV() {
        copyMPVAssets()
        player?.initialize(filesDir.path, cacheDir.path)
        MPVLib.addObserver(playerObserver)
    }

    private fun setupAudio() {
        audioPreferences?.audioChannels.let { MPVLib.setPropertyString(it?.property.toString(), it?.value.toString()) }

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
            it.setAudioAttributes(
                AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
            )
            it.setOnAudioFocusChangeListener(audioFocusChangeListener)
        }.build()
        AudioManagerCompat.requestAudioFocus(audioManager, request).let {
            if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
            audioFocusRequest = request
        }
    }

    private fun copyMPVAssets() {
        Utils.copyAssets(this@PlayerActivity)
        copyMPVScripts()
        copyMPVConfigFiles()
        // fonts can be lazily loaded
        lifecycleScope.launch(Dispatchers.IO) {
            copyMPVFonts()
        }
    }

    private fun copyMPVConfigFiles() {
        val applicationPath = filesDir.path
        fileManager?.let { fileManager ->
            try {
                val mpvConf = fileManager.fromUri(advancedPreferences?.mpvConfStorageUri.toString().toUri())
                    ?: error("User hasn't set any mpvConfig directory")
                if (!fileManager.exists(mpvConf)) error("Couldn't access mpv configuration directory")
                fileManager.copyDirectoryWithContent(mpvConf, fileManager.fromPath(applicationPath), true)
            } catch (e: Exception) {
                File("$applicationPath/mpv.conf")
                    .also { if (!it.exists()) it.createNewFile() }
                    .writeText(advancedPreferences?.mpvConf.toString())
                File("$applicationPath/input.conf")
                    .also { if (!it.exists()) it.createNewFile() }
                    .writeText(advancedPreferences?.inputConf.toString())
                Log.e("PlayerActivity", "Couldn't copy mpv configuration files: ${e.message}")
            }
        }
    }

    private fun copyMPVScripts() {
        val mpvktLua = assets.open("mpvkt.lua")
        val applicationPath = filesDir.path

        val scriptsDir = fileManager?.createDir(fileManager?.fromPath(applicationPath)!!, "scripts")!!

        fileManager?.deleteContent(scriptsDir)

        File("$scriptsDir/mpvkt.lua")
            .also { if (!it.exists()) it.createNewFile() }
            .writeText(mpvktLua.bufferedReader().readText())
    }

    private fun copyMPVFonts() {
        try {
            fileManager?.let { fileManager ->
                val cachePath = cacheDir.path
                val fontsDir = fileManager.fromUri(subtitlesPreferences?.fontsFolder.toString().toUri())
                    ?: error("User hasn't set any fonts directory")
                if (!fileManager.exists(fontsDir)) error("Couldn't access fonts directory")

                val destDir = fileManager.fromPath("$cachePath/fonts")
                if (!fileManager.exists(destDir)) fileManager.createDir(fileManager.fromPath(cachePath), "fonts")

                if (fileManager.findFile(destDir, "subfont.ttf") == null) {
                    resources.assets.open("subfont.ttf")
                        .copyTo(File("$cachePath/fonts/subfont.ttf").outputStream())
                }

                fileManager.copyDirectoryWithContent(fontsDir, destDir, false)
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Couldn't copy fonts to application directory: ${e.message}")
        }

    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = player?.paused ?: false
                viewModel.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) viewModel.unpause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "0.5"))
                restoreAudioFocus = {
                    MPVLib.command(arrayOf("multiply", "volume", "2"))
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }

            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Log.d("PlayerActivity", "didn't get audio focus")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
    }

    private fun setIntentExtras(extras: Bundle?) {
        if (extras == null) return

        extras.getString("title")?.let {
            viewModel.mediaTitle.update { _ -> it }
            MPVLib.setPropertyString("force-media-title", it)
        }
        player?.timePos = extras.getInt("position", 0) / 1000

        // subtitles
        if (extras.containsKey("subs")) {
            val subList = Utils.getParcelableArray<Uri>(extras, "subs")
            val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

            for (suburi in subList) {
                val subfile = suburi.resolveUri(this) ?: continue
                val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

                Log.v(TAG, "Adding subtitles from intent extras: $subfile")
                MPVLib.command(arrayOf("sub-add", subfile, flag))
            }
        }

        extras.getStringArray("headers")?.let { headers ->
            if (headers[0].startsWith("User-Agent", true)) MPVLib.setPropertyString("user-agent", headers[1])
            val headersString = headers.asSequence().drop(2).chunked(2).associate { it[0] to it[1] }
                .map { "${it.key}: ${it.value.replace(",", "\\,")}" }.joinToString(",")
            MPVLib.setPropertyString("http-header-fields", headersString)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun parsePathFromIntent(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
            Intent.ACTION_SEND -> {
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.resolveUri(this)
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                        val uri = it.trim().toUri()
                        if (uri.isHierarchical && !uri.isRelative) uri.resolveUri(this) else null
                    }
                }
            }

            else -> intent.getStringExtra("uri")
        }
    }

    private fun getFileName(intent: Intent): String {
        val uri = if (intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)!!.toUri()
        } else {
            (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))
        }
        if (uri != null) {
            val cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null)
            if (cursor?.moveToFirst() == true) return cursor.getString(0).also { cursor.close() }
        }
        return uri?.lastPathSegment?.substringAfterLast("/") ?: uri?.path ?: ""
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences?.videoAspect?:VideoAspect.Fit)
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    internal fun onObserverEvent(property: String, value: Long) {
        if (player?.isExiting == true) return
        when (property) {
            "time-pos" -> viewModel.updatePlayBackPos(value.toFloat())
            "demuxer-cache-time" -> viewModel.updateReadAhead(value = value)
            "volume" -> viewModel.setMPVVolume(value.toInt())
            "volume-max" -> viewModel.volumeBoostCap = value.toInt() - 100
            "chapter" -> viewModel.updateChapter(value)
            "duration" -> viewModel.duration.update { value.toFloat() }
        }
    }

    internal fun onObserverEvent(property: String) {
        if (player?.isExiting == true) return
        when (property) {
            "chapter-list" -> {
                viewModel.loadChapters()
                viewModel.updateChapter(0)
            }
            "track-list" -> viewModel.loadTracks()
        }
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player?.isExiting == true) return
        when (property) {
            "pause" -> {
                if (value) {
                    viewModel.pause()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    viewModel.unpause()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        setPictureInPictureParams(createPipParams())
                    }
                }
            }

            "paused-for-cache" -> {
                viewModel.isLoading.update { value }
            }

            "seeking" -> {
                viewModel.isLoading.update { value }
            }

            "eof-reached" -> {
                if (value && playerPreferences?.closeAfterReachingEndOfVideo == true) {
                    finishAndRemoveTask()
                }
            }
        }
    }

    val trackId: (String) -> Int? = {
        when (it) {
            "auto" -> null
            "no" -> -1
            else -> it.toInt()
        }
    }

    internal fun onObserverEvent(property: String, value: String) {
        if (player?.isExiting == true) return
        when (property.substringBeforeLast("/")) {
            "aid" -> trackId(value)?.let { viewModel.updateAudio(it) }
            "sid" -> trackId(value)?.let { viewModel.updateSubtitle(it, viewModel.selectedSubtitles.value.second) }
            "secondary-sid" -> trackId(value)?.let { viewModel.updateSubtitle(viewModel.selectedSubtitles.value.first, it) }
            "hwdec", "hwdec-current" -> viewModel.getDecoder()
            "user-data/mpvkt" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @SuppressLint("NewApi")
    internal fun onObserverEvent(property: String, value: Double) {
        if (player?.isExiting == true) return
        when (property) {
            "speed" -> viewModel.playbackSpeed.update { value.toFloat() }
            "video-params/aspect" -> if (isPipSupported) createPipParams()
        }
    }

    internal fun event(eventId: Int) {
        if (player?.isExiting == true) return
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                fileName = getFileName(intent)
                setIntentExtras(intent.extras)
                viewModel.mediaTitle.update {
                    val mediaTitle = MPVLib.getPropertyString("media-title")
                    if (mediaTitle.isBlank() || mediaTitle.isDigitsOnly()) fileName else mediaTitle
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    loadVideoPlaybackState(fileName)
                }
                setOrientation()
                viewModel.changeVideoAspect(playerPreferences?.videoAspect?:VideoAspect.Fit)
            }

            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel.isLoading.update { true }
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player?.isExiting = false
        }
    }

    private fun saveVideoPlaybackState(mediaTitle: String) {
        if (mediaTitle.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            player?.let { player ->
                val oldState = playbackStateRepository.getVideoDataByTitle(fileName)
                Log.d(TAG, "Saving playback state")
                val speed = (playerPreferences?.defaultSpeed?:1f).toDouble()
                playbackStateRepository.upsert(
                    PlaybackStateEntity(
                        mediaTitle = mediaTitle,
                        lastPosition = if (playerPreferences?.savePositionOnQuit == true) {
                            if ((player.timePos ?: 0) < (player.duration ?: 0) - 1) player.timePos ?: 0 else 0
                        } else {
                            oldState?.lastPosition ?: 0
                        },
                        playbackSpeed = player.playbackSpeed ?: speed ,
                        sid = player.sid,
                        subDelay = ((player.subDelay ?: 0.0) * 1000).toInt(),
                        subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: 1.0,
                        secondarySid = player.secondarySid,
                        secondarySubDelay = ((player.secondarySubDelay ?: 0.0) * 1000).toInt(),
                        aid = player.aid,
                        audioDelay = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toInt(),
                    ),
                )
            }
        }
    }

    private suspend fun loadVideoPlaybackState(mediaTitle: String) {
        if (mediaTitle.isBlank()) return
        val state = playbackStateRepository.getVideoDataByTitle(mediaTitle)
        val getDelay: (Int, Int?) -> Double = { preferenceDelay, stateDelay ->
            (stateDelay ?: preferenceDelay) / 1000.0
        }
        val subDelay = getDelay(subtitlesPreferences?.defaultSubDelay?:0, state?.subDelay)
        val secondarySubDelay = getDelay(subtitlesPreferences?.defaultSecondarySubDelay?:0, state?.secondarySubDelay)
        val audioDelay = getDelay(audioPreferences?.defaultAudioDelay?:0, state?.audioDelay)
        state?.let {
            player?.sid = it.sid
            player?.secondarySid = it.secondarySid
            player?.aid = it.aid
            player?.subDelay = subDelay
            player?.secondarySubDelay = secondarySubDelay
            player?.playbackSpeed = it.playbackSpeed
            MPVLib.setPropertyDouble("audio-delay", audioDelay)
        }
        if (playerPreferences?.savePositionOnQuit == true) {
            state?.lastPosition?.let { if (it != 0) player?.timePos = it }
        }
        MPVLib.setPropertyDouble("sub-speed", state?.subSpeed ?: (subtitlesPreferences?.defaultSubSpeed?:0).toDouble())
    }

    private fun setReturnIntent() {
        setResult(
            RESULT_OK,
            Intent(RESULT_INTENT).apply {
                player?.timePos?.let { putExtra("position", it * 1000) }
                player?.duration?.let { putExtra("duration", it * 1000) }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        getPlayableUri(intent)?.let { MPVLib.command(arrayOf("loadfile", it)) }
        setIntent(intent)
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle(viewModel.mediaTitle.value)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences?.automaticallyEnterPip==true
            builder.setAutoEnterEnabled(player?.paused == false && autoEnter)
            builder.setSeamlessResizeEnabled(player?.paused == false && autoEnter)
        }
        builder.setActions(createPipActions(this, player?.paused != false))
        builder.setSourceRectHint(pipRect)
        player?.videoH?.let {
            val height = it
            val width = it * player?.getVideoOutAspect()!!
            val rational = Rational(height, width.toInt()).toFloat()
            if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
        }
        return builder.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(createPipParams())
        }
        viewModel.hideControls()
        viewModel.hideSeekBar()
        viewModel.isBrightnessSliderShown.update { false }
        viewModel.isVolumeSliderShown.update { false }
        viewModel.sheetShown.update { Sheets.None }
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                    PIP_PAUSE -> viewModel.pause()
                    PIP_PLAY -> viewModel.unpause()
                    PIP_FF -> viewModel.handleRightDoubleTap()
                    PIP_FR -> viewModel.handleLeftDoubleTap()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPictureInPictureParams(createPipParams())
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
        }
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setOrientation() {
        requestedOrientation = when (playerPreferences?.orientation?:PlayerOrientation.SensorLandscape) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player?.videoAspect ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { player?.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let { if (player?.onKey(it) == true) return true }
        return super.onKeyUp(keyCode, event)
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences?.mediaPreviousGesture?:SingleActionGesture.Seek
        val playAction = gesturePreferences?.mediaPlayGesture?:SingleActionGesture.PlayPause
        val nextAction = gesturePreferences?.mediaNextGesture?:SingleActionGesture.Seek

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }
                        }
                    }

                    override fun onPause() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPrevious.keyCode))
                            }
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaNext.keyCode))
                            }
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        this@PlayerActivity.onStop()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    override fun ActivityPlayerBinding.initListeners() {
    }

    override fun ActivityPlayerBinding.initView() {
    }

    companion object {

        // action of result intent
        private const val RESULT_INTENT = "core.rocket.player.activities.PlayerActivity.result"
    }
}