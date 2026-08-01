package com.khanabook.lite.pos.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

@Composable
fun rememberMenuItemAddFeedback(
    settings: MenuFeedbackSettings
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val soundPlayer = remember(context) {
        MenuItemAddSoundPlayer(context)
    }
    val currentSettings = rememberUpdatedState(settings)
    val currentView = rememberUpdatedState(view)

    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer.release()
        }
    }

    return remember(soundPlayer) {
        {
            val activeSettings = currentSettings.value
            if (activeSettings.soundEnabled) {
                soundPlayer.play()
            }
            if (activeSettings.hapticEnabled) {
                currentView.value.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}

internal inline fun performMenuItemAdd(
    addToCart: () -> Unit,
    playFeedback: () -> Unit
) {
    addToCart()
    playFeedback()
}

internal fun createMenuItemAddWaveFile(): ByteArray {
    val sampleCount = SAMPLE_RATE * SOUND_DURATION_MS / 1_000
    val pcm = ByteArray(sampleCount * PCM_BYTES_PER_SAMPLE)
    var noiseState = NOISE_SEED

    repeat(sampleCount) { index ->
        val timeSeconds = index.toDouble() / SAMPLE_RATE
        val attack = (timeSeconds / ATTACK_SECONDS).coerceIn(0.0, 1.0)
        val decay = exp(-DECAY_RATE * timeSeconds)
        noiseState = noiseState * NOISE_MULTIPLIER + NOISE_INCREMENT
        val noise = (((noiseState ushr 16) and 0x7fff) / 16_383.5) - 1.0
        val body = sin(2.0 * PI * BODY_FREQUENCY_HZ * timeSeconds)
        val waveform = attack * decay * ((NOISE_MIX * noise) + (BODY_MIX * body))
        val sample = (waveform * Short.MAX_VALUE * PEAK_AMPLITUDE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
        val byteIndex = index * PCM_BYTES_PER_SAMPLE
        pcm[byteIndex] = (sample.toInt() and 0xff).toByte()
        pcm[byteIndex + 1] = ((sample.toInt() ushr 8) and 0xff).toByte()
    }

    return createWaveFile(pcm)
}

private class MenuItemAddSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var loaded = false
    private var activeStreamId = 0
    private var lastPlayedAt = 0L
    private val soundId: Int

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }
        soundId = runCatching {
            val soundFile = File(context.cacheDir, SOUND_CACHE_FILE_NAME)
            val waveFile = createMenuItemAddWaveFile()
            if (!soundFile.exists() || soundFile.length() != waveFile.size.toLong()) {
                soundFile.writeBytes(waveFile)
            }
            soundPool.load(soundFile.absolutePath, 1)
        }.getOrDefault(0)
    }

    fun play() {
        if (!loaded || soundId == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayedAt < MIN_PLAY_INTERVAL_MS) return
        lastPlayedAt = now
        if (activeStreamId != 0) {
            soundPool.stop(activeStreamId)
        }
        activeStreamId = soundPool.play(
            soundId,
            PLAYBACK_VOLUME,
            PLAYBACK_VOLUME,
            1,
            0,
            1f
        )
    }

    fun release() {
        soundPool.release()
    }
}

private fun createWaveFile(pcm: ByteArray): ByteArray {
    val result = ByteArray(WAVE_HEADER_SIZE + pcm.size)
    result.writeAscii(0, "RIFF")
    result.writeIntLittleEndian(4, result.size - 8)
    result.writeAscii(8, "WAVE")
    result.writeAscii(12, "fmt ")
    result.writeIntLittleEndian(16, 16)
    result.writeShortLittleEndian(20, WAVE_FORMAT_PCM)
    result.writeShortLittleEndian(22, CHANNEL_COUNT)
    result.writeIntLittleEndian(24, SAMPLE_RATE)
    result.writeIntLittleEndian(28, SAMPLE_RATE * CHANNEL_COUNT * PCM_BYTES_PER_SAMPLE)
    result.writeShortLittleEndian(32, CHANNEL_COUNT * PCM_BYTES_PER_SAMPLE)
    result.writeShortLittleEndian(34, PCM_BITS_PER_SAMPLE)
    result.writeAscii(36, "data")
    result.writeIntLittleEndian(40, pcm.size)
    pcm.copyInto(result, WAVE_HEADER_SIZE)
    return result
}

private fun ByteArray.writeAscii(offset: Int, value: String) {
    value.forEachIndexed { index, character ->
        this[offset + index] = character.code.toByte()
    }
}

private fun ByteArray.writeIntLittleEndian(offset: Int, value: Int) {
    repeat(Int.SIZE_BYTES) { byteIndex ->
        this[offset + byteIndex] = ((value ushr (byteIndex * Byte.SIZE_BITS)) and 0xff).toByte()
    }
}

private fun ByteArray.writeShortLittleEndian(offset: Int, value: Int) {
    repeat(Short.SIZE_BYTES) { byteIndex ->
        this[offset + byteIndex] = ((value ushr (byteIndex * Byte.SIZE_BITS)) and 0xff).toByte()
    }
}

private const val SOUND_CACHE_FILE_NAME = "menu_item_add_v1.wav"
private const val SAMPLE_RATE = 22_050
private const val SOUND_DURATION_MS = 52
private const val MIN_PLAY_INTERVAL_MS = 45L
private const val PLAYBACK_VOLUME = 0.3f
private const val PCM_BYTES_PER_SAMPLE = 2
private const val PCM_BITS_PER_SAMPLE = 16
private const val WAVE_FORMAT_PCM = 1
private const val CHANNEL_COUNT = 1
private const val WAVE_HEADER_SIZE = 44
private const val ATTACK_SECONDS = 0.0015
private const val DECAY_RATE = 78.0
private const val BODY_FREQUENCY_HZ = 920.0
private const val NOISE_MIX = 0.62
private const val BODY_MIX = 0.38
private const val PEAK_AMPLITUDE = 0.42
private const val NOISE_SEED = 0x13579bdf
private const val NOISE_MULTIPLIER = 1_103_515_245
private const val NOISE_INCREMENT = 12_345
