package cat.xoriguer.lectura

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily

/** Un fitxer trobat en escanejar les carpetes. */
data class FitxerTrobat(val cami: String, val nom: String, val mida: Long, val modificat: Long)

/** Un arxiu ZIP obert (EPUB, CBZ...). */
interface ArxiuZip {
    val noms: List<String>
    fun llegeix(nom: String): ByteArray?
    fun tanca()
}

/** Un PDF obert. Cada plataforma el pinta amb el seu motor. */
interface DocumentPdf {
    val pagines: Int
    suspend fun pagina(index: Int, ample: Int): ImageBitmap?
    fun tanca()
}

/** El que cada plataforma (Android, Windows, iOS) ha de fer per si mateixa. */
expect object Plataforma {
    val nom: String

    /** Carpetes on es busquen llibres si l'usuari no n'ha afegit cap. */
    fun carpetesPerDefecte(): List<String>

    /** Llista recursiva de fitxers (sense carpetes ocultes). */
    fun llistaFitxers(carpeta: String, profunditat: Int = 8): List<FitxerTrobat>

    fun existeix(cami: String): Boolean
    fun llegeix(cami: String): ByteArray
    fun obreZip(cami: String): ArxiuZip?
    fun obrePdf(cami: String): DocumentPdf?

    /** Fitxers propis de l'app (progrés, configuració). */
    fun llegeixDada(nom: String): String?
    fun desaDada(nom: String, text: String)

    /** Permís per llegir tots els fitxers del dispositiu (només cal a Android). */
    fun tePermisFitxers(): Boolean
    fun demanaPermisFitxers()

    /** Diàleg per triar una carpeta; null si l'usuari cancel·la o si no es pot. */
    suspend fun triaCarpeta(): String?

    /** Baixa una adreça d'internet (segueix redireccions). Llança una excepció si falla. */
    suspend fun descarrega(url: String): ByteArray

    /** Carpeta on es desen els llibres baixats (es crea si no hi és; la biblioteca la mira sempre). */
    fun carpetaBaixades(): String

    /** Carpeta on l'usuari posa els seus tipus de lletra (.ttf, .otf). */
    fun carpetaFonts(): String

    fun escriu(cami: String, bytes: ByteArray)
    fun creaCarpeta(cami: String)

    /** Gestió de fitxers. Tornen el camí nou, o null si no s'ha pogut. */
    fun reanomena(cami: String, nouNom: String): String?
    fun mou(cami: String, carpeta: String): String?
    fun esborra(cami: String): Boolean

    /** Tipus de lletra d'un fitxer .ttf/.otf; null si no es pot carregar. */
    fun font(cami: String): FontFamily?

    /** Lectura en veu alta (motor de veu del sistema). */
    val veu: Veu

    /** Obre una adreça al navegador. */
    fun obreWeb(url: String)
}

/** Motor de veu. `parla` diu un tros de text i crida `fet` quan acaba (o `fet(false)` si s'ha aturat o ha fallat). */
interface Veu {
    val disponible: Boolean
    fun parla(text: String, idioma: String?, velocitat: Float, fet: (Boolean) -> Unit)
    fun atura()
    /** Avisa que comença o acaba una sessió de lectura (a Android, per mantenir-la amb la pantalla bloquejada). */
    fun sessio(activa: Boolean, titol: String = "") {}
}
