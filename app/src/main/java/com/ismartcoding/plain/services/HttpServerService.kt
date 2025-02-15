package com.ismartcoding.plain.services

import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.helpers.PortHelper
import com.ismartcoding.lib.isUPlus
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.BuildConfig
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.R
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.features.StartHttpServerErrorEvent
import com.ismartcoding.plain.helpers.NotificationHelper
import com.ismartcoding.plain.web.HttpServerManager
import io.ktor.server.application.ApplicationStopPreparing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HttpServerService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.ensureDefaultChannel()
        val notification =
            NotificationHelper.createServiceNotification(
                this,
                "${BuildConfig.APPLICATION_ID}.action.stop_http_server",
                getString(R.string.api_service_is_running),
            )
        if (isUPlus()) {
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            try {
                if (HttpServerManager.httpServer == null) {
                    HttpServerManager.portsInUse.clear()
                    HttpServerManager.stoppedByUser = false
                    HttpServerManager.httpServerError = ""
                    HttpServerManager.httpServer = HttpServerManager.createHttpServer(MainApp.instance)
                    HttpServerManager.httpServer?.start(wait = true)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                HttpServerManager.httpServer = null
                HttpServerManager.httpServerError = ex.toString()
                if (PortHelper.isPortInUse(TempData.httpPort)) {
                    HttpServerManager.portsInUse.add(TempData.httpPort)
                }
                if (PortHelper.isPortInUse(TempData.httpsPort)) {
                    HttpServerManager.portsInUse.add(TempData.httpsPort)
                }
                sendEvent(StartHttpServerErrorEvent())
                LogCat.e(ex.toString())
            }
        }
    }

    fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        coIO {
            try {
                HttpServerManager.httpServer?.let { h ->
                    val environment = h.environment
                    environment.monitor.raise(ApplicationStopPreparing, environment)
                    environment.stop()
                }
                HttpServerManager.httpServer = null
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    companion object {
        var instance: HttpServerService? = null
    }
}
