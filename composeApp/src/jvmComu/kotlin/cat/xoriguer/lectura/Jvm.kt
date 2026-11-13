package cat.xoriguer.lectura

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** Codi compartit per Android i Windows (totes dues són JVM). */
internal object Jvm {
    const val AGENT = "LecturaXoriguer/0.2 (+https://github.com/Xorigu3r/xoriguer-lectura)"

    suspend fun descarrega(url: String): ByteArray = withContext(Dispatchers.IO) {
        var adreca = url
        repeat(6) {
            val c = URI(adreca).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000; c.readTimeout = 90_000
            c.setRequestProperty("User-Agent", AGENT)
            try {
                val codi = c.responseCode
                if (codi in 300..399) {
                    val nova = c.getHeaderField("Location") ?: error("Redirecció sense destí")
                    adreca = URI(adreca).resolve(nova).toString()
                    return@repeat
                }
                if (codi !in 200..299) error("El servidor ha respost $codi")
                return@withContext c.inputStream.use { it.readBytes() }
            } finally { c.disconnect() }
        }
        error("Massa redireccions")
    }

    fun escriu(cami: String, bytes: ByteArray) { File(cami).apply { parentFile?.mkdirs() }.writeBytes(bytes) }
    fun creaCarpeta(cami: String) { File(cami).mkdirs() }

    fun reanomena(cami: String, nouNom: String): String? {
        val f = File(cami)
        val net = nouNom.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (net.isEmpty()) return null
        val desti = File(f.parentFile, net)
        if (desti.exists()) return null
        return if (f.renameTo(desti)) desti.absolutePath else null
    }

    fun mou(cami: String, carpeta: String): String? {
        val f = File(cami); val d = File(carpeta).apply { mkdirs() }
        val desti = File(d, f.name)
        if (desti.exists()) return null
        if (f.renameTo(desti)) return desti.absolutePath
        // entre unitats diferents renameTo falla: es copia i s'esborra
        return runCatching { f.copyTo(desti); f.delete(); desti.absolutePath }.getOrNull()
    }

    fun esborra(cami: String) = File(cami).delete()

    fun extrems(cami: String, mida: Int): Pair<ByteArray, ByteArray>? = runCatching {
        java.io.RandomAccessFile(cami, "r").use { f ->
            val n = f.length(); val k = minOf(mida.toLong(), n).toInt()
            val a = ByteArray(k); f.readFully(a)
            val b = ByteArray(k); f.seek(n - k); f.readFully(b)
            a to b
        }
    }.getOrNull()

    fun diaLocal(): Int = java.time.LocalDate.now().toEpochDay().toInt()
}
