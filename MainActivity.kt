package com.pet.robo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = (24 * d).toInt()
            setPadding(p, p, p, p)
        }

        fun texto(t: String, tamanho: Float) = TextView(this).apply {
            text = t
            textSize = tamanho
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * d).toInt())
        }

        fun botao(t: String, acao: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { acao() }
        }

        raiz.addView(texto("🤖 Pet", 28f))
        status = texto("", 15f)
        raiz.addView(status)
        raiz.addView(botao("1. Permitir exibir sobre outros apps") { abrirPermissao() })
        raiz.addView(botao("2. Ligar o pet") { ligar() })
        raiz.addView(botao("Desligar o pet") { stopService(Intent(this, PetService::class.java)) })
        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this))
            "Permissão concedida. Pode ligar o pet!"
        else
            "Falta a permissão de exibir sobre outros apps."
    }

    private fun abrirPermissao() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun ligar() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primeiro conceda a permissão (passo 1).", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, PetService::class.java))
    }
}
