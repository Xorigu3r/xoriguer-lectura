package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Cataleg
import cat.xoriguer.lectura.dades.FontLlibres
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves amb els servidors de veritat: només s'executen amb XARXA=1. */
class CatalegTest {
    private val xarxa = System.getenv("XARXA") == "1"

    @Test fun gutenbergCatala() = runBlocking {
        if (!xarxa) return@runBlocking
        val font = FontLlibres.predeterminades.filterIsInstance<FontLlibres.Gutenberg>().first { it.idioma == "ca" }
        val p = Cataleg.cerca(font, "")
        println("Gutenberg ca: ${p.entrades.size} — ${p.entrades.take(3).map { "${it.titol} / ${it.autor}" }} — següent: ${p.seguent != null}")
        assertTrue(p.entrades.size >= 10)
        val e = p.entrades.first()
        assertTrue(e.baixada!!.endsWith(".epub3.images"))
        assertEquals("Catalan", e.idioma)
        val bytes = Plataforma.descarrega(e.baixada!!)
        assertTrue(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte(), "no és un EPUB")
    }

    @Test fun viquitexts() = runBlocking {
        if (!xarxa) return@runBlocking
        val font = FontLlibres.predeterminades.filterIsInstance<FontLlibres.Wikisource>().first { it.idioma == "ca" }
        val p = Cataleg.cerca(font, "Canigó")
        println("Viquitexts: ${p.entrades.size} — ${p.entrades.take(5).map { it.titol }}")
        assertTrue(p.entrades.any { it.titol == "Canigó" })
        assertTrue(p.entrades.none { '/' in it.titol })
    }

    @Test fun opdsRelatiu() {
        val xml = """<feed><title>Prova</title><link rel="next" href="?page=2"/>
            <entry><title>Llibre</title><author><name>Anna</name></author>
            <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/b/1.epub"/></entry>
            <entry><title>Carpeta</title><link rel="subsection" type="application/atom+xml;profile=opds-catalog" href="sub.xml"/></entry></feed>"""
        val p = Cataleg.llegeixOpds(xml, "https://exemple.cat/opds/arrel.xml")
        assertEquals("https://exemple.cat/b/1.epub", p.entrades[0].baixada)
        assertEquals("Anna", p.entrades[0].autor)
        assertEquals("https://exemple.cat/opds/sub.xml", p.entrades[1].navega)
        assertEquals("https://exemple.cat/opds/?page=2", p.seguent)
    }
}
