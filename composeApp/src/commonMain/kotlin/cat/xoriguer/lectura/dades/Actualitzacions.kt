package cat.xoriguer.lectura.dades

import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.VERSIO_APP
import cat.xoriguer.lectura.formats.Json
import cat.xoriguer.lectura.formats.llista
import cat.xoriguer.lectura.formats.text
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Una versió publicada més nova que la que hi ha instal·lada. */
data class VersioNova(val versio: String, val notes: String, val baixada: String, val pagina: String)

/**
 * Avís de versions noves: mira la darrera Release de GitHub (com a molt un cop al dia)
 * i diu si és més nova que la instal·lada. No instal·la res: només n'ofereix l'enllaç.
 */
object Actualitzacions {
    const val REPO = "Xorigu3r/xoriguer-lectura"
    private const val FITXER = "actualitzacio.txt"
    private const val UN_DIA = 24L * 60 * 60 * 1000

    /** Versió que l'usuari ha dit "ara no" (no se li torna a ensenyar aquesta). */
    var ignorada: String?
        get() = Plataforma.llegeixDada("actualitzacio-ignorada.txt")?.trim()?.ifEmpty { null }
        set(v) = Plataforma.desaDada("actualitzacio-ignorada.txt", v.orEmpty())

    /** "0.10.1" > "0.9.3": es compara número a número. */
    fun esMesNova(nova: String, actual: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(nova); val b = parts(actual)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Llegeix la resposta de l'API de GitHub (/releases/latest) i tria el fitxer d'aquesta plataforma. */
    fun deRelease(json: String, plataforma: String): VersioNova? {
        val j = Json.llegeix(json)
        val tag = j.text("tag_name") ?: return null
        val pagina = j.text("html_url") ?: "https://github.com/$REPO/releases/latest"
        val fitxers = j.llista("assets").mapNotNull { it.text("browser_download_url") }
        val baixada = when (plataforma) {
            "Android" -> fitxers.firstOrNull { it.endsWith(".apk") }
            "Windows" -> fitxers.firstOrNull { it.endsWith(".zip") || it.endsWith(".msi") || it.endsWith(".exe") }
            else -> null
        } ?: pagina
        val notes = j.text("body").orEmpty().replace("**", "").replace("`", "").trim()
        return VersioNova(tag.removePrefix("v"), notes, baixada, pagina)
    }

    /**
     * Torna la versió nova si n'hi ha. Sense `ara`, com a molt consulta internet un cop al dia
     * (la resta de vegades fa servir el que va trobar l'última vegada).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun comprova(ara: Boolean = false): VersioNova? {
        val avui = Clock.System.now().toEpochMilliseconds()
        val desat = Plataforma.llegeixDada(FITXER)?.lines()
        val quan = desat?.firstOrNull()?.toLongOrNull() ?: 0L
        val trobada = if (!ara && avui - quan < UN_DIA && desat != null) {
            desat.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { v ->
                VersioNova(v, desat.drop(4).joinToString("\n"), desat.getOrElse(2) { "" }, desat.getOrElse(3) { "" })
            }
        } else {
            val r = deRelease(Plataforma.descarrega("https://api.github.com/repos/$REPO/releases/latest").decodeToString(), Plataforma.nom)
            Plataforma.desaDada(FITXER, listOf(avui.toString(), r?.versio.orEmpty(), r?.baixada.orEmpty(), r?.pagina.orEmpty(), r?.notes.orEmpty()).joinToString("\n"))
            r
        }
        return trobada?.takeIf { esMesNova(it.versio, VERSIO_APP) }
    }
}
