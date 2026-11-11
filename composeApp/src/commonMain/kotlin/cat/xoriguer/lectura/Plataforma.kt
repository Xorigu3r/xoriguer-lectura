package cat.xoriguer.lectura

import androidx.compose.ui.graphics.ImageBitmap

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
}
