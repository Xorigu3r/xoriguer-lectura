package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Anotacions
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Diccionari
import cat.xoriguer.lectura.dades.Idioma
import cat.xoriguer.lectura.dades.Sincronitzacio
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiccionariSincroTest {
    @Test fun netejaDefinicio() {
        val ext = """
            == Català ==
            Pronúncia(i):
            Etimologia:  De bòria per metàtesi.

            === Nom ===
            boira f.
            Fenomen atmosfèric de núvols baixos.

            ==== Derivats ====
            boirina, boirim

            === Miscel·lània ===
            Síl·labes: boi·ra (2)

            == Aragonès ==
            === Nom ===
            boira f. Una altra cosa.
        """.trimIndent()
        val d = assertNotNull(Diccionari.neteja(ext, "ca"))
        assertEquals("■ Nom\nboira f.\nFenomen atmosfèric de núvols baixos.", d)
        assertFalse("boirina" in d); assertFalse("Aragonès" in d)
    }

    @Test fun idioma() {
        assertEquals("ca", Idioma.detecta("La boira baixava pel coll i la Mariona va obrir la porta del refugi amb molt de compte."))
        assertEquals("es", Idioma.detecta("La niebla bajaba por el cuello y María abrió la puerta del refugio con mucho cuidado."))
        assertEquals("en", Idioma.detecta("The fog was coming down the hill and she opened the door of the hut with care."))
    }

    /** Dos "dispositius" que fan servir la mateixa carpeta: el segon rep el progrés i les cites del primer. */
    @Test fun sincronitza() {
        val dades = System.getenv("APPDATA")?.let(::File) ?: return
        if (!dades.path.contains("scratchpad")) return // només amb una carpeta de dades de prova
        val dir = File(dades, "XoriguerLectura").apply { deleteRecursively(); mkdirs() }
        val nuvol = File(dades, "nuvol").apply { deleteRecursively(); mkdirs() }
        // dispositiu A (camí de Windows)
        File(dir, "progres.tsv").writeText("C:\\A\\llibre.epub\t12\t0\t0.4\t2000")
        File(dir, "anotacions.tsv").writeText(Anotacions.aLinia(
            cat.xoriguer.lectura.dades.Anotacio("a1", "C:\\A\\llibre.epub", "Llibre", null, cat.xoriguer.lectura.dades.TipusAnotacio.CITA,
                3, 0, 5, "Hola!", "nota", cat.xoriguer.lectura.dades.ColorCita.VERD, 1000, .1f)))
        Sincronitzacio.carpeta = nuvol.path
        val a = Biblioteca()
        Sincronitzacio.sincronitza(a)
        assertTrue(File(nuvol, "Lectura Xoriguer - dades/progres.tsv").readText().contains("\t12\t"))
        // dispositiu B: no té res; en sincronitzar rep les dades d'A
        File(dir, "progres.tsv").writeText("/sdcard/Llibres/altre.epub\t1\t0\t0.1\t500")
        File(dir, "anotacions.tsv").writeText("")
        val b = Biblioteca()
        val r = Sincronitzacio.sincronitza(b)
        assertTrue(r.startsWith("S'han afegit"), r)
        assertEquals(2, b.progres.size)
        assertEquals("Hola!", b.anotacions.totes.single().text)
        Sincronitzacio.carpeta = null
        dir.deleteRecursively(); nuvol.deleteRecursively()
    }
}

class DiccionariBaseTest {
    private val malastruga = "\n== Català ==\nPronúncia(i): oriental /mə.ɫəsˈtɾu.ɣə/\nRimes: -uɡa\n\n\n=== Adjectiu ===\nmal·as·tru·ga f. ‎(plural malastrugues)\n\nForma femenina de malastruc.\n\n\n=== Miscel·lània ===\nSíl·labes: mal·as·tru·ga (4)"

    @Test fun treuSoroll() {
        val d = kotlin.test.assertNotNull(Diccionari.neteja(malastruga, "ca"))
        assertEquals("■ Adjectiu\nmal·as·tru·ga f. (plural malastrugues)\nForma femenina de malastruc.", d)
    }

    @Test fun paraulaBase() {
        assertEquals("malastruc", Diccionari.paraulaBase("■ Adjectiu\nForma femenina de malastruc."))
        assertEquals("cantar", Diccionari.paraulaBase("Tercera persona del singular (ell, ella, vostè) del present d'indicatiu de cantar."))
        assertEquals("niebla", Diccionari.paraulaBase("Forma del plural de niebla."))
        assertEquals(null, Diccionari.paraulaBase("Fenomen atmosfèric de núvols baixos."))
    }

    /** Amb el Viquidiccionari de veritat (XARXA=1): «malastruga» ha de portar també el significat de «malastruc». */
    @Test fun seguixLaBase() = kotlinx.coroutines.runBlocking {
        if (System.getenv("XARXA") != "1") return@runBlocking
        val d = kotlin.test.assertNotNull(Diccionari.busca("malastruga", "ca"))
        println("DICCIONARI malastruga:\n$d")
        assertTrue("Que té desgràcia" in d, d)
        assertTrue("Rimes" !in d, d)
    }
}
