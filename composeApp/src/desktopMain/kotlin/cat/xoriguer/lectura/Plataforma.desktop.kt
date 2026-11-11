package cat.xoriguer.lectura

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.zip.ZipFile
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual object Plataforma {
    actual val nom: String = "Windows"

    private val dades: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "XoriguerLectura").apply { mkdirs() }
    }

    actual fun carpetesPerDefecte(): List<String> {
        val h = File(System.getProperty("user.home"))
        return listOf("Documents", "Downloads", "Books", "Llibres", "Desktop")
            .map { File(h, it) }.filter { it.isDirectory }.map { it.absolutePath }
    }

    actual fun llistaFitxers(carpeta: String, profunditat: Int): List<FitxerTrobat> {
        val arrel = File(carpeta); if (!arrel.isDirectory) return emptyList()
        return arrel.walkTopDown().maxDepth(profunditat)
            .onEnter { !it.name.startsWith(".") && it.name != "node_modules" && it.name != "AppData" }
            .filter { it.isFile }
            .map { FitxerTrobat(it.absolutePath, it.name, it.length(), it.lastModified()) }
            .toList()
    }

    actual fun existeix(cami: String) = File(cami).exists()
    actual fun llegeix(cami: String): ByteArray = File(cami).readBytes()

    actual fun obreZip(cami: String): ArxiuZip? = runCatching { ZipJvm(ZipFile(cami)) }.getOrNull()

    actual fun obrePdf(cami: String): DocumentPdf? = runCatching { PdfBox(Loader.loadPDF(File(cami))) }.getOrNull()

    actual fun llegeixDada(nom: String): String? = File(dades, nom).takeIf { it.exists() }?.readText()
    actual fun desaDada(nom: String, text: String) { File(dades, nom).writeText(text) }

    actual fun tePermisFitxers() = true
    actual fun demanaPermisFitxers() {}

    actual suspend fun triaCarpeta(): String? = withContext(Dispatchers.IO) {
        var r: String? = null
        SwingUtilities.invokeAndWait {
            val fc = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Tria una carpeta amb llibres" }
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) r = fc.selectedFile.absolutePath
        }
        r
    }
}

@Composable
actual fun GestorEnrere(actiu: Boolean, accio: () -> Unit) {}

internal class ZipJvm(private val z: ZipFile) : ArxiuZip {
    override val noms: List<String> = z.entries().asSequence().map { it.name }.toList()
    override fun llegeix(nom: String): ByteArray? =
        (z.getEntry(nom) ?: z.entries().asSequence().firstOrNull { it.name.equals(nom, ignoreCase = true) })
            ?.let { z.getInputStream(it).use { s -> s.readBytes() } }
    override fun tanca() = z.close()
}

private class PdfBox(private val doc: PDDocument) : DocumentPdf {
    private val pintor = PDFRenderer(doc)
    private val mutex = Mutex()
    override val pagines = doc.numberOfPages
    override suspend fun pagina(index: Int, ample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val w = doc.getPage(index).mediaBox.width
                pintor.renderImage(index, ample / w, ImageType.RGB).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    override fun tanca() = doc.close()
}
