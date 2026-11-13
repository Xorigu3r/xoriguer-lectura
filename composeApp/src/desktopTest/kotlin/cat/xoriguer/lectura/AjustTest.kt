package cat.xoriguer.lectura

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import cat.xoriguer.lectura.pantalles.Ajustos
import cat.xoriguer.lectura.pantalles.ajustAutomatic
import cat.xoriguer.lectura.pantalles.paraulaA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AjustTest {
    @Test fun paraulaOnEsPrem() {
        val t = "Escrivia cada nit; l'estufa i el col·legi."
        assertEquals(TextRange(9, 13), paraulaA(t, 10))            // «cada»
        assertEquals("estufa", paraulaA(t, 22)!!.let { t.substring(it.start, it.end) }) // l'apòstrof separa
        assertEquals("col·legi", paraulaA(t, 35)!!.let { t.substring(it.start, it.end) }) // el punt volat no
        assertEquals("nit", paraulaA(t, 17)!!.let { t.substring(it.start, it.end) })      // just després de la paraula
        assertNull(paraulaA(t, null))
    }

    @Test fun ajustSegonsPantalla() {
        val a = Ajustos()
        val mobil = ajustAutomatic(a, 360.dp, 1f, estret = true)
        val mobilLletraGran = ajustAutomatic(a, 360.dp, 1.3f, estret = true)
        val ordinador = ajustAutomatic(a, 700.dp, 1f, estret = false)
        assertEquals(16, mobil.marge)
        assertTrue(mobil.mida in 15f..19f, "mòbil: ${mobil.mida}")
        assertTrue(mobilLletraGran.mida <= mobil.mida, "amb la lletra del sistema gran, menys sp")
        assertTrue(ordinador.marge > mobil.marge)
        // al mòbil hi caben unes 36 lletres per línia
        val lletres = (360 - 2 * mobil.marge) / (0.52f * mobil.mida)
        assertTrue(lletres in 32f..46f, "lletres per línia: $lletres")
    }
}
