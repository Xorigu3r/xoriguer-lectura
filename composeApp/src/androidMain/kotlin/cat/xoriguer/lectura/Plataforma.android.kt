package cat.xoriguer.lectura

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.zip.ZipFile

actual object Plataforma {
    actual val nom: String = "Android"
    private lateinit var app: Context
    private var activitat = WeakReference<Activity>(null)

    fun inicia(a: Activity) {
        app = a.applicationContext
        activitat = WeakReference(a)
    }

    actual fun carpetesPerDefecte(): List<String> =
        listOf(Environment.getExternalStorageDirectory().absolutePath)

    actual fun llistaFitxers(carpeta: String, profunditat: Int): List<FitxerTrobat> {
        val arrel = File(carpeta)
        if (!arrel.isDirectory) return emptyList()
        return arrel.walkTopDown().maxDepth(profunditat)
            .onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .filter { it.isFile }
            .map { FitxerTrobat(it.absolutePath, it.name, it.length(), it.lastModified()) }
            .toList()
    }

    actual fun existeix(cami: String) = File(cami).exists()
    actual fun llegeix(cami: String): ByteArray = File(cami).readBytes()
    actual fun obreZip(cami: String): ArxiuZip? = runCatching { ZipAndroid(ZipFile(cami)) }.getOrNull()
    actual fun obrePdf(cami: String): DocumentPdf? = runCatching {
        PdfAndroid(PdfRenderer(ParcelFileDescriptor.open(File(cami), ParcelFileDescriptor.MODE_READ_ONLY)))
    }.getOrNull()

    actual fun llegeixDada(nom: String): String? = File(app.filesDir, nom).takeIf { it.exists() }?.readText()
    actual fun desaDada(nom: String, text: String) { File(app.filesDir, nom).writeText(text) }

    actual fun tePermisFitxers(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else app.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    actual fun demanaPermisFitxers() {
        val a = activitat.get() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${a.packageName}"))
            runCatching { a.startActivity(i) }.onFailure { a.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else {
            a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    // A Android es busca a tota la memòria; triar carpetes concretes vindrà més endavant.
    actual suspend fun triaCarpeta(): String? = null
}

@Composable
actual fun GestorEnrere(actiu: Boolean, accio: () -> Unit) = BackHandler(actiu, accio)

private class ZipAndroid(private val z: ZipFile) : ArxiuZip {
    override val noms: List<String> = z.entries().asSequence().map { it.name }.toList()
    override fun llegeix(nom: String): ByteArray? =
        (z.getEntry(nom) ?: z.entries().asSequence().firstOrNull { it.name.equals(nom, ignoreCase = true) })
            ?.let { z.getInputStream(it).use { s -> s.readBytes() } }
    override fun tanca() = z.close()
}

private class PdfAndroid(private val r: PdfRenderer) : DocumentPdf {
    private val mutex = Mutex()
    override val pagines = r.pageCount
    override suspend fun pagina(index: Int, ample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                r.openPage(index).use { p ->
                    val h = (ample.toFloat() * p.height / p.width).toInt()
                    val bmp = Bitmap.createBitmap(ample, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    override fun tanca() = r.close()
}
