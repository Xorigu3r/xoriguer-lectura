package cat.xoriguer.lectura.dades

import cat.xoriguer.lectura.Plataforma

/**
 * Formes → paraula base (refent → refer, cançons → cançó), amb les taules que ja fa servir el joc Paraules
 * (generades del diccionari de Softcatalà, llicència GPL/LGPL). Són a la web de Xoriguer partides en trossos:
 * es baixa només el tros de la paraula que es busca. Cal connexió.
 */
object Lemes {
    private const val WEB = "https://xorigu3r.github.io/paraules/dades/lemes"
    private val ALFA = mapOf("ca" to "ABCDEFGHIJLMNOPQRSTUVXZÇŁŊ", "es" to "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ")
    private val trossos = mutableMapOf<String, List<Pair<String, String>>>()

    private const val AMB = "ÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜ"
    private const val SENSE = "AAAAEEEEIIIIOOOOUUUU"

    /** El mateix que `encode` de Paraules: majúscules, sense accents (excepte Ç, Ñ), L·L, NY i QU com una sola lletra. */
    fun codifica(paraula: String, idioma: String): List<Int>? {
        val alfa = ALFA[idioma] ?: return null
        var s = paraula.uppercase()
        if (idioma == "ca") s = s.replace(Regex("L[·.ŀ]L|ĿL"), "Ł").replace("NY", "Ŋ").replace(Regex("QÜ|QU"), "Q")
        return s.map { c ->
            val n = AMB.indexOf(c).let { if (it >= 0) SENSE[it] else c }
            alfa.indexOf(n).takeIf { it >= 0 } ?: return null
        }
    }

    /** Nom del tros: les tres primeres lletres, cadascuna amb dues xifres (com fa Paraules). */
    fun clau(codi: List<Int>) = codi.take(3).joinToString("") { it.toString().padStart(2, '0') }

    /** Paraules base d'una forma ("refent" → ["refer"]). Buit si no se'n sap res. */
    suspend fun bases(paraula: String, idioma: String): List<String> {
        val codi = codifica(paraula, idioma) ?: return emptyList()
        if (codi.size < 2) return emptyList()
        val k = "$idioma/${clau(codi)}"
        val tros = trossos[k] ?: runCatching {
            Plataforma.descarrega("$WEB/$k.txt").decodeToString().lines().filter { it.isNotBlank() }.map { l ->
                val p = l.split('\t'); p[0] to (p.getOrNull(1)?.ifBlank { null } ?: p[0])
            }
        }.getOrDefault(emptyList()).also { trossos[k] = it }
        return tros.filter { (f, _) -> codifica(f, idioma) == codi }.map { it.second }.distinct()
    }

    private val PRONOMS = setOf("me", "te", "se", "nos", "vos", "los", "les", "lo", "la", "li", "hi", "ho", "ne", "us", "m", "t", "s", "n", "l", "ns", "ls")

    /**
     * Treu els pronoms que s'enganxen al verb i l'article apostrofat: «refent-les» → «refent»,
     * «dóna-m'ho» → «dóna», «l'estufa» → «estufa». Torna la mateixa paraula si no n'hi ha.
     */
    fun senseClitics(paraula: String): String {
        var p = paraula.replace('’', '\'')
        Regex("^(?i)(l|d|m|n|s|t)'(?=\\p{L})").find(p)?.let { p = p.substring(it.range.last + 1) }
        val tall = p.indexOfFirst { it == '-' || it == '\'' }
        if (tall > 0) {
            val resta = p.substring(tall + 1).split('-', '\'').filter { it.isNotEmpty() }
            if (resta.isNotEmpty() && resta.all { it.lowercase() in PRONOMS }) p = p.substring(0, tall)
        }
        return p
    }
}
