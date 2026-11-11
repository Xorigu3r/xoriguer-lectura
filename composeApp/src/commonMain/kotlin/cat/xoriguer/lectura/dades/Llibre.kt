package cat.xoriguer.lectura.dades

/** `paginat`: es mostra pàgina a pàgina com a imatge (PDF, còmics); la resta, com a text que es pot ajustar. */
enum class Format(val extensions: List<String>, val etiqueta: String, val suportat: Boolean, val paginat: Boolean = false) {
    EPUB(listOf("epub"), "EPUB", true),
    PDF(listOf("pdf"), "PDF", true, paginat = true),
    MOBI(listOf("mobi", "azw", "azw3", "prc"), "Kindle", true),
    FB2(listOf("fb2"), "FB2", true),
    DOCX(listOf("docx"), "Word", true),
    ODT(listOf("odt"), "ODT", true),
    RTF(listOf("rtf"), "RTF", true),
    TXT(listOf("txt"), "TXT", true),
    CBZ(listOf("cbz"), "Còmic", true, paginat = true),
    CBR(listOf("cbr"), "CBR", false, paginat = true),
    DJVU(listOf("djvu", "djv"), "DJVU", false, paginat = true),
    DOC(listOf("doc"), "DOC", false),
    CHM(listOf("chm"), "CHM", false);

    companion object {
        fun perNom(nom: String): Format? {
            val ext = nom.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

data class Llibre(
    val cami: String,
    val nomFitxer: String,
    val format: Format,
    val mida: Long,
    val modificat: Long,
    val titol: String,
    val autor: String?,
    val serie: String? = null,
    val portada: ByteArray? = null,
)

/** On és l'usuari dins d'un llibre. */
data class Progres(
    val posicio: Int,      // paràgraf o pàgina
    val desplacament: Int, // píxels dins del paràgraf
    val percent: Float,    // 0..1
    val darreraLectura: Long,
)

/** Contingut d'un llibre de text, ja separat en blocs. */
data class Contingut(val blocs: List<Bloc>, val capitols: List<Capitol>)

data class Bloc(val text: String, val tipus: TipusBloc = TipusBloc.PARAGRAF)

enum class TipusBloc { PARAGRAF, TITOL, SUBTITOL }

data class Capitol(val titol: String, val bloc: Int)
