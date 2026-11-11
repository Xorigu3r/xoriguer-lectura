package cat.xoriguer.lectura

import java.io.BufferedWriter
import java.util.Base64
import kotlin.concurrent.thread

/**
 * Lectura en veu alta a Windows amb les veus del sistema (System.Speech / SAPI).
 * Hi ha un PowerShell obert que rep ordres per l'entrada: "PARLA <idioma> <velocitat> <text en base64>"
 * i escriu "FET" quan acaba cada tros. Per aturar, es tanca el procés (i es torna a obrir al següent tros).
 */
internal object VeuWindows : Veu {
    private var proces: Process? = null
    private var entrada: BufferedWriter? = null
    @Volatile private var pendent: ((Boolean) -> Unit)? = null

    private val SCRIPT = """
        ${'$'}ErrorActionPreference = 'SilentlyContinue'
        Add-Type -AssemblyName System.Speech
        ${'$'}s = New-Object System.Speech.Synthesis.SpeechSynthesizer
        ${'$'}s.SetOutputToDefaultAudioDevice()
        while (${'$'}true) {
          ${'$'}l = [Console]::In.ReadLine()
          if (${'$'}l -eq ${'$'}null) { break }
          ${'$'}p = ${'$'}l.Split(' ', 4)
          if (${'$'}p[0] -ne 'PARLA') { continue }
          ${'$'}v = ${'$'}s.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled -and ${'$'}_.VoiceInfo.Culture.Name -like (${'$'}p[1] + '*') } | Select-Object -First 1
          if (${'$'}v) { ${'$'}s.SelectVoice(${'$'}v.VoiceInfo.Name) }
          ${'$'}s.Rate = [int]${'$'}p[2]
          ${'$'}s.Speak([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(${'$'}p[3])))
          [Console]::Out.WriteLine('FET'); [Console]::Out.Flush()
        }
    """.trimIndent()

    override val disponible: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Synchronized
    private fun engega(): Boolean {
        if (proces?.isAlive == true) return true
        return runCatching {
            val codificat = Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))
            val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", codificat)
                .redirectErrorStream(true).start()
            proces = p; entrada = p.outputStream.bufferedWriter(Charsets.UTF_8)
            thread(isDaemon = true, name = "veu") {
                val lector = p.inputStream.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val l = runCatching { lector.readLine() }.getOrNull() ?: break
                    if (l.trim() == "FET") pendent?.let { pendent = null; it(true) }
                }
                // el procés s'ha tancat (aturat o error)
                pendent?.let { pendent = null; it(false) }
            }
            true
        }.getOrDefault(false)
    }

    override fun parla(text: String, idioma: String?, velocitat: Float, fet: (Boolean) -> Unit) {
        if (!engega()) { fet(false); return }
        pendent = fet
        // SAPI: Rate de -10 a 10; velocitat 1.0 = 0
        val rate = ((velocitat - 1f) * 10f).toInt().coerceIn(-10, 10)
        val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        runCatching { entrada!!.apply { write("PARLA ${idioma ?: "ca"} $rate $b64\n"); flush() } }
            .onFailure { pendent = null; fet(false) }
    }

    @Synchronized
    override fun atura() {
        val f = pendent; pendent = null
        proces?.destroyForcibly(); proces = null; entrada = null
        f?.invoke(false)
    }
}
