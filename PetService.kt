package com.pet.robo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager

/** Serviço que mantém o pet flutuando sobre os outros apps. */
class PetService : Service() {

    companion object {
        const val ACTION_STOP = "com.pet.robo.PARAR"
        private const val CANAL = "pet"
        private const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private var view: PetView? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        iniciarEmPrimeiroPlano()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) mostrar()
        return START_STICKY
    }

    private fun iniciarEmPrimeiroPlano() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Pet", NotificationManager.IMPORTANCE_LOW)
        )
        val parar = PendingIntent.getService(
            this, 0,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icone = Icon.createWithResource(this, R.drawable.ic_launcher)
        val notificacao = Notification.Builder(this, CANAL)
            .setSmallIcon(icone)
            .setContentTitle("Pet ligado")
            .setContentText("Toque em Parar para desligar")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icone, "Parar", parar).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notificacao)
        }
    }

    private fun tamanhoDaTela(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    private fun mostrar() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val pet = PetView(this) { x, y ->
            params.x = x
            params.y = y
            view?.let { v -> if (v.isAttachedToWindow) wm.updateViewLayout(v, params) }
        }
        params.width = pet.larguraPx
        params.height = pet.alturaPx
        view = pet
        wm.addView(pet, params)
        val (w, h) = tamanhoDaTela()
        pet.definirTela(w, h)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val (w, h) = tamanhoDaTela()
        view?.definirTela(w, h)
    }

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }
}
