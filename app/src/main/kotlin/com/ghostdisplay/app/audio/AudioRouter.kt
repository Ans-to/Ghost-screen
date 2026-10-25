package com.ghostdisplay.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures another app's playback via MediaProjection
 * ([AudioPlaybackCaptureConfiguration]) and plays it out the speaker /
 * Bluetooth through an [AudioTrack].
 *
 * Audio continues with the phone screen locked because this runs inside the
 * foreground [com.ghostdisplay.app.service.GhostService] holding a
 * PARTIAL_WAKE_LOCK.
 *
 * Per-app mute is a pause of the capture→playback loop, not a volume change
 * of the hidden app (we cannot set another app's volume).
 *
 * Apps may opt out with allowAudioPlaybackCapture="false"; that is reported,
 * not swallowed.
 */
class AudioRouter {

    data class StartResult(
        val started: Boolean,
        val notice: String,
    )

    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    val isRunning: Boolean get() = running.get()
    val isMuted: Boolean get() = muted.get()

    fun start(mediaProjection: MediaProjection?): StartResult {
        if (mediaProjection == null) {
            return StartResult(
                false,
                "Audio routing needs the standard MediaProjection (screen-capture) consent. " +
                    "Without it the hidden app's audio may still play through the default mixer, " +
                    "or it may be silent — Android does not let a third-party app tap another app's audio otherwise.",
            )
        }
        if (running.get()) return StartResult(true, "Audio routing already running.")

        val sampleRate = 44100
        val channelIn = AudioFormat.CHANNEL_IN_STEREO
        val channelOut = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minIn = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val minOut = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
        if (minIn <= 0 || minOut <= 0) {
            return StartResult(false, "This device rejected the audio capture format.")
        }
        // AudioTrack rejects sizes that are not a multiple of the frame
        // (stereo 16-bit = 4 bytes). sampleRate/2 is 22050, which is not.
        val buffer = alignedAudioBufferBytes(minIn, minOut, bytesPerFrame = 4)

        val captureConfig = try {
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        } catch (t: Throwable) {
            return StartResult(false, "Could not build audio capture config: ${t.message}")
        }

        val audioFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelIn)
            .build()

        val recorder = try {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(buffer)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (se: SecurityException) {
            return StartResult(
                false,
                "RECORD_AUDIO permission is required to route the hidden app's audio. Grant it in Setup.",
            )
        } catch (t: Throwable) {
            return StartResult(false, "AudioRecord failed: ${t.message}")
        }

        val outFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelOut)
            .build()

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val player = try {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(outFormat)
                .setBufferSizeInBytes(buffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            recorder.release()
            return StartResult(false, "AudioTrack failed: ${t.message}")
        }

        record = recorder
        track = player
        running.set(true)
        muted.set(false)

        try {
            recorder.startRecording()
            player.play()
        } catch (t: Throwable) {
            stop()
            return StartResult(
                false,
                "Audio capture did not start: ${t.message}. The hidden app may have opted out of playback capture.",
            )
        }

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buf = ByteArray(buffer)
            while (running.get()) {
                val rec = record ?: break
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (muted.get()) continue
                track?.write(buf, 0, n)
            }
        }, "ghost-audio").also { it.start() }

        return StartResult(
            true,
            "Audio routing is on. It continues with the screen locked. Mute from the session card if needed. " +
                "Apps that set allowAudioPlaybackCapture=\"false\" will stay silent here.",
        )
    }

    fun setMuted(value: Boolean) {
        muted.set(value)
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            track?.stop()
        } catch (_: Throwable) {
        }
        record?.release()
        track?.release()
        record = null
        track = null
        Log.i(TAG, "Audio router stopped")
    }

    companion object {
        private const val TAG = "GhostAudio"
        fun requiresMediaProjection(): Boolean = Build.VERSION.SDK_INT >= 29
    }
}
