package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Anotacio
import cat.xoriguer.lectura.dades.Anotacions
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Calendari
import cat.xoriguer.lectura.dades.ColorCita
import cat.xoriguer.lectura.dades.Empremta
import cat.xoriguer.lectura.dades.Estadistiques
import cat.xoriguer.lectura.dades.TipusAnotacio
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 0.7.0: llibres identificats pel contingut, cites de més d'un paràgraf i estadístiques. */
class SetTest {
    @Test fun calendari() {
        assertEquals(Triple(1970, 1, 1), Calendari.data(0))
        assertEquals("dj", Calendari.diaSetmana(0))
        val d = java.time.LocalDate.of(2026, 11, 13).toEpochDay().toInt()
        assertEquals(Triple(2026, 11, 13), Calendari.data(d))
        assertEquals("dv", Calendari.diaSetmana(d))
        assertEquals("13 de novembre de 2026", Calendari.llarga(d))
        assertEquals("1 d'abril de 2024", Calendari.llarga(java.time.LocalDate.of(2024, 4, 1).toEpochDay().toInt()))
        assertEquals(Triple(2024, 2, 29), Calendari.data(java.time.LocalDate.of(2024, 2, 29).toEpochDay().toInt()))
    }

    @Test fun estadistiques() {
        val e = Estadistiques(null)
        val avui = e.avui()
        e.afegeixTemps("a.epub", 90); e.afegeixTemps("b.epub", 30); e.afegeixPagina(); e.afegeixPagina()
        assertEquals(2, e.minutsAvui())
        assertEquals(2, e.dies[avui]!!.pagines)
        assertEquals(1, e.ratxa())
        e.dies[avui - 1] = cat.xoriguer.lectura.dades.DiaLectura(600, 3)
        e.dies[avui - 3] = cat.xoriguer.lectura.dades.DiaLectura(600, 3) // salt d'un dia: talla la ratxa
        assertEquals(2, e.ratxa())
        assertEquals(7, e.ultims(7).size); assertEquals(avui, e.ultims(7).last().first)
        e.canviaCami("a.epub", "c.epub")
        assertEquals(90L, e.perLlibre["c.epub"]); assertNull(e.perLlibre["a.epub"])
        e.canviaObjectiu(1000); assertEquals(240, e.objectiuMinuts)
    }

    @Test fun citaDeDosParagrafs() {
        val a = Anotacio("x", "l.epub", "Llibre", null, TipusAnotacio.CITA, 3, 10, 20, "tros", "", ColorCita.GROC, 1, .5f, blocFi = 5)
        assertEquals(10 until 40, a.trosA(3, 40))
        assertEquals(0 until 30, a.trosA(4, 30))
        assertEquals(0 until 20, a.trosA(5, 50))
        assertNull(a.trosA(2, 50)); assertNull(a.trosA(6, 50))
        val b = assertNotNull(Anotacions.deLinia(Anotacions.aLinia(a)))
        assertEquals(5, b.blocFi); assertEquals(a, b)
        // les línies velles (13 camps) són d'un sol paràgraf
        val vella = Anotacions.aLinia(a.copy(blocFi = 3)).substringBeforeLast('\t')
        assertEquals(3, Anotacions.deLinia(vella)!!.blocFi)
    }

    @Test fun empremta() {
        val dir = File(System.getProperty("java.io.tmpdir"), "lectura-empremta").apply { deleteRecursively(); mkdirs() }
        val a = File(dir, "a.txt").apply { writeText("Hola, món! ".repeat(20_000)) }
        val b = File(dir, "b.txt").apply { writeBytes(a.readBytes()) }
        val c = File(dir, "c.txt").apply { writeText("Hola, món! ".repeat(19_999) + "Adéu, món!") }
        val ea = assertNotNull(Empremta.de(a.path, a.length()))
        assertEquals(ea, Empremta.de(b.path, b.length()))
        assertNotEquals(ea, Empremta.de(c.path, c.length()))
        dir.deleteRecursively()
    }

    /** Un llibre mogut i reanomenat fora de l'app conserva el progrés, les cites i les col·leccions. */
    @Test fun llibreMogut() {
        val dades = System.getenv("APPDATA")?.let(::File) ?: return
        if (!dades.path.contains("scratchpad")) return // només amb una carpeta de dades de prova
        val dir = File(dades, "XoriguerLectura").apply { deleteRecursively(); mkdirs() }
        val llibres = File(dades, "llibres-mogut").apply { deleteRecursively(); mkdirs() }
        val vell = File(llibres, "vell.txt").apply { writeText("Primer paràgraf.\n\nSegon paràgraf.\n\n".repeat(500)) }
        File(dir, "carpetes.txt").writeText(llibres.path)
        val bib = Biblioteca()
        runBlocking { bib.escaneja() }
        val l = bib.llibres.first { it.nomFitxer == "vell.txt" }
        assertNotNull(l.id)
        bib.desaProgres(l, 7, 0, .3f)
        bib.anotacions.nova(l, TipusAnotacio.CITA, 1, 0, 5, "Segon")
        // fora de l'app: es mou a una subcarpeta i es reanomena
        val nou = File(File(llibres, "altra").apply { mkdirs() }, "Nou nom.txt")
        assertTrue(vell.renameTo(nou))
        val bib2 = Biblioteca()
        runBlocking { bib2.escaneja() }
        val l2 = bib2.llibres.first { it.nomFitxer == "Nou nom.txt" }
        assertEquals(7, bib2.progres[l2.cami]?.posicio)
        assertEquals("Segon", bib2.anotacions.deLlibre(l2.cami).single().text)
        assertNull(bib2.progres[l.cami])
        llibres.deleteRecursively(); dir.deleteRecursively()
    }
}
