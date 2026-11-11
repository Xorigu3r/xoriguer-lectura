package cat.xoriguer.lectura.formats

import androidx.compose.ui.graphics.ImageBitmap
import cat.xoriguer.lectura.ArxiuZip
import cat.xoriguer.lectura.DocumentPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Còmic CBZ: un ZIP amb una imatge per pàgina, en ordre de nom ("natural": 2 abans de 10). */
class Comic(private val zip: ArxiuZip) : DocumentPdf {
    val imatges: List<String> = zip.noms
        .filter { n -> !n.startsWith("__MACOSX") && n.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
        .sortedWith(compareBy(ordreNatural) { it.lowercase() })

    private val mutex = Mutex()
    override val pagines = imatges.size

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun pagina(index: Int, ample: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val bytes = mutex.withLock { zip.llegeix(imatges[index]) } ?: return@withContext null
        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }

    fun portada(): ByteArray? = imatges.firstOrNull()?.let(zip::llegeix)

    override fun tanca() = zip.tanca()

    companion object {
        /** Compara "p2.jpg" < "p10.jpg" (els números com a números). */
        val ordreNatural = Comparator<String> { a, b ->
            val ra = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList()
            val rb = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
            for (k in 0 until minOf(ra.size, rb.size)) {
                val x = ra[k]; val y = rb[k]
                val c = if (x[0].isDigit() && y[0].isDigit()) (x.trimStart('0').length - y.trimStart('0').length).takeIf { it != 0 }
                    ?: x.trimStart('0').compareTo(y.trimStart('0')) else x.compareTo(y)
                if (c != 0) return@Comparator c
            }
            ra.size - rb.size
        }
    }
}
