package cat.xoriguer.lectura.formats

import cat.xoriguer.lectura.ArxiuZip
import cat.xoriguer.lectura.dades.Bloc
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut
import cat.xoriguer.lectura.dades.TipusBloc

/** Documents d'oficina: Word (DOCX), LibreOffice (ODT) i RTF. Se'n treu el text amb títols i paràgrafs. */
object Oficina {

    // ---------- DOCX ----------
    fun docxMetadades(z: ArxiuZip): Metadades {
        val core = z.llegeix("docProps/core.xml")?.decodeToString().orEmpty()
        return Metadades(Text.etiqueta(core, "title"), Text.etiqueta(core, "creator"))
    }

    fun docx(z: ArxiuZip): Contingut {
        val xml = z.llegeix("word/document.xml")?.decodeToString() ?: error("No és un document de Word")
        val blocs = mutableListOf<Bloc>(); val caps = mutableListOf<Capitol>()
        Regex("<w:p[ >][\\s\\S]*?</w:p>").findAll(xml).forEach { m ->
            val p = m.value
            val estil = Regex("<w:pStyle w:val=\"([^\"]+)\"").find(p)?.groupValues?.get(1)?.lowercase().orEmpty()
            val text = buildString {
                Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>").findAll(p).forEach { t ->
                    if (t.value.startsWith("<w:t")) append(Text.entitats(t.groupValues[1])) else append(' ')
                }
            }.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return@forEach
            val tipus = titol(estil)
            if (tipus != TipusBloc.PARAGRAF) caps += Capitol(text, blocs.size)
            blocs += Bloc(text, tipus)
        }
        return Contingut(blocs, caps)
    }

    /** "Heading1", "Ttol1", "Title", "Titre1"... → títol. */
    private fun titol(estil: String): TipusBloc = when {
        estil == "title" || estil.matches(Regex("(heading|t[ií]tol|t[ií]tulo|titre|ttol|berschrift)\\s*1")) -> TipusBloc.TITOL
        estil.matches(Regex("(heading|t[ií]tol|t[ií]tulo|titre|ttol|berschrift)\\s*[23]")) -> TipusBloc.SUBTITOL
        else -> TipusBloc.PARAGRAF
    }

    // ---------- ODT ----------
    fun odtMetadades(z: ArxiuZip): Metadades {
        val meta = z.llegeix("meta.xml")?.decodeToString().orEmpty()
        return Metadades(Text.etiqueta(meta, "title"), Text.etiqueta(meta, "initial-creator") ?: Text.etiqueta(meta, "creator"))
    }

    fun odt(z: ArxiuZip): Contingut {
        val xml = z.llegeix("content.xml")?.decodeToString() ?: error("No és un document ODT")
        val cos = xml.substringAfter("<office:text", xml)
            .replace(Regex("<text:s(?: text:c=\"(\\d+)\")?/>")) { " ".repeat(it.groupValues[1].toIntOrNull() ?: 1) }
            .replace(Regex("<text:(tab|line-break)/>"), " ")
        val blocs = mutableListOf<Bloc>(); val caps = mutableListOf<Capitol>()
        Regex("<text:(h|p)\\b([^>]*)>([\\s\\S]*?)</text:\\1>").findAll(cos).forEach { m ->
            val text = Text.entitats(m.groupValues[3].replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return@forEach
            val nivell = Regex("outline-level=\"(\\d)\"").find(m.groupValues[2])?.groupValues?.get(1)?.toInt()
            val tipus = if (m.groupValues[1] == "h") (if ((nivell ?: 1) <= 1) TipusBloc.TITOL else TipusBloc.SUBTITOL) else TipusBloc.PARAGRAF
            if (tipus != TipusBloc.PARAGRAF) caps += Capitol(text, blocs.size)
            blocs += Bloc(text, tipus)
        }
        return Contingut(blocs, caps)
    }

    // ---------- RTF ----------
    private val DESTINS_IGNORATS = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr", "footerl", "footerr",
        "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator", "themedata", "colorschememapping",
        "latentstyles", "datastore", "object", "fldinst", "filetbl", "revtbl", "pgdsctbl", "footnote",
    )

    fun rtf(s: String): Contingut {
        val paragrafs = mutableListOf<String>()
        val actual = StringBuilder()
        // pila de grups: true = el grup s'ignora
        val pila = ArrayDeque<Boolean>(); var ignora = false
        var ucSalta = 1; var salta = 0
        var i = 0
        fun emet(c: Char) { if (salta > 0) salta-- else if (!ignora) actual.append(c) }
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '{' -> { pila.addLast(ignora); i++ }
                '}' -> { ignora = pila.removeLastOrNull() ?: false; i++ }
                '\\' -> {
                    i++
                    if (i >= s.length) break
                    val n = s[i]
                    when {
                        n == '\\' || n == '{' || n == '}' -> { emet(n); i++ }
                        n == '*' -> { ignora = true; i++ }
                        n == '\'' -> {
                            val hex = s.substring(i + 1, minOf(i + 3, s.length)).toIntOrNull(16)
                            if (hex != null) emet(Text.cp1252(hex)); i += 3
                        }
                        n == '~' -> { emet(' '); i++ }
                        n == '-' || n == '_' -> i++
                        n == '\n' || n == '\r' -> { if (!ignora) { paragrafs += actual.toString(); actual.clear() }; i++ }
                        n.isLetter() -> {
                            val ini = i
                            while (i < s.length && s[i].isLetter()) i++
                            val paraula = s.substring(ini, i)
                            val numIni = i
                            if (i < s.length && (s[i] == '-' || s[i].isDigit())) { i++; while (i < s.length && s[i].isDigit()) i++ }
                            val num = s.substring(numIni, i).toIntOrNull()
                            if (i < s.length && s[i] == ' ') i++
                            when (paraula) {
                                "par", "line", "sect", "page" -> if (!ignora) { paragrafs += actual.toString(); actual.clear() }
                                "tab" -> emet(' ')
                                "u" -> if (num != null) { emet((if (num < 0) num + 65536 else num).toChar()); salta = ucSalta }
                                "uc" -> ucSalta = num ?: 1
                                "emdash" -> emet('—'); "endash" -> emet('–'); "bullet" -> emet('•')
                                "lquote" -> emet('‘'); "rquote" -> emet('’'); "ldblquote" -> emet('“'); "rdblquote" -> emet('”')
                                else -> if (paraula in DESTINS_IGNORATS) ignora = true
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> { emet(c); i++ }
            }
        }
        paragrafs += actual.toString()
        val blocs = paragrafs.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.map { Bloc(it) }
        return Contingut(blocs, emptyList())
    }
}
