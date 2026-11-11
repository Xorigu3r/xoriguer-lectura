package cat.xoriguer.lectura

import androidx.compose.runtime.Composable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.posix.memcpy

// iOS: només es pot compilar en un Mac amb Xcode. Pendent: ZIP (EPUB) i PDF (PDFKit).
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object Plataforma {
    actual val nom: String = "iOS"
    private val fm = NSFileManager.defaultManager

    private fun docs(): String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: NSTemporaryDirectory()

    actual fun carpetesPerDefecte(): List<String> = listOf(docs())

    actual fun llistaFitxers(carpeta: String, profunditat: Int): List<FitxerTrobat> {
        val sub = fm.subpathsOfDirectoryAtPath(carpeta, null) ?: return emptyList()
        return sub.mapNotNull { it as? String }.filter { !it.startsWith(".") }.mapNotNull { rel ->
            val cami = "$carpeta/$rel"
            val attrs = fm.attributesOfItemAtPath(cami, null) ?: return@mapNotNull null
            if (attrs[NSFileType] != NSFileTypeRegular) return@mapNotNull null
            val mida = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
            val mod = ((attrs[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0) * 1000
            FitxerTrobat(cami, rel.substringAfterLast('/'), mida, mod.toLong())
        }
    }

    actual fun existeix(cami: String) = fm.fileExistsAtPath(cami)

    actual fun llegeix(cami: String): ByteArray {
        val d = NSData.dataWithContentsOfFile(cami) ?: error("No es pot llegir $cami")
        val out = ByteArray(d.length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), d.bytes, d.length) }
        return out
    }

    actual fun obreZip(cami: String): ArxiuZip? = null
    actual fun obrePdf(cami: String): DocumentPdf? = null

    actual fun llegeixDada(nom: String): String? =
        NSString.stringWithContentsOfFile("${docs()}/.lectura-$nom", NSUTF8StringEncoding, null)

    @Suppress("CAST_NEVER_SUCCEEDS")
    actual fun desaDada(nom: String, text: String) {
        (text as NSString).writeToFile("${docs()}/.lectura-$nom", true, NSUTF8StringEncoding, null)
    }

    actual fun tePermisFitxers() = true
    actual fun demanaPermisFitxers() {}
    actual suspend fun triaCarpeta(): String? = null

    actual suspend fun descarrega(url: String): ByteArray = withContext(Dispatchers.Default) {
        val d = NSURL.URLWithString(url)?.let { NSData.dataWithContentsOfURL(it) } ?: error("No s'ha pogut baixar")
        val out = ByteArray(d.length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), d.bytes, d.length) }
        out
    }

    actual fun carpetaBaixades(): String = "${docs()}/Lectura Xoriguer".also { creaCarpeta(it) }
    actual fun carpetaFonts(): String = "${carpetaBaixades()}/Fonts".also { creaCarpeta(it) }

    actual fun escriu(cami: String, bytes: ByteArray) {
        creaCarpeta(cami.substringBeforeLast('/'))
        val d = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        d.writeToFile(cami, true)
    }

    actual fun creaCarpeta(cami: String) { fm.createDirectoryAtPath(cami, true, null, null) }

    actual fun reanomena(cami: String, nouNom: String): String? {
        val desti = "${cami.substringBeforeLast('/')}/$nouNom"
        return if (fm.moveItemAtPath(cami, desti, null)) desti else null
    }

    actual fun mou(cami: String, carpeta: String): String? {
        creaCarpeta(carpeta)
        val desti = "$carpeta/${cami.substringAfterLast('/')}"
        return if (fm.moveItemAtPath(cami, desti, null)) desti else null
    }

    actual fun esborra(cami: String): Boolean = fm.removeItemAtPath(cami, null)

    // Pendent a iOS: carregar fonts d'un fitxer i la veu (AVSpeechSynthesizer amb delegat)
    actual fun font(cami: String): FontFamily? = null
    actual val veu: Veu = object : Veu {
        override val disponible = false
        override fun parla(text: String, idioma: String?, velocitat: Float, fet: (Boolean) -> Unit) = fet(false)
        override fun atura() {}
    }

    actual fun obreWeb(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }
}

@Composable
actual fun GestorEnrere(actiu: Boolean, accio: () -> Unit) {}
