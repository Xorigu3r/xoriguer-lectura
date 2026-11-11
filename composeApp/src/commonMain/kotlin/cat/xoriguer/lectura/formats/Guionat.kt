package cat.xoriguer.lectura.formats

/**
 * Partició de mots a final de línia (català i castellà) amb guions suaus (U+00AD): el motor de text només
 * talla per aquests punts i hi posa el guionet. Com que el text mostrat té caràcters de més, es guarda la
 * correspondència amb el text original (les cites, la cerca i la veu treballen sempre amb l'original).
 */
class Guionat private constructor(val text: String, private val aMostrat: IntArray, private val aOriginal: IntArray) {
    /** Índex del text original → índex del text mostrat. */
    fun mostrat(i: Int) = aMostrat[i.coerceIn(0, aMostrat.lastIndex)]
    /** Índex del text mostrat → índex del text original (un guió suau compta com la lletra que el segueix). */
    fun original(i: Int) = aOriginal[i.coerceIn(0, aOriginal.lastIndex)]

    companion object {
        const val GUIO = '­'
        private const val VOCALS = "aeiouàáèéíïòóúüy"
        private const val LLETRES_MIN_ABANS = 2
        private const val LLETRES_MIN_DESPRES = 3

        /** Grups de dues consonants que no es separen mai (bl, tr…). */
        private val INSEPARABLES = setOf("bl", "br", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "pl", "pr", "tr", "kl", "kr", "vr")

        fun de(text: String, idioma: String): Guionat {
            val punts = BooleanArray(text.length + 1) // punts[i]: es pot tallar abans del caràcter i
            if (idioma == "ca" || idioma == "es") {
                var i = 0
                while (i < text.length) {
                    if (!text[i].isLetter()) { i++; continue }
                    var j = i
                    while (j < text.length && (text[j].isLetter() || (text[j] == '·' && j + 1 < text.length && text[j + 1].isLetter()))) j++
                    sillabes(text.substring(i, j), idioma).forEach { punts[i + it] = true }
                    i = j
                }
            }
            val n = punts.count { it }
            val sb = StringBuilder(text.length + n)
            val aM = IntArray(text.length + 1)
            val aO = IntArray(text.length + n + 1)
            for (k in text.indices) {
                if (punts[k]) { aO[sb.length] = k; sb.append(GUIO) }
                aM[k] = sb.length; aO[sb.length] = k; sb.append(text[k])
            }
            aM[text.length] = sb.length; aO[sb.length] = text.length
            return Guionat(sb.toString(), aM, aO)
        }

        /** Posicions (dins de la paraula) on es pot tallar. */
        fun sillabes(paraula: String, idioma: String): List<Int> {
            if (paraula.count { it.isLetter() } < LLETRES_MIN_ABANS + LLETRES_MIN_DESPRES) return emptyList()
            val p = paraula.lowercase()
            // cada lletra és vocal (V) o consonant (C); "qu"/"gu" davant de e/i: la u no és vocal; la i entre vocals fa de consonant
            val tipus = CharArray(p.length) { k ->
                val c = p[k]
                when {
                    c == '·' -> '·'
                    c == 'u' && k > 0 && (p[k - 1] == 'q' || p[k - 1] == 'g') && k + 1 < p.length && p[k + 1] in "eiéèí" -> 'C'
                    c == 'y' && (k + 1 >= p.length || p[k + 1] !in VOCALS) -> if (k > 0 && p[k - 1] in VOCALS) 'V' else 'C'
                    c in VOCALS -> 'V'
                    else -> 'C'
                }
            }
            val r = ArrayList<Int>()
            var k = 0
            // es busca cada tram consonant entre dues vocals
            while (k < p.length) {
                if (tipus[k] != 'V') { k++; continue }
                var c0 = k + 1
                while (c0 < p.length && tipus[c0] == 'V') c0++ // vocals seguides: no es parteixen (diftongs)
                var c1 = c0
                while (c1 < p.length && tipus[c1] != 'V') c1++
                if (c1 >= p.length) break // no hi ha més vocals: final de paraula
                val tall = tallEntre(p, c0, c1, idioma)
                if (tall != null) r += tall
                k = c1
            }
            return r.filter { it >= LLETRES_MIN_ABANS && p.length - it >= LLETRES_MIN_DESPRES && p[it - 1] != '·' && p[it] != '·' }
        }

        /** On es talla el grup de consonants p[a..b) que hi ha entre dues vocals. */
        private fun tallEntre(p: String, a: Int, b: Int, idioma: String): Int? {
            val g = p.substring(a, b)
            if (g.isEmpty()) return null
            // l·l: es parteix com «col·le-gi» no; la norma diu «col-legi»: no hi tallem (evita confusions)
            if ('·' in g) return null
            // unitats que no es parteixen
            // qu/gu davant de e/i també són una sola consonant (xo-ri-guer, ar-que-ta)
            val unitats = if (idioma == "es") setOf("ch", "ll", "rr", "qu", "gu") else setOf("ny", "ll", "qu", "gu")
            fun unitat(i: Int) = i + 1 < b && p.substring(i, i + 2) in unitats
            return when (g.length) {
                1 -> a                                                       // V-CV
                2 -> if (g in INSEPARABLES || g in unitats) a else a + 1     // V-CCV o VC-CV
                3 -> when {
                    unitat(a + 1) -> a + 1                                   // VC-ny-V
                    unitat(a) -> a + 2                                       // V-ll-C (rar)
                    g.substring(1) in INSEPARABLES -> a + 1                  // VC-CCV (cons-truc)
                    else -> a + 2                                            // VCC-CV (ins-tant)
                }
                else -> if (p.substring(b - 2, b) in INSEPARABLES) b - 2 else b - 1
            }
        }
    }
}
