package cat.xoriguer.lectura.dades

import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.formats.Json
import cat.xoriguer.lectura.formats.Text
import cat.xoriguer.lectura.formats.llista
import cat.xoriguer.lectura.formats.obj
import cat.xoriguer.lectura.formats.text

/** Un llibre (o una carpeta d'un catàleg) que es pot baixar gratis i legalment. */
data class EntradaCataleg(
    val titol: String,
    val autor: String? = null,
    val resum: String? = null,
    val portada: String? = null,
    val baixada: String? = null,   // adreça del fitxer
    val extensio: String = "epub",
    val navega: String? = null,    // adreça d'un subcatàleg (OPDS)
    val idioma: String? = null,
)

data class PaginaCataleg(val entrades: List<EntradaCataleg>, val seguent: String? = null, val titol: String? = null)

/** D'on es baixen els llibres. Només fonts de domini públic o amb llicència lliure. */
sealed class FontLlibres(val nom: String, val descripcio: String) {
    /** Viquitexts / Wikisource: textos de domini públic, convertits a EPUB per ws-export. */
    class Wikisource(val idioma: String, nom: String, descripcio: String, val suggeriments: List<String>) : FontLlibres(nom, descripcio)
    /** Project Gutenberg (catàleg OPDS amb cerca i filtre d'idioma). */
    class Gutenberg(val idioma: String?, nom: String, descripcio: String) : FontLlibres(nom, descripcio)
    /** Qualsevol catàleg OPDS que afegeixi l'usuari (p. ex. el servidor de Calibre de casa). */
    class Opds(nom: String, val url: String) : FontLlibres(nom, url)

    companion object {
        val predeterminades = listOf(
            Wikisource("ca", "Viquitexts", "Clàssics en català de domini públic",
                listOf("Verdaguer", "Narcís Oller", "Víctor Català", "Joaquim Ruyra", "Àngel Guimerà", "Santiago Rusiñol", "Ramon Llull", "Ausiàs March")),
            Gutenberg("ca", "Gutenberg · català", "Project Gutenberg, llibres en català"),
            Gutenberg("es", "Gutenberg · castellà", "Project Gutenberg, llibres en castellà"),
            Wikisource("es", "Wikisource · castellà", "Clàssics en castellà de domini públic",
                listOf("Cervantes", "Galdós", "Bécquer", "Lope de Vega", "Clarín", "Emilia Pardo Bazán")),
            Gutenberg("en", "Gutenberg · anglès", "Project Gutenberg, llibres en anglès"),
            Gutenberg(null, "Gutenberg · tots", "Més de 70.000 llibres en tots els idiomes"),
        )
    }
}

object Cataleg {
    private fun codifica(s: String) = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-_.~") append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    /** Cerca en una font. Amb `seguent` es demana la pàgina següent d'una cerca anterior. */
    suspend fun cerca(font: FontLlibres, q: String, seguent: String? = null): PaginaCataleg = when (font) {
        is FontLlibres.Wikisource -> wikisource(font, q, seguent)
        is FontLlibres.Gutenberg -> {
            val consulta = listOfNotNull(q.trim().ifEmpty { null }, font.idioma?.let { "l.$it" }).joinToString(" ")
            opds(seguent ?: "https://www.gutenberg.org/ebooks/search.opds/?query=${codifica(consulta)}")
                .let { p -> p.copy(entrades = p.entrades.filter { it.baixada != null }) } // fora "ordena per…"
        }
        is FontLlibres.Opds -> opds(seguent ?: font.url).let { p ->
            if (q.isBlank()) p else p.copy(entrades = p.entrades.filter { it.titol.contains(q, true) || it.autor?.contains(q, true) == true })
        }
    }

    private suspend fun wikisource(font: FontLlibres.Wikisource, q: String, seguent: String?): PaginaCataleg {
        if (q.isBlank()) return PaginaCataleg(emptyList())
        val offset = seguent?.toIntOrNull() ?: 0
        val api = "https://${font.idioma}.wikisource.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=40" +
            "&sroffset=$offset&format=json&srsearch=${codifica(q)}"
        val j = Json.llegeix(Plataforma.descarrega(api).decodeToString())
        val res = j.obj("query").llista("search")
        val entrades = res.mapNotNull { r ->
            val titol = r.text("title") ?: return@mapNotNull null
            if ('/' in titol) return@mapNotNull null // capítols d'una obra: ja surt l'obra sencera
            val resum = r.text("snippet")?.replace(Regex("<[^>]+>"), "")?.let(Text::entitats)?.trim()
            EntradaCataleg(
                titol, resum = resum, idioma = font.idioma,
                baixada = "https://ws-export.wmcloud.org/?format=epub&lang=${font.idioma}&page=${codifica(titol.replace(' ', '_'))}",
            )
        }
        val mes = j.obj("continue")?.get("sroffset")?.let { (it as? Double)?.toInt()?.toString() }
        return PaginaCataleg(entrades, mes)
    }

    /** Llegeix un catàleg OPDS (Atom). */
    suspend fun opds(url: String): PaginaCataleg = llegeixOpds(Plataforma.descarrega(url).decodeToString(), url)

