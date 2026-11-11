package cat.xoriguer.lectura.formats

import cat.xoriguer.lectura.dades.Bloc
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut

/**
 * Llibres de Kindle sense DRM: MOBI, AZW i AZW3 (KF8).
 * Format: base de dades Palm (PDB) → registre 0 amb les capçaleres PalmDOC, MOBI i EXTH →
 * registres de text (HTML comprimit amb PalmDOC) → registres d'imatges.
 */
class Mobi(private val b: ByteArray) {
    private val registres: List<Int>
    private val r0: Int
    private val compressio: Int
    private val nText: Int
    private val xifrat: Int
    private val tipusText: Int // 65001 = UTF-8, 1252 = Windows-1252
    private val primeraImatge: Int
    private val extraFlags: Int
    private val titolComplet: String?
    private val exth = mutableMapOf<Int, MutableList<ByteArray>>()
    private var kf8: Int = -1 // registre on comença la part KF8 (AZW3 combinat)

    init {
        require(b.size > 78 && String(b.copyOfRange(60, 68).map { it.toInt().toChar() }.toCharArray()).let { it == "BOOKMOBI" || it == "TEXtREAd" }) {
            "No és un fitxer MOBI"
        }
        val n = u16(76)
        registres = (0 until n).map { u32(78 + it * 8) }
        r0 = registres[0]
        compressio = u16(r0)
        nText = u16(r0 + 8)
        xifrat = u16(r0 + 12)
        val teMobi = b.size > r0 + 20 && String(CharArray(4) { (b[r0 + 16 + it].toInt() and 0xFF).toChar() }) == "MOBI"
        if (teMobi) {
            val llarg = u32(r0 + 20)
            tipusText = u32(r0 + 28)
            primeraImatge = u32(r0 + 108)
            extraFlags = if (llarg >= 0xE4) u16(r0 + 0xF2) else 0
            val off = u32(r0 + 84); val len = u32(r0 + 88)
            titolComplet = if (len in 1..1000 && r0 + off + len <= b.size) text(b.copyOfRange(r0 + off, r0 + off + len)) else null
            if (u32(r0 + 128) and 0x40 != 0) llegeixExth(r0 + 16 + llarg)
            exth[121]?.firstOrNull()?.let { kf8 = be32(it, 0) }
        } else {
            tipusText = 1252; primeraImatge = -1; extraFlags = 0; titolComplet = null
        }
    }

    private fun llegeixExth(p: Int) {
        if (p + 12 > b.size || String(CharArray(4) { (b[p + it].toInt() and 0xFF).toChar() }) != "EXTH") return
        val n = u32(p + 8); var q = p + 12
        repeat(n) {
            if (q + 8 > b.size) return
            val tipus = u32(q); val mida = u32(q + 4)
            if (mida < 8 || q + mida > b.size) return
            exth.getOrPut(tipus) { mutableListOf() } += b.copyOfRange(q + 8, q + mida)
            q += mida
        }
    }

    fun metadades(): Metadades {
        val titol = exth[503]?.firstOrNull()?.let(::text) ?: titolComplet
        val autor = exth[100]?.map(::text)?.joinToString(", ")?.ifBlank { null }
        val portada = (exth[201] ?: exth[202])?.firstOrNull()?.let { be32(it, 0) }?.let { imatge(primeraImatge + it) }
        return Metadades(titol?.trim(), autor?.trim(), null, portada)
    }

    private fun imatge(i: Int): ByteArray? {
        if (primeraImatge < 0 || i !in registres.indices) return null
        val ini = registres[i]; val fi = if (i + 1 < registres.size) registres[i + 1] else b.size
        return b.copyOfRange(ini, fi).takeIf { it.size > 8 }
    }

