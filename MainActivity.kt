package com.example.domawear

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var audioManager: AudioManager
    private lateinit var audioHelper: AudioHelper
    private lateinit var tts: TextToSpeech
    private lateinit var txtStatus: TextView
    private var ttsPronto = false

    // Mensagens, lembretes e avisos exibidos na lista
    private val itens = listOf(
        "Mensagem: reunião às 14 horas na sala 3.",
        "Lembrete: beber água e fazer uma pausa.",
        "Aviso: treinamento de segurança amanhã às 9 horas.",
        "Mensagem: seu almoço está liberado no refeitório.",
        "Lembrete: registrar o ponto antes de sair."
    )

    // Detecta quando um fone/caixa de som é conectado ou removido
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            atualizarStatusAudio()
            if (audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)) {
                falar("Fone Bluetooth conectado")
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            atualizarStatusAudio()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioHelper = AudioHelper(this)
        tts = TextToSpeech(this, this)

        txtStatus = findViewById(R.id.txtStatus)
        val lista = findViewById<ListView>(R.id.listaMensagens)
        val btnAlerta = findViewById<Button>(R.id.btnAlerta)
        val btnBluetooth = findViewById<Button>(R.id.btnBluetooth)
        val btnVoz = findViewById<Button>(R.id.btnVoz)

        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, itens)

        // Toque simples: lê o item em voz alta
        lista.setOnItemClickListener { _, _, position, _ -> falar(itens[position]) }

        // Toque longo: lê todos os itens
        lista.setOnItemLongClickListener { _, _, _, _ ->
            lerTudo()
            true
        }

        btnAlerta.setOnClickListener { emitirAlertaSeguranca() }
        btnBluetooth.setOnClickListener { abrirConfiguracoesBluetooth() }
        btnVoz.setOnClickListener { iniciarReconhecimentoVoz() }

        audioManager.registerAudioDeviceCallback(audioCallback, null)
        atualizarStatusAudio()
    }

    // ---------------- Texto para voz ----------------
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "Voz indisponível neste dispositivo", Toast.LENGTH_LONG).show()
            return
        }
        val resultado = tts.setLanguage(Locale("pt", "BR"))
        if (resultado == TextToSpeech.LANG_MISSING_DATA ||
            resultado == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Toast.makeText(this, "Português não disponível, usando voz padrão", Toast.LENGTH_LONG).show()
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ttsPronto = true
        falar("Aplicativo Doma pronto")
    }

    private fun falar(texto: String) {
        if (!ttsPronto) {
            Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
            return
        }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "doma")
    }

    private fun lerTudo() {
        falar(itens.joinToString(" ... "))
    }

    // ---------------- Saídas de áudio ----------------
    private fun atualizarStatusAudio() {
        val alto = audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        val bluetooth = audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        txtStatus.text = when {
            bluetooth -> "Áudio: fone Bluetooth"
            alto -> "Áudio: alto-falante"
            else -> "Sem saída de áudio. Toque em Fone"
        }
    }

    private fun abrirConfiguracoesBluetooth() {
        try {
            val intent = with(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("EXTRA_CONNECTION_ONLY", true)
                putExtra("EXTRA_CLOSE_ON_CONNECT", true)
                putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 1)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Configurações de Bluetooth indisponíveis", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Alerta de segurança ----------------
    private fun emitirAlertaSeguranca() {
        val vibrador = getSystemService(Vibrator::class.java)
        vibrador?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1)
        )
        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
                .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
        } catch (e: RuntimeException) {
            // Sem som de alarme neste dispositivo: segue só com voz e vibração
        }
        falar("Alerta de segurança! Dirija-se à saída de emergência mais próxima.")
    }

    // ---------------- Comandos de voz ----------------
    private fun iniciarReconhecimentoVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga um comando")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VOZ)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Reconhecimento de voz indisponível", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VOZ || resultCode != RESULT_OK) return

        val frase = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.lowercase(Locale("pt", "BR")) ?: return

        when {
            "alerta" in frase || "emergência" in frase -> emitirAlertaSeguranca()
            "ler" in frase || "mensagem" in frase || "mensagens" in frase -> lerTudo()
            "bluetooth" in frase || "fone" in frase -> abrirConfiguracoesBluetooth()
            else -> falar("Não entendi o comando: $frase")
        }
    }

    override fun onDestroy() {
        audioManager.unregisterAudioDeviceCallback(audioCallback)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_VOZ = 100
    }
}
