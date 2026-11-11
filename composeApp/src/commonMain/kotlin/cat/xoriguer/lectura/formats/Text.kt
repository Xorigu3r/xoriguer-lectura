package cat.xoriguer.lectura.formats

import cat.xoriguer.lectura.dades.Bloc
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut
import cat.xoriguer.lectura.dades.TipusBloc

/** Eines comunes per passar HTML/XML i text pla a blocs llegibles. */
object Text {

    /** UTF-8 si és vàlid; si no, Windows-1252 / Latin-1 (molts .txt antics en català). */
    fun decodifica(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b = b.copyOfRange(3, b.size)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return utf16(b, 2, little = true)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return utf16(b, 2, little = false)
        val s = b.decodeToString()
        if ('\uFFFD' !in s) return s
        return buildString(b.size) { for (x in b) append(cp1252(x.toInt() and 0xFF)) }
    }

    private fun utf16(b: ByteArray, from: Int, little: Boolean) = buildString {
        var i = from
        while (i + 1 < b.size) {
            val lo = b[i].toInt() and 0xFF; val hi = b[i + 1].toInt() and 0xFF
            append((if (little) (hi shl 8) or lo else (lo shl 8) or hi).toChar()); i += 2
        }
    }

    private val CP1252 = "€\u0081‚ƒ„…†‡ˆ‰Š‹Œ\u008DŽ\u008F\u0090‘’“”•–—˜™š›œ\u009DžŸ"
    private fun cp1252(v: Int): Char = if (v in 0x80..0x9F) CP1252[v - 0x80] else v.toChar()

    /** Text pla: paràgrafs separats per línies buides (o per cada línia si no n'hi ha). */
    fun deTextPla(s: String): Contingut {
        val net = s.replace("\r\n", "\n").replace('\r', '\n')
        val parts = if (net.contains("\n\n")) net.split(Regex("\n\\s*\n")) else net.split('\n')
        val blocs = parts.map { it.replace(Regex("\\s*\n\\s*"), " ").trim() }.filter { it.isNotEmpty() }.map { Bloc(it) }
        val caps = blocs.withIndex()
            .filter { (_, b) -> b.text.length < 60 && Regex("^(cap[ií]tol|chapter|cap[ií]tulo|part|parte)\\b.*", RegexOption.IGNORE_CASE).matches(b.text) }
            .map { Capitol(it.value.text, it.index) }
        return Contingut(blocs.mapIndexed { i, b -> if (caps.any { it.bloc == i }) b.copy(tipus = TipusBloc.TITOL) else b }, caps)
    }

    private val reBloc = Regex("<(/?)(p|div|h[1-6]|li|br|tr|blockquote|section|title|subtitle|v|stanza|empty-line)\\b[^>]*?(/?)>", RegexOption.IGNORE_CASE)
    private val reEtiqueta = Regex("<[^>]+>")
    private val reEspais = Regex("\\s+")

    /** HTML/XHTML/XML -> blocs. Els títols (h1-h3, <title> de FB2) es marquen. */
    fun deHtml(html: String, sortida: MutableList<Bloc>, capitols: MutableList<Capitol>) {
        val cos = html.substringAfter("<body", html).let { if (it === html) html else it.substringAfter('>') }
            .replace(Regex("<(script|style|head)\\b[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
        var tipus = TipusBloc.PARAGRAF
        val actual = StringBuilder()
        fun buida() {
            val t = entitats(reEtiqueta.replace(actual, "")).replace(reEspais, " ").trim()
            if (t.isNotEmpty()) {
                if (tipus != TipusBloc.PARAGRAF && t.length < 120) capitols += Capitol(t, sortida.size)
                sortida += Bloc(t, if (t.length < 120) tipus else TipusBloc.PARAGRAF)
            }
            actual.clear()
        }
        var pos = 0
        for (m in reBloc.findAll(cos)) {
            actual.append(cos, pos, m.range.first)
            pos = m.range.last + 1
            val tanca = m.groupValues[1] == "/"
            val nom = m.groupValues[2].lowercase()
            buida()
            if (!tanca) tipus = when (nom) {
                "h1", "h2", "title" -> TipusBloc.TITOL
                "h3", "h4", "subtitle" -> TipusBloc.SUBTITOL
                else -> if (tipus != TipusBloc.PARAGRAF && nom == "p") tipus else TipusBloc.PARAGRAF
            } else if (nom.startsWith("h") || nom == "title" || nom == "subtitle") tipus = TipusBloc.PARAGRAF
        }
        actual.append(cos, pos, cos.length)
        buida()
    }

    private val reEntitat = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val NOMS = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "iexcl" to "¡", "iquest" to "¿", "shy" to "",
    )

    fun entitats(s: String): String = reEntitat.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { codi(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { codi(it) } ?: m.value
            else -> NOMS[e] ?: NOMS[e.lowercase()] ?: m.value
        }
    }

    private fun codi(c: Int): String = if (c < 0x10000) c.toChar().toString() else {
        val v = c - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }

    /** Primer valor d'una etiqueta XML (amb o sense prefix, p. ex. dc:title). */
    fun etiqueta(xml: String, nom: String): String? =
        Regex("<(?:[a-zA-Z0-9]+:)?$nom\\b[^>]*>([\\s\\S]*?)</(?:[a-zA-Z0-9]+:)?$nom>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.let { entitats(reEtiqueta.replace(it, "")).replace(reEspais, " ").trim() }
            ?.takeIf { it.isNotEmpty() }

    fun atribut(etiqueta: String, nom: String): String? =
        Regex("\\b$nom\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(etiqueta)?.groupValues?.get(2)
}
