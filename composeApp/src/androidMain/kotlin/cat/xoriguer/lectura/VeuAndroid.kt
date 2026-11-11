package cat.xoriguer.lectura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Veu del sistema d'Android (TextToSpeech). La sessió de lectura manté un servei en primer pla. */
internal class VeuAndroid(private val ctx: Context) : Veu {
    private var tts: TextToSpeech? = null
    private var llest = false
    private var esperant: (() -> Unit)? = null
    @Volatile private var pendent: ((Boolean) -> Unit)? = null
    private var n = 0

    override val disponible = true

    private fun motor(despres: () -> Unit) {
        if (llest) { despres(); return }
        esperant = despres
        if (tts != null) return
        tts = TextToSpeech(ctx) { estat ->
            llest = estat == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { pendent?.let { pendent = null; it(true) } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { pendent?.let { pendent = null; it(false) } }
                override fun onStop(id: String?, interrupted: Boolean) { pendent?.let { pendent = null; it(false) } }
            })
            val f = esperant; esperant = null
            if (llest) f?.invoke() else pendent?.let { pendent = null; it(false) }
        }
    }

    override fun parla(text: String, idioma: String?, velocitat: Float, fet: (Boolean) -> Unit) {
        pendent = fet
        motor {
            val t = tts ?: return@motor
            idioma?.let { val l = Locale.forLanguageTag(it); if (t.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) t.language = l }
            t.setSpeechRate(velocitat)
            // TextToSpeech té un límit de llargada per tros
            val tros = text.take(TextToSpeech.getMaxSpeechInputLength() - 1)
            t.speak(tros, TextToSpeech.QUEUE_FLUSH, Bundle(), "t${n++}")
        }
    }

    override fun atura() {
        val f = pendent; pendent = null
        tts?.stop(); f?.invoke(false)
    }

    override fun sessio(activa: Boolean, titol: String) {
        val i = Intent(ctx, ServeiVeu::class.java).putExtra("titol", titol)
        if (activa) {
            Plataforma.demanaNotificacions()
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } else ctx.stopService(i)
    }
}

/** Servei en primer pla mentre es llegeix en veu alta: així continua amb la pantalla bloquejada. */
class ServeiVeu : Service() {
    private var bloqueig: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ATURA") {
            cat.xoriguer.lectura.dades.ControlVeu.atura?.invoke() ?: Plataforma.veu.atura(); stopSelf(); return START_NOT_STICKY
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("veu", "Lectura en veu alta", NotificationManager.IMPORTANCE_LOW))
        val obre = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val atura = PendingIntent.getService(this, 1, Intent(this, ServeiVeu::class.java).setAction("ATURA"), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "veu") else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Llegint en veu alta")
            .setContentText(intent?.getStringExtra("titol").orEmpty())
            .setContentIntent(obre).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Atura", atura).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(7, n)
        if (bloqueig == null) bloqueig = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lectura:veu").apply { acquire(6 * 60 * 60 * 1000L) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bloqueig?.let { if (it.isHeld) it.release() }; bloqueig = null
        super.onDestroy()
    }
}
