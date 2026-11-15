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
            "assets":[{"browser_download_url":"https://e/LecturaXoriguer.apk"},{"browser_download_url":"https://e/LecturaXoriguer-Windows.zip"},{"browser_download_url":"https://e/LecturaXoriguer-Instalador.msi"}]}"""
        assertEquals("https://e/LecturaXoriguer.apk", Actualitzacions.deRelease(json, "Android")!!.baixada)
        val w = assertNotNull(Actualitzacions.deRelease(json, "Windows"))
        assertEquals("https://e/LecturaXoriguer-Instalador.msi", w.baixada) // l'instal·lador abans que el ZIP
        assertEquals("0.5.0", w.versio)
        assertEquals("Novetats\n- Una cosa nova", w.notes)
        assertEquals("https://github.com/x/y/releases/tag/v0.5.0", Actualitzacions.deRelease(json, "iOS")!!.baixada)
    }

    @Test fun versioJsonDeXoriguerDev() {
        val json = """{"versio": "0.7.1", "url": "https://baixades.xoriguer.dev/LecturaXoriguer-Instalador.msi", "sha256": "AB",
            "apk": "https://xoriguer.dev/baixades/LecturaXoriguer.apk", "apk_sha256": "CD"}"""
        val a = assertNotNull(Actualitzacions.deVersioJson(json, "Android"))
        assertEquals("0.7.1", a.versio)
        assertEquals("https://xoriguer.dev/baixades/LecturaXoriguer.apk", a.baixada)
        assertEquals("https://baixades.xoriguer.dev/LecturaXoriguer-Instalador.msi", Actualitzacions.deVersioJson(json, "Windows")!!.baixada)
        assertEquals(Actualitzacions.PAGINA, Actualitzacions.deVersioJson(json, "iOS")!!.baixada)
    }

    /** Amb el servidor de veritat (XARXA=1): el versio.json de xoriguer.dev s'ha de poder llegir. */
    @Test fun versioJsonDeVeritat() = runBlocking {
        if (System.getenv("XARXA") != "1") return@runBlocking
        val r = assertNotNull(Actualitzacions.deVersioJson(Plataforma.descarrega(Actualitzacions.URL_VERSIO).decodeToString(), "Android"))
        println("Publicada: ${r.versio} -> ${r.baixada}")
        assertTrue(r.baixada.endsWith("LecturaXoriguer.apk"))
    }

}
