package cat.xoriguer.lectura.dades

import androidx.compose.runtime.mutableStateListOf
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.formats.Json
import cat.xoriguer.lectura.formats.obj
import cat.xoriguer.lectura.formats.text
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Una paraula buscada mentre es llegia. */
data class Paraula(val paraula: String, val definicio: String, val idioma: String, val llibre: String, val data: Long)

/** Definicions del Viquidiccionari / Wiktionary, i la llista de paraules buscades (secció Diccionari). */
class Diccionari(private val fitxer: String? = "diccionari.tsv") {
    val paraules = mutableStateListOf<Paraula>()

    init { recarrega() }

    fun recarrega() {
        paraules.clear()
        fitxer?.let { Plataforma.llegeixDada(it) }?.lines()?.mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 5) null
            else Paraula(p[0], p[1].replace("\\n", "\n"), p[2], p[3], p[4].toLongOrNull() ?: 0)
        }?.let(paraules::addAll)
    }

    @OptIn(ExperimentalTime::class)
    fun desa(paraula: String, definicio: String, idioma: String, llibre: String) {
        paraules.removeAll { it.paraula.equals(paraula, true) && it.idioma == idioma }
        paraules.add(0, Paraula(paraula, definicio, idioma, llibre, Clock.System.now().toEpochMilliseconds()))
        desaFitxer()
    }

    fun esborra(p: Paraula) { paraules.remove(p); desaFitxer() }

    private fun desaFitxer() {
        fitxer?.let { f ->
            Plataforma.desaDada(f, paraules.joinToString("\n") {
                listOf(it.paraula, it.definicio.replace("\t", " ").replace("\n", "\\n"), it.idioma, it.llibre.replace("\t", " "), it.data).joinToString("\t")
            })
        }
    }

    companion object {
        private val NOMS = mapOf("ca" to "Català", "es" to "Español", "en" to "English", "fr" to "Français", "it" to "Italiano", "pt" to "Português")
        private val FORA = Regex(
            "(Pronúncia|Etimologia|Etimología|Etymology|Pronunciation|Derivats|Compostos|Traduccions|Traducciones|Translations|Miscel·lània|Vegeu també|" +
                "Véase también|See also|Locuciones|Referencias|Referències|Sinònims|Sinónimos|Synonyms|Anagrames|Anagrams|Variants|Informació|Información|Notes|Further reading)",
            RegexOption.IGNORE_CASE,
        )

        /** Busca la definició. Prova la paraula tal com és i en minúscules. Null si no hi és. */
        suspend fun busca(paraula: String, idioma: String): String? {
            val net = paraula.trim().trim { !it.isLetter() && it != '·' && it != '-' && it != '\'' }
            if (net.isEmpty()) return null
            for (p in listOf(net, net.lowercase()).distinct()) {
                val url = "https://$idioma.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&redirects=1&format=json&titles=" +
                    p.encodeToByteArray().joinToString("") { b -> val c = b.toInt() and 0xFF; if (c.toChar().isLetterOrDigit() && c < 128) c.toChar().toString() else "%" + c.toString(16).padStart(2, '0').uppercase() }
                val j = Json.llegeix(Plataforma.descarrega(url).decodeToString())
                val pagines = j.obj("query").obj("pages") ?: continue
                val extracte = pagines.values.firstNotNullOfOrNull { it.text("extract") } ?: continue
                neteja(extracte, idioma)?.let { return it }
            }
            return null
        }

        /** Es queda amb la secció de l'idioma i en treu pronúncia, traduccions, etc. */
        fun neteja(extracte: String, idioma: String): String? {
            val nom = NOMS[idioma]
            val linies = extracte.lines()
            val ini = nom?.let { n -> linies.indexOfFirst { it.trim() == "== $n ==" } } ?: -1
            val tros = if (ini >= 0) linies.drop(ini + 1).takeWhile { !Regex("^==\\s[^=].*\\s==$").matches(it.trim()) } else linies
            val sortida = mutableListOf<String>()
            var salta = false
            for (l in tros) {
                val t = l.trim()
                val cap = Regex("^(=+)\\s*(.*?)\\s*=+$").find(t)
                if (cap != null) {
                    val nivell = cap.groupValues[1].length; val titol = cap.groupValues[2]
                    salta = nivell >= 4 || FORA.containsMatchIn(titol)
                    if (!salta) sortida += "■ $titol"
                    continue
                }
                if (salta || t.isEmpty() || t.startsWith("Pronúncia") || t.startsWith("Etimologia")) continue
                sortida += t
            }
            return sortida.joinToString("\n").trim().takeIf { it.length > 3 }
        }
    }
}

/** Idioma d'un text, per triar veu i diccionari (heurística amb paraules molt freqüents). */
object Idioma {
    private val PARAULES = mapOf(
        "ca" to setOf("i", "que", "de", "la", "el", "els", "les", "amb", "per", "és", "una", "dels", "va", "però", "seva", "també", "més", "aquest", "molt"),
        "es" to setOf("y", "que", "de", "la", "el", "los", "las", "con", "por", "es", "una", "del", "pero", "su", "también", "más", "este", "muy", "se"),
        "en" to setOf("the", "and", "of", "to", "is", "in", "that", "it", "with", "was", "for", "his", "her", "he", "she", "but", "not"),
        "fr" to setOf("et", "le", "les", "des", "est", "une", "dans", "que", "qui", "pas", "pour", "avec", "sur", "il", "elle", "mais", "du"),
    )

    fun detecta(text: String): String {
        val mots = text.lowercase().split(Regex("[^\\p{L}·']+")).filter { it.isNotEmpty() }.take(3000)
        return PARAULES.maxByOrNull { (_, s) -> mots.count { it in s } }?.key ?: "ca"
    }
}
