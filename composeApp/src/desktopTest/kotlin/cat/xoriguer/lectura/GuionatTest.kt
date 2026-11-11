package cat.xoriguer.lectura

import cat.xoriguer.lectura.formats.Guionat
import kotlin.test.Test
import kotlin.test.assertEquals

class GuionatTest {
    private fun parteix(p: String, idioma: String = "ca"): String {
        val talls = Guionat.sillabes(p, idioma).toSet()
        return buildString { p.forEachIndexed { i, c -> if (i in talls) append('-'); append(c) } }
    }

    @Test fun catala() {
        assertEquals("im-pres-sio-nant", parteix("impressionant"))
        assertEquals("mun-ta-nya", parteix("muntanya"))
        assertEquals("car-rer", parteix("carrer"))
        assertEquals("cons-truc-ció", parteix("construcció"))
        assertEquals("queixa", parteix("queixa"))        // «quei-xa» deixaria només 2 lletres a la línia següent
        assertEquals("lli-bre", parteix("llibre"))
        assertEquals("ins-tant", parteix("instant"))
        assertEquals("pet-ja-des", parteix("petjades"))    // tj, tg, tx es parteixen: met-ge, cot-xe
        assertEquals("col·legi", parteix("col·legi"))     // la ela geminada no es parteix
        assertEquals("boira", parteix("boira"))           // massa curta
        assertEquals("xo-ri-guer", parteix("xoriguer"))   // gu davant de e: una sola consonant
        assertEquals("ar-queta", parteix("arqueta"))       // «que-ta» deixaria 2 lletres soles
    }

    @Test fun castella() {
        assertEquals("pe-rro", parteix("perro", "es"))
        assertEquals("ca-ba-llo", parteix("caballo", "es"))
        assertEquals("mu-cha-cho", parteix("muchacho", "es"))
        assertEquals("cons-truc-ción", parteix("construcción", "es"))
    }

    @Test fun correspondencia() {
        val text = "L'impressionant vol d'un xoriguer."
        val g = Guionat.de(text, "ca")
        assertEquals(text, g.text.replace(Guionat.GUIO.toString(), ""))
        // la «v» de «vol» és al mateix lloc en tots dos textos després de convertir
        val v = text.indexOf("vol")
        assertEquals('v', g.text[g.mostrat(v)])
        assertEquals(v, g.original(g.mostrat(v)))
        // un guió suau correspon a la lletra que el segueix
        val guio = g.text.indexOf(Guionat.GUIO)
        assertEquals(g.text[guio + 1], text[g.original(guio)])
    }
}
