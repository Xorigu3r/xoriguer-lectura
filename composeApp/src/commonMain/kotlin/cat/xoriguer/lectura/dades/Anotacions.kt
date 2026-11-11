package cat.xoriguer.lectura.dades

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import cat.xoriguer.lectura.Plataforma
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class TipusAnotacio { CITA, MARCADOR }

/** Colors per ressaltar cites. */
enum class ColorCita(val nom: String, val color: Color) {
    GROC("Groc", Color(0xFFFFD54F)),
    VERD("Verd", Color(0xFF81C784)),
    BLAU("Blau", Color(0xFF64B5F6)),
    ROSA("Rosa", Color(0xFFF48FB1)),
    TARONJA("Taronja", Color(0xFFF7931E)),
    LILA("Lila", Color(0xFFB39DDB)),
}

/**
 * Una cita (text ressaltat, amb nota o sense) o un marcador.
 * `bloc` és el paràgraf (o la pàgina als PDF); `inici`..`fi` és el tros ressaltat dins del paràgraf.
 */
data class Anotacio(
    val id: String,
    val cami: String,
    val titolLlibre: String,
    val autor: String?,
    val tipus: TipusAnotacio,
    val bloc: Int,
    val inici: Int,
    val fi: Int,
    val text: String,
    val nota: String,
    val color: ColorCita,
    val data: Long,
    val percent: Float,
) {
    val teNota get() = nota.isNotBlank()

    @OptIn(ExperimentalTime::class)
    fun dataCurta(): String {
        val d = Instant.fromEpochMilliseconds(data).toString() // 2026-11-11T18:30:00Z
        return "${d.substring(8, 10)}/${d.substring(5, 7)}/${d.substring(0, 4)}"
    }
}

/** Totes les cites, notes i marcadors de tots els llibres (fitxer anotacions.tsv). */
class Anotacions(private val fitxer: String? = "anotacions.tsv") {
    val totes = mutableStateListOf<Anotacio>()

    init {
        fitxer?.let { Plataforma.llegeixDada(it) }?.lineSequence()?.mapNotNull(::deLinia)?.let(totes::addAll)
    }

    fun deLlibre(cami: String) = totes.filter { it.cami == cami }

    @OptIn(ExperimentalTime::class)
    fun nova(
        llibre: Llibre, tipus: TipusAnotacio, bloc: Int, inici: Int, fi: Int, text: String,
        color: ColorCita = ColorCita.GROC, nota: String = "", percent: Float = 0f,
    ): Anotacio {
        val ara = Clock.System.now().toEpochMilliseconds()
        val a = Anotacio(
            "$ara-${Random.nextInt(0, 1 shl 30).toString(36)}", llibre.cami, llibre.titol, llibre.autor,
            tipus, bloc, inici, fi, text, nota, color, ara, percent,
        )
        totes += a; desa(); return a
    }

    fun actualitza(a: Anotacio) {
        val i = totes.indexOfFirst { it.id == a.id }
        if (i >= 0) { totes[i] = a; desa() }
    }

    fun esborra(a: Anotacio) { if (totes.removeAll { it.id == a.id }) desa() }

    /** El fitxer ha canviat de nom o de lloc: les cites el segueixen. */
    fun canviaCami(vell: String, nou: String) {
        var canvi = false
        for (i in totes.indices) if (totes[i].cami == vell) { totes[i] = totes[i].copy(cami = nou); canvi = true }
        if (canvi) desa()
    }

    fun recarrega() {
        totes.clear()
        fitxer?.let { Plataforma.llegeixDada(it) }?.lineSequence()?.mapNotNull(::deLinia)?.let(totes::addAll)
    }

    fun marcador(cami: String, bloc: Int) =totes.firstOrNull { it.cami == cami && it.tipus == TipusAnotacio.MARCADOR && it.bloc == bloc }

    private fun desa() { fitxer?.let { Plataforma.desaDada(it, totes.joinToString("\n", transform = ::aLinia)) } }

    companion object {
        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")
        private fun desc(s: String) = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> append('\t'); 'n' -> append('\n'); else -> append(s[i + 1]) }
                    i += 2
                } else { append(c); i++ }
            }
        }

        fun aLinia(a: Anotacio) = listOf(
            a.id, a.cami, a.titolLlibre, a.autor ?: "", a.tipus.name, a.bloc, a.inici, a.fi,
            a.text, a.nota, a.color.name, a.data, a.percent,
        ).joinToString("\t") { esc(it.toString()) }

        fun deLinia(l: String): Anotacio? = runCatching {
            if (l.isBlank()) return null
            val p = l.split('\t').map(::desc)
            Anotacio(
                p[0], p[1], p[2], p[3].ifEmpty { null }, TipusAnotacio.valueOf(p[4]), p[5].toInt(), p[6].toInt(), p[7].toInt(),
                p[8], p[9], ColorCita.valueOf(p[10]), p[11].toLong(), p[12].toFloat(),
            )
        }.getOrNull()

        /** Text per copiar o compartir totes les cites d'una llista. */
        fun comText(llista: List<Anotacio>): String = llista.groupBy { it.cami }.values.joinToString("\n\n") { g ->
            val l = g.first()
            buildString {
                append("# ").append(l.titolLlibre); l.autor?.let { append(" — ").append(it) }; append('\n')
                g.sortedBy { it.bloc }.forEach { a ->
                    append('\n')
                    if (a.tipus == TipusAnotacio.MARCADOR) append("🔖 ").append(a.text).append('\n')
                    else {
                        append("> ").append(a.text).append('\n')
                        if (a.teNota) append("✎ ").append(a.nota).append('\n')
                    }
                }
            }.trimEnd()
        }
    }
}
