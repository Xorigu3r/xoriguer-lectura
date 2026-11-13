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
        /** Seccions que no s'ensenyen (pronúncia, traduccions...). Sinònims i antònims sí, que ajuden a entendre-la. */
        private val FORA = Regex(
            "(Pronúncia|Etimologia|Etimología|Etymology|Pronunciation|Derivats|Compostos|Traduccions|Traducciones|Translations|Miscel·lània|Vegeu també|" +
                "Véase también|See also|Locuciones|Referencias|Referències|Anagrames|Anagrams|Informació|Información|Notes|Further reading|Relacionats|Hipònims|Hiperònims)",
            RegexOption.IGNORE_CASE,
        )
        private val UTILS = Regex("(Sinònims|Sinónimos|Synonyms|Antònims|Antónimos|Antonyms|Variants)", RegexOption.IGNORE_CASE)
        /** Línies que no diuen res del significat. */
        private val SOROLL = Regex("^(Pronúncia|Etimologia|Etimología|Rimes|Rima|Síl·labes|Sílabas|Separació|Anagrames|Homòfons|Parònims|Accentuació|Pronunciación|Etymology|Rhymes|Syllab)", RegexOption.IGNORE_CASE)
        /** "Forma femenina de malastruc", "Plural de casa", "Tercera persona del singular ... de cantar" → la paraula base. */
        private val REMISSIO = Regex(
            "^(?:Forma|Plural|Femení|Femenino|Masculí|Masculino|Primera|Segunda|Segona|Tercera|Participi|Participio|Gerundi|Gerundio|Infinitiu|Imperatiu|Feminine|Plural of|Inflection|Variant)" +
                "[^.]*?\\b(?:de|del|d'|of)\\s+([\\p{L}·'-]+)\\.?\\s*$",
            RegexOption.IGNORE_CASE,
        )

        private suspend fun extracte(paraula: String, idioma: String): String? {
            val url = "https://$idioma.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&redirects=1&format=json&titles=" +
                paraula.encodeToByteArray().joinToString("") { b -> val c = b.toInt() and 0xFF; if (c.toChar().isLetterOrDigit() && c < 128) c.toChar().toString() else "%" + c.toString(16).padStart(2, '0').uppercase() }
            val j = Json.llegeix(Plataforma.descarrega(url).decodeToString())
            return j.obj("query").obj("pages")?.values?.firstNotNullOfOrNull { it.text("extract") }
        }

        /**
         * Busca la definició. Prova la paraula tal com és i en minúscules. Si només diu que és una forma d'una altra
         * paraula (femení, plural, temps verbal…), hi afegeix la definició de la paraula base. Null si no hi és.
         */
        suspend fun busca(paraula: String, idioma: String): String? {
            val net = paraula.trim().replace('’', '\'').trim { !it.isLetter() && it != '·' && it != '-' && it != '\'' }
            if (net.isEmpty()) return null
            // «refent-les» → «refent», «l'estufa» → «estufa»
            val sense = Lemes.senseClitics(net)
            val provar = listOf(net, net.lowercase(), sense, sense.lowercase()).distinct()
            for (p in provar) {
                val d = extracte(p, idioma)?.let { neteja(it, idioma) } ?: continue
                val base = paraulaBase(d)?.takeIf { !it.equals(p, true) } ?: return d
                val db = runCatching { extracte(base, idioma)?.let { neteja(it, idioma) } }.getOrNull() ?: return d
                return d + "\n◆ $base\n" + db
            }
            // El Viquidiccionari no té gaires formes verbals (refent, refeia…): es busca la paraula base a les
            // taules de Softcatalà (les mateixes del joc Paraules) i se'n dona la definició
            for (p in listOf(sense.lowercase(), net.lowercase()).distinct()) {
                for (base in runCatching { Lemes.bases(p, idioma) }.getOrDefault(emptyList())) {
                    if (base.equals(p, true)) continue
                    val db = runCatching { extracte(base, idioma)?.let { neteja(it, idioma) } }.getOrNull() ?: continue
                    return "«$p» és una forma de «$base».\n◆ $base\n" + db
                }
            }
            return null
        }

        /** La paraula base si la definició és una remissió ("Forma femenina de malastruc"). */
        fun paraulaBase(definicio: String): String? =
            definicio.lines().firstNotNullOfOrNull { REMISSIO.find(it.trim())?.groupValues?.get(1) }

        /** Es queda amb la secció de l'idioma i en treu pronúncia, traduccions, rimes, etc. */
        fun neteja(extracte: String, idioma: String): String? {
            val nom = NOMS[idioma]
            val linies = extracte.lines()
            val ini = nom?.let { n -> linies.indexOfFirst { it.trim() == "== $n ==" } } ?: -1
            val tros = if (ini >= 0) linies.drop(ini + 1).takeWhile { !Regex("^==\\s[^=].*\\s==$").matches(it.trim()) } else linies
            val sortida = mutableListOf<String>()
            var salta = false
            for (l in tros) {
                // fora caràcters invisibles (marques de direcció) que deixaven línies "buides"
                val t = l.replace(Regex("[\u200e\u200f\u202a-\u202e]"), "").trim()
                val cap = Regex("^(=+)\\s*(.*?)\\s*=+$").find(t)
                if (cap != null) {
                    val nivell = cap.groupValues[1].length; val titol = cap.groupValues[2]
                    salta = FORA.containsMatchIn(titol) || (nivell >= 4 && !UTILS.containsMatchIn(titol))
                    if (!salta) sortida += "■ $titol"
                    continue
                }
                if (salta || t.isEmpty() || SOROLL.containsMatchIn(t)) continue
                sortida += t
            }
            // un títol sense res a sota no serveix
            val net = sortida.filterIndexed { i, l -> !(l.startsWith("■") && (i == sortida.lastIndex || sortida[i + 1].startsWith("■"))) }
            return net.joinToString("\n").trim().takeIf { it.length > 3 }
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
