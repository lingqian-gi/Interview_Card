package com.caj.interviewcards.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.caj.interviewcards.MainActivity
import com.caj.interviewcards.R
import com.caj.interviewcards.data.Recording
import com.caj.interviewcards.data.RecordingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 录音前台服务。
 *
 * ★ 为什么必须是前台服务：
 *   Android 9+ 对后台麦克风有强限制，App 切后台/息屏后普通录音会被系统掐断。
 *   用 foregroundServiceType="microphone" + 常驻通知，才能长时间稳定录音（开会场景必需）。
 *
 * 用法：
 *   startService(Intent(this, RecordingService::class.java).setAction(ACTION_START))
 *   startService(Intent(this, RecordingService::class.java).setAction(ACTION_STOP))
 *   startService(Intent(this, RecordingService::class.java).setAction(ACTION_CANCEL))
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.caj.interviewcards.REC_START"
        const val ACTION_STOP = "com.caj.interviewcards.REC_STOP"
        const val ACTION_CANCEL = "com.caj.interviewcards.REC_CANCEL"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 1001

        /** ★ 全局录音状态（UI 直接观察，不必靠 bind） */
        private val _state = MutableStateFlow(RecState())
        val state: StateFlow<RecState> = _state

        /** 是否正在录音 */
        val isRecording: Boolean get() = _state.value.recording

        /**
         * 启动录音（外部调用）。返回 false 表示已在录音。
         */
        fun start(context: Context) {
            val i = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_CANCEL))
        }
    }

    data class RecState(
        val recording: Boolean = false,
        val elapsedMs: Long = 0L,
        val title: String = "",
    )

    private var recorder: MediaRecorder? = null
    private var curFile: File? = null
    private var curId: String = ""
    private var startedAt = 0L
    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private lateinit var store: RecordingStore

    override fun onCreate() {
        super.onCreate()
        store = RecordingStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (recorder == null) startRecording()
            ACTION_STOP -> stopRecording(save = true)
            ACTION_CANCEL -> stopRecording(save = false)
            else -> {
                // 服务被系统重启且无指令 → 直接退出，避免出现"幽灵录音"
                if (recorder == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "录音",
                        NotificationManager.IMPORTANCE_LOW,   // 静音，不打扰开会
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在录音")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startRecording() {
        val (id, file) = store.newFile()
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this)
        else
            @Suppress("DEPRECATION") MediaRecorder()

        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            // ★ AAC in MP4：体积小（1 小时约 30~60MB）、兼容性好、可直接分享给豆包等工具
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)              // 会议录音单声道够用，省一半体积
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(96000)   // 96kbps：语音清晰度与体积的平衡点
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        } catch (e: Exception) {
            runCatching { rec.release() }
            runCatching { file.delete() }
            stopSelf()
            return
        }

        recorder = rec
        curFile = file
        curId = id
        startedAt = SystemClock.elapsedRealtime()
        _state.value = RecState(
            recording = true,
            title = defaultTitle(System.currentTimeMillis()),
        )

        startForeground(NOTIF_ID, buildNotification("00:00"))

        // 计时 + 每秒刷新通知
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        tickJob = scope!!.launch {
            while (true) {
                val ms = SystemClock.elapsedRealtime() - startedAt
                _state.value = _state.value.copy(elapsedMs = ms)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification(fmt(ms)))
                delay(1000)
            }
        }
    }

    private fun stopRecording(save: Boolean) {
        val rec = recorder ?: run { stopSelf(); return }
        recorder = null
        tickJob?.cancel()
        tickJob = null

        val durationMs = SystemClock.elapsedRealtime() - startedAt
        val file = curFile
        val id = curId

        runCatching {
            rec.stop()
        }.onFailure {
            // stop() 可能因录音过短而抛异常 —— 此时文件不可用
            runCatching { rec.release() }
            runCatching { file?.delete() }
            cleanupService()
            return
        }
        runCatching { rec.release() }

        if (!save) {
            runCatching { file?.delete() }
            cleanupService()
            return
        }

        val size = file?.length() ?: 0L
        if (file == null || !file.exists() || size < 1024) {
            runCatching { file?.delete() }
            cleanupService()
            return
        }

        // 登记元信息
        val createdAt = System.currentTimeMillis()
        val item = Recording(
            id = id,
            title = defaultTitle(createdAt),
            durationMs = durationMs,
            sizeBytes = size,
            createdAt = createdAt,
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { store.add(item) }

        cleanupService()
    }

    private fun cleanupService() {
        _state.value = RecState()
        curFile = null
        curId = ""
        scope?.cancel()
        scope = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        scope?.cancel()
        _state.value = RecState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun defaultTitle(t: Long): String {
        val f = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return "录音 " + f.format(java.util.Date(t))
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}
