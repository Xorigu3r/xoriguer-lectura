package cat.xoriguer.lectura

import cat.xoriguer.lectura.dades.Actualitzacions
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActualitzacionsTest {
    @Test fun comparaVersions() {
        assertTrue(Actualitzacions.esMesNova("0.4.0", "0.3.0"))
        assertTrue(Actualitzacions.esMesNova("v0.10.0", "0.9.9"))
        assertTrue(Actualitzacions.esMesNova("1.0", "0.9.9"))
        assertFalse(Actualitzacions.esMesNova("0.3.0", "0.3.0"))
        assertFalse(Actualitzacions.esMesNova("0.2.9", "0.3.0"))
        assertFalse(Actualitzacions.esMesNova("v0.3", "0.3.0"))
    }

    @Test fun triaFitxerPerPlataforma() {
        val json = """{"tag_name":"v0.5.0","html_url":"https://github.com/x/y/releases/tag/v0.5.0","body":"**Novetats**\n- Una cosa `nova`",
            "assets":[{"browser_download_url":"https://e/LecturaXoriguer.apk"},{"browser_download_url":"https://e/LecturaXoriguer-Windows.zip"}]}"""
        assertEquals("https://e/LecturaXoriguer.apk", Actualitzacions.deRelease(json, "Android")!!.baixada)
        val w = assertNotNull(Actualitzacions.deRelease(json, "Windows"))
        assertEquals("https://e/LecturaXoriguer-Windows.zip", w.baixada)
        assertEquals("0.5.0", w.versio)
        assertEquals("Novetats\n- Una cosa nova", w.notes)
        assertEquals("https://github.com/x/y/releases/tag/v0.5.0", Actualitzacions.deRelease(json, "iOS")!!.baixada)
    }

    /** Amb el servidor de veritat (XARXA=1): la darrera Release publicada s'ha de poder llegir. */
    @Test fun releaseDeVeritat() = runBlocking {
        if (System.getenv("XARXA") != "1") return@runBlocking
        val json = Plataforma.descarrega("https://api.github.com/repos/${Actualitzacions.REPO}/releases/latest").decodeToString()
        val r = assertNotNull(Actualitzacions.deRelease(json, "Android"))
        println("Darrera Release: ${r.versio} -> ${r.baixada}")
        assertTrue(r.baixada.endsWith("LecturaXoriguer.apk"))
        // la instal·lada (VERSIO_APP) és la que s'està preparant, més nova que la publicada
        assertFalse(Actualitzacions.esMesNova(r.versio, VERSIO_APP))
    }
}