    fun contingut(): Contingut {
        if (xifrat != 0) error("Aquest llibre té DRM (protecció de còpia) i no es pot obrir.")
        if (compressio == 17480) error("Aquest MOBI fa servir la compressió Huffman, que encara no s'admet.")
        // En un AZW3 combinat, la part KF8 (més nova) comença a kf8: el seu registre 0 té el seu propi compte de text
        val base = if (kf8 > 0 && kf8 < registres.size) kf8 else 0
        val nRec = if (base > 0) u16(registres[base] + 8) else nText
        val sortida = ByteArrayBuilder()
        for (i in 1..nRec) {
            val idx = base + i
            if (idx >= registres.size) break
            val ini = registres[idx]; val fi = if (idx + 1 < registres.size) registres[idx + 1] else b.size
            val rec = b.copyOfRange(ini, maxOf(ini, fi - extres(ini, fi)))
            sortida.append(if (compressio == 2) palmdoc(rec) else rec)
        }
        val html = text(sortida.toByteArray())
        val blocs = mutableListOf<Bloc>(); val caps = mutableListOf<Capitol>()
        Text.deHtml(html.replace(Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE), "<p></p>"), blocs, caps)
        return Contingut(blocs, caps)
    }

    /** Bytes del final del registre que no són text (indicats per extraFlags). */
    private fun extres(ini: Int, fi: Int): Int {
        var mida = 0
        var flags = extraFlags shr 1
        while (flags != 0) {
            if (flags and 1 != 0) {
                // mida en format "backward variable-width integer": es llegeix des del final cap enrere
                var v = 0; var desplaca = 0; var p = fi - mida
                while (p > ini) {
                    val c = b[p - 1].toInt() and 0xFF
                    v = v or ((c and 0x7F) shl desplaca); desplaca += 7; p--
                    if (c and 0x80 != 0 || desplaca >= 28) break
                }
                mida += v
            }
            flags = flags shr 1
        }
        if (extraFlags and 1 != 0 && fi - mida - 1 >= ini) mida += (b[fi - mida - 1].toInt() and 0x3) + 1
        return mida.coerceIn(0, fi - ini)
    }

    private fun text(x: ByteArray): String =
        if (tipusText == 65001) x.decodeToString() else buildString(x.size) { for (c in x) append(Text.cp1252((c.toInt() and 0xFF))) }

    private fun u16(p: Int) = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
    private fun u32(p: Int) = be32(b, p)

    companion object {
        fun be32(x: ByteArray, p: Int) = ((x[p].toInt() and 0xFF) shl 24) or ((x[p + 1].toInt() and 0xFF) shl 16) or
            ((x[p + 2].toInt() and 0xFF) shl 8) or (x[p + 3].toInt() and 0xFF)

        /** Descompressió PalmDOC (LZ77 senzill). */
        fun palmdoc(x: ByteArray): ByteArray {
            val o = ByteArrayBuilder(x.size * 2)
            var i = 0
            while (i < x.size) {
                val c = x[i++].toInt() and 0xFF
                when {
                    c == 0 || c in 0x09..0x7F -> o.add(c)
                    c in 0x01..0x08 -> repeat(c) { if (i < x.size) o.add(x[i++].toInt() and 0xFF) }
                    c in 0x80..0xBF -> {
                        if (i >= x.size) break
                        val v = (c shl 8) or (x[i++].toInt() and 0xFF)
                        val dist = (v shr 3) and 0x7FF
                        val llarg = (v and 7) + 3
                        if (dist == 0 || dist > o.size) continue
                        repeat(llarg) { o.add(o.get(o.size - dist)) }
                    }
                    else -> { o.add(' '.code); o.add(c xor 0x80) }
                }
            }
            return o.toByteArray()
        }
    }
}

/** Petit buffer de bytes que creix (comú a totes les plataformes). */
class ByteArrayBuilder(capacitat: Int = 1024) {
    private var buf = ByteArray(maxOf(16, capacitat))
    var size = 0; private set
    fun add(v: Int) { if (size == buf.size) buf = buf.copyOf(buf.size * 2); buf[size++] = v.toByte() }
    fun get(i: Int) = buf[i].toInt() and 0xFF
    fun append(x: ByteArray) {
        if (size + x.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + x.size))
        x.copyInto(buf, size); size += x.size
    }
    fun toByteArray() = buf.copyOf(size)
}
