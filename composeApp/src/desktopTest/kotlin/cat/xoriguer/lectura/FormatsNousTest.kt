package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Format
import cat.xoriguer.lectura.dades.TipusBloc
import cat.xoriguer.lectura.formats.Comic
import cat.xoriguer.lectura.formats.Llegidors
import cat.xoriguer.lectura.formats.Mobi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Formats nous, amb els fitxers de mostra de LLIBRES_PROVA (fets amb fes_formats.py). */
class FormatsNousTest {
    private val dir = System.getenv("LLIBRES_PROVA")
    private val falco = "El xoriguer volava baix sobre el camp de blat."

    @Test fun docx() {
        if (dir == null) return
        val m = assertNotNull(Llegidors.metadadesOError("$dir/prova.docx", Format.DOCX))
        assertEquals("Document de prova", m.titol); assertEquals("Joana Puig", m.autor)
        val c = Llegidors.contingut("$dir/prova.docx", Format.DOCX)
        assertEquals(TipusBloc.TITOL, c.blocs[0].tipus)
        assertEquals(falco, c.blocs[1].text)
        assertEquals(1, c.capitols.size)
    }

    @Test fun odt() {
        if (dir == null) return
        val m = assertNotNull(Llegidors.metadadesOError("$dir/prova.odt", Format.ODT))
        assertEquals("Pau Ribas", m.autor)
        val c = Llegidors.contingut("$dir/prova.odt", Format.ODT)
        assertEquals("Introducció", c.blocs[0].text)
        assertEquals(falco, c.blocs[1].text)
    }

    @Test fun rtf() {
        if (dir == null) return
        val c = Llegidors.contingut("$dir/prova.rtf", Format.RTF)
        assertEquals(falco, c.blocs[0].text)
        assertTrue(c.blocs[2].text.startsWith("«Torna demà»"), c.blocs[2].text)
        assertEquals("Euro: € fi.", c.blocs.last().text)
    }

    @Test fun mobi() {
        if (dir == null) return
        val m = assertNotNull(Llegidors.metadadesOError("$dir/prova.mobi", Format.MOBI))
        assertEquals("El falcó i el camp", m.titol); assertEquals("Marta Vilà", m.autor)
        assertNotNull(m.portada)
        val c = Llegidors.contingut("$dir/prova.mobi", Format.MOBI)
        assertEquals(listOf("Capítol u", "Capítol dos"), c.capitols.map { it.titol })
        assertEquals(falco, c.blocs[1].text)
        assertEquals("Final del llibre, final del llibre.", c.blocs.last().text)
    }

    @Test fun palmdoc() {
        // "abcabcabc": literal "abc" + referència (distància 3, llargada 6)
        val v = 0x8000 or (3 shl 3) or (6 - 3)
        val x = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), (v shr 8).toByte(), v.toByte())
        assertEquals("abcabcabc", Mobi.palmdoc(x).decodeToString())
        // 0xC0+: espai + caràcter
        assertEquals(" A", Mobi.palmdoc(byteArrayOf(('A'.code xor 0x80).toByte())).decodeToString())
    }

    @Test fun cbz() {
        if (dir == null) return
        val z = assertNotNull(Plataforma.obreZip("$dir/prova.cbz"))
        val c = Comic(z)
        assertEquals(listOf("pag1.png", "pag2.png", "pag10.png"), c.imatges)
        c.tanca()
    }
}
