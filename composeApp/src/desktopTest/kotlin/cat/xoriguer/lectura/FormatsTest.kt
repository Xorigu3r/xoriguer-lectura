package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Format
import cat.xoriguer.lectura.formats.Llegidors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Prova amb els llibres de mostra de la carpeta indicada a LLIBRES_PROVA. */
class FormatsTest {
    private val dir = System.getenv("LLIBRES_PROVA")

    @Test fun epub() {
        if (dir == null) return
        val cami = "$dir/el_refugi.epub"
        val m = assertNotNull(Llegidors.metadadesOError(cami, Format.EPUB))
        assertEquals("El refugi de la boira", m.titol)
        assertEquals("Anna Serra", m.autor)
        assertNotNull(m.portada)
        val c = Llegidors.contingut(cami, Format.EPUB)
        assertEquals(5, c.capitols.size)
        assertTrue(c.blocs.size > 50)
    }

    @Test fun fb2() {
        if (dir == null) return
        val m = assertNotNull(Llegidors.metadadesOError("$dir/contes_montseny.fb2", Format.FB2))
        assertEquals("Contes del Montseny", m.titol)
        assertEquals("Pere Vidal", m.autor)
        assertEquals(3, Llegidors.contingut("$dir/contes_montseny.fb2", Format.FB2).capitols.size)
    }

    @Test fun txt() {
        if (dir == null) return
        val c = Llegidors.contingut("$dir/Notes de viatge.txt", Format.TXT)
        assertEquals("Capítol 1", c.blocs.first().text)
        assertEquals(2, c.capitols.size)
    }
}
