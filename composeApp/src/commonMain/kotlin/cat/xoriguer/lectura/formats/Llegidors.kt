package cat.xoriguer.lectura.formats

import cat.xoriguer.lectura.ArxiuZip
import cat.xoriguer.lectura.DocumentPdf
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.Bloc
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut
import cat.xoriguer.lectura.dades.Format
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Metadades llegides del fitxer (títol, autor, portada). */
data class Metadades(val titol: String?, val autor: String?, val serie: String? = null, val portada: ByteArray? = null)

object Llegidors {

    fun metadades(cami: String, format: Format): Metadades? = runCatching { metadadesOError(cami, format) }.getOrNull()

    private fun <T> ambZip(cami: String, f: (ArxiuZip) -> T): T? = Plataforma.obreZip(cami)?.let { z -> try { f(z) } finally { z.tanca() } }

    fun metadadesOError(cami: String, format: Format): Metadades? =
        when (format) {
            Format.EPUB -> ambZip(cami) { Epub(it).metadades() }
            Format.FB2 -> Fb2.metadades(Text.decodifica(Plataforma.llegeix(cami)))
            Format.MOBI -> Mobi(Plataforma.llegeix(cami)).metadades()
            Format.DOCX -> ambZip(cami, Oficina::docxMetadades)
            Format.ODT -> ambZip(cami, Oficina::odtMetadades)
            Format.CBZ -> ambZip(cami) { Metadades(null, null, null, Comic(it).portada()) }
            else -> null
        }

    fun contingut(cami: String, format: Format): Contingut = when (format) {
        Format.EPUB -> ambZip(cami) { Epub(it).contingut() } ?: error("No s'ha pogut obrir l'EPUB")
        Format.FB2 -> Fb2.contingut(Text.decodifica(Plataforma.llegeix(cami)))
        Format.TXT -> Text.deTextPla(Text.decodifica(Plataforma.llegeix(cami)))
        Format.MOBI -> Mobi(Plataforma.llegeix(cami)).contingut()
        Format.DOCX -> ambZip(cami, Oficina::docx) ?: error("No s'ha pogut obrir el document")
        Format.ODT -> ambZip(cami, Oficina::odt) ?: error("No s'ha pogut obrir el document")
        Format.RTF -> Oficina.rtf(Text.decodifica(Plataforma.llegeix(cami)))
        else -> error(missatgeNoSuportat(format))
    }

    fun missatgeNoSuportat(format: Format): String = when (format) {
        Format.CBR -> "Els còmics CBR (comprimits amb RAR) encara no es poden obrir. Si el converteixes a CBZ, sí."
        Format.DJVU -> "Els fitxers DJVU encara no es poden obrir."
        Format.DOC -> "Els documents .doc antics encara no es poden obrir. Desa'l com a .docx des del Word o el LibreOffice."
        Format.CHM -> "Els fitxers d'ajuda CHM encara no es poden obrir."
        else -> "Format encara no suportat: ${format.etiqueta}"
    }

    /** Documents que es mostren pàgina a pàgina (PDF i còmics). */
    fun paginat(cami: String, format: Format): DocumentPdf? = when (format) {
        Format.PDF -> Plataforma.obrePdf(cami)
        Format.CBZ -> Plataforma.obreZip(cami)?.let { Comic(it) }?.takeIf { it.pagines > 0 }
        else -> null
    }
}

private class Epub(val zip: ArxiuZip) {
    private val opfCami: String
    private val opf: String
    private val base: String

