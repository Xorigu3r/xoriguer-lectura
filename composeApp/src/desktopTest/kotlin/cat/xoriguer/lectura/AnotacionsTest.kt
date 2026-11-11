package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Anotacio
import cat.xoriguer.lectura.dades.Anotacions
import cat.xoriguer.lectura.dades.ColorCita
import cat.xoriguer.lectura.dades.TipusAnotacio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnotacionsTest {
    private val a = Anotacio(
        "1-x", "C:\\Llibres\\el refugi.epub", "El refugi", null, TipusAnotacio.CITA, 7, 3, 20,
        "text amb\ttabulador i \\ barra", "nota\nde dues línies", ColorCita.VERD, 1_794_417_939_301, .25f,
    )

    @Test fun anadaITornada() {
        val l = Anotacions.aLinia(a)
        assertEquals(1, l.lines().size)
        assertEquals(a, Anotacions.deLinia(l))
    }

    @Test fun liniaDolentaNoPeta() {
        assertNull(Anotacions.deLinia("res\tde\tbo"))
        assertNull(Anotacions.deLinia(""))
    }

    @Test fun data() = assertEquals("11/11/2026", a.dataCurta())

    @Test fun text() {
        val t = Anotacions.comText(listOf(a, a.copy(id = "2", tipus = TipusAnotacio.MARCADOR, text = "on era", nota = "")))
        assertTrue(t.startsWith("# El refugi"))
        assertTrue("> text amb" in t && "✎ nota" in t && "🔖 on era" in t)
    }
}