    fun llegeixOpds(xml: String, base: String): PaginaCataleg {
        fun absolut(h: String) = resol(base, Text.entitats(h))
        val capcalera = xml.substringBefore("<entry")
        val seguent = enllacos(capcalera).firstOrNull { it.rel == "next" }?.href?.let(::absolut)
        val titolFeed = Text.etiqueta(capcalera, "title")
        val entrades = Regex("<entry\\b[\\s\\S]*?</entry>").findAll(xml).mapNotNull { m ->
            val e = m.value
            var titol = Text.etiqueta(e, "title") ?: return@mapNotNull null
            val autor = Regex("<author>[\\s\\S]*?</author>").find(e)?.value?.let { Text.etiqueta(it, "name") }
            val resum = (Text.etiqueta(e, "summary") ?: Text.etiqueta(e, "content"))?.take(300)
            val ll = enllacos(e)
            val portada = ll.firstOrNull { it.rel == "http://opds-spec.org/image" && !it.href.startsWith("data:") }?.href
                ?: ll.firstOrNull { it.rel == "http://opds-spec.org/image/thumbnail" && !it.href.startsWith("data:") }?.href
            val adquisicions = ll.filter { it.rel.startsWith("http://opds-spec.org/acquisition") }
            val millor = PREFERITS.firstNotNullOfOrNull { (tipus, ext) -> adquisicions.firstOrNull { it.type.startsWith(tipus) }?.let { it to ext } }
            val navega = ll.firstOrNull { it.type.contains("opds-catalog") || it.rel == "subsection" }?.href
            var baixada = millor?.first?.href?.let(::absolut)
            var ext = millor?.second ?: "epub"
            var imatge = portada?.let(::absolut)
            // Gutenberg: la cerca no porta l'enllaç del fitxer, però les adreces són fixes
            val gid = Regex("gutenberg\\.org/ebooks/(\\d+)").find(Text.etiqueta(e, "id").orEmpty())?.groupValues?.get(1)
            var idioma: String? = null
            if (gid != null) {
                baixada = "https://www.gutenberg.org/ebooks/$gid.epub3.images"; ext = "epub"
                imatge = "https://www.gutenberg.org/cache/epub/$gid/pg$gid.cover.medium.jpg"
                Regex("\\s*\\(([A-Za-z ]+)\\)$").find(titol)?.let { idioma = it.groupValues[1]; titol = titol.removeRange(it.range) }
                titol = titol.replace(Regex("\\s+:\\s+"), ": ")
            }
            EntradaCataleg(
                titol, if (gid != null) resum else autor, if (gid != null) null else resum, imatge, baixada, ext,
                if (baixada == null) navega?.let(::absolut) else null, idioma,
            )
        }.filter { it.baixada != null || it.navega != null }.toList()
        return PaginaCataleg(entrades, seguent, titolFeed)
    }

    private val PREFERITS = listOf(
        "application/epub+zip" to "epub", "application/x-fictionbook" to "fb2", "application/fb2" to "fb2",
        "application/x-mobipocket-ebook" to "mobi", "application/pdf" to "pdf", "application/vnd.comicbook+zip" to "cbz",
        "text/plain" to "txt",
    )

    private data class Enllac(val rel: String, val type: String, val href: String)

    private fun enllacos(xml: String) = Regex("<link\\b[^>]*>").findAll(xml).mapNotNull { m ->
        val t = m.value
        Enllac(Text.atribut(t, "rel").orEmpty(), Text.atribut(t, "type").orEmpty(), Text.atribut(t, "href") ?: return@mapNotNull null)
    }.toList()

    /** Resol una adreça relativa respecte d'una de base. */
    fun resol(base: String, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> base.substringBefore("//") + href
        href.startsWith("/") -> Regex("^(https?://[^/]+)").find(base)?.value.orEmpty() + href
        else -> base.substringBeforeLast('/') + "/" + href
    }

    /** Baixa el llibre a la carpeta de baixades. Torna el camí del fitxer. */
    suspend fun baixa(e: EntradaCataleg): String {
        val url = e.baixada ?: error("Aquesta entrada no es pot baixar")
        val bytes = Plataforma.descarrega(url)
        if (bytes.size < 200) error("El fitxer baixat és buit")
        if (e.extensio == "epub" && !(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()))
            error("El servidor no ha tornat un EPUB (potser està ocupat; torna-ho a provar)")
        val nom = (listOfNotNull(e.titol, e.autor?.takeIf { it.length < 60 }).joinToString(" - "))
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
        var cami = "${Plataforma.carpetaBaixades()}/$nom.${e.extensio}"
        var n = 2
        while (Plataforma.existeix(cami)) cami = "${Plataforma.carpetaBaixades()}/$nom ($n).${e.extensio}".also { n++ }
        Plataforma.escriu(cami, bytes)
        return cami
    }

    // ---- Catàlegs OPDS de l'usuari ----
    fun meus(): List<FontLlibres.Opds> = Plataforma.llegeixDada("cataleg.tsv")?.lines()?.mapNotNull { l ->
        val p = l.split('\t'); if (p.size == 2 && p[1].startsWith("http")) FontLlibres.Opds(p[0], p[1]) else null
    }.orEmpty()

    fun desaMeus(l: List<FontLlibres.Opds>) = Plataforma.desaDada("cataleg.tsv", l.joinToString("\n") { "${it.nom}\t${it.url}" })
}