    init {
        val container = zip.llegeix("META-INF/container.xml")?.decodeToString()
        opfCami = container?.let { Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            ?: zip.noms.first { it.endsWith(".opf", ignoreCase = true) }
        opf = zip.llegeix(opfCami)?.decodeToString() ?: error("OPF no trobat")
        base = opfCami.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    }

    private data class Item(val id: String, val href: String, val tipus: String, val props: String)

    private val items: List<Item> by lazy {
        Regex("<(?:opf:)?item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).mapNotNull { m ->
            val t = m.value
            val id = Text.atribut(t, "id") ?: return@mapNotNull null
            val href = Text.atribut(t, "href") ?: return@mapNotNull null
            Item(id, resol(base, Text.entitats(href)), Text.atribut(t, "media-type") ?: "", Text.atribut(t, "properties") ?: "")
        }.toList()
    }

    fun metadades(): Metadades {
        val meta = opf.substringBefore("</metadata>", opf)
        val serie = Regex("<meta\\b[^>]*name=\"calibre:series\"[^>]*>", RegexOption.IGNORE_CASE).find(meta)?.value?.let { Text.atribut(it, "content") }
        return Metadades(Text.etiqueta(meta, "title"), Text.etiqueta(meta, "creator"), serie, portada())
    }

    private fun portada(): ByteArray? {
        val perProp = items.firstOrNull { "cover-image" in it.props }
        val idMeta = Regex("<meta\\b[^>]*name=\"cover\"[^>]*>", RegexOption.IGNORE_CASE).find(opf)?.value?.let { Text.atribut(it, "content") }
        val item = perProp ?: items.firstOrNull { it.id == idMeta }
            ?: items.firstOrNull { it.tipus.startsWith("image/") && it.href.contains("cover", ignoreCase = true) }
        return item?.let { zip.llegeix(it.href) }
    }

    fun contingut(): Contingut {
        val perId = items.associateBy { it.id }
        val ordre = Regex("<(?:opf:)?itemref\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
            .mapNotNull { Text.atribut(it.value, "idref")?.let(perId::get) }.toList()
            .ifEmpty { items.filter { "html" in it.tipus } }
        val blocs = mutableListOf<Bloc>(); val caps = mutableListOf<Capitol>()
        for (it in ordre) {
            val html = zip.llegeix(it.href)?.let(Text::decodifica) ?: continue
            Text.deHtml(html, blocs, caps)
        }
        return Contingut(blocs, caps)
    }

    companion object {
        fun resol(base: String, href: String): String {
            val parts = (base + href.substringBefore('#')).split('/').fold(mutableListOf<String>()) { acc, p ->
                when (p) {
                    ".." -> if (acc.isNotEmpty()) acc.removeAt(acc.size - 1)
                    ".", "" -> {}
                    else -> acc += p
                }
                acc
            }
            return parts.joinToString("/").replace("%20", " ")
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private object Fb2 {
    fun metadades(xml: String): Metadades {
        val info = xml.substringAfter("<title-info>", "").substringBefore("</title-info>")
        val autor = info.substringAfter("<author>", "").substringBefore("</author>").let { a ->
            listOfNotNull(Text.etiqueta(a, "first-name"), Text.etiqueta(a, "last-name")).joinToString(" ").ifBlank { null }
        }
        val serie = Regex("<sequence\\b[^>]*>").find(info)?.value?.let { Text.atribut(it, "name") }
        val portadaId = Regex("<coverpage>[\\s\\S]*?href=\"#([^\"]+)\"").find(info)?.groupValues?.get(1)
        val portada = portadaId?.let { id ->
            Regex("<binary\\b[^>]*id=\"${Regex.escape(id)}\"[^>]*>([\\s\\S]*?)</binary>").find(xml)?.groupValues?.get(1)
        }?.let { runCatching { Base64.decode(it.filterNot(Char::isWhitespace)) }.getOrNull() }
        return Metadades(Text.etiqueta(info, "book-title"), autor, serie, portada)
    }

    fun contingut(xml: String): Contingut {
        val blocs = mutableListOf<Bloc>(); val caps = mutableListOf<Capitol>()
        Regex("<body\\b[^>]*>([\\s\\S]*?)</body>").findAll(xml).forEach { Text.deHtml("<body>" + it.groupValues[1], blocs, caps) }
        return Contingut(blocs, caps)
    }
}
