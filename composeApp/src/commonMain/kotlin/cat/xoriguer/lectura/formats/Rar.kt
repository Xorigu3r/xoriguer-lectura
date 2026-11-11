package cat.xoriguer.lectura.formats

import cat.xoriguer.lectura.ArxiuZip

/**
 * Lector mínim de RAR (versions 4 i 5) per als còmics CBR: només fitxers desats sense compressió («store»),
 * que és com es guarden sovint els còmics (les imatges ja van comprimides). El RAR comprimit de debò no es pot
 * llegir sense el codi d'UnRAR, que té una llicència incompatible amb la GPL d'aquesta app.
 */
class Rar private constructor(private val b: ByteArray, private val entrades: Map<String, LongRange>, val comprimits: Int) : ArxiuZip {
    override val noms: List<String> = entrades.keys.toList()
    override fun llegeix(nom: String): ByteArray? = entrades[nom]?.let { r -> b.copyOfRange(r.first.toInt(), r.last.toInt() + 1) }
    override fun tanca() {}

    companion object {
        private val SIG4 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        private val SIG5 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

        fun esRar(b: ByteArray) = comenca(b, SIG4) || comenca(b, SIG5)
        private fun comenca(b: ByteArray, s: ByteArray) = b.size >= s.size && s.indices.all { b[it] == s[it] }

        /** Llegeix l'índex del RAR. `comprimits`: quants fitxers no s'han pogut treure (comprimits o xifrats). */
        fun obre(b: ByteArray): Rar = when {
            comenca(b, SIG5) -> rar5(b)
            comenca(b, SIG4) -> rar4(b)
            else -> error("No és un fitxer RAR")
        }

        private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
        private fun u32(b: ByteArray, p: Int) = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)

        private fun rar4(b: ByteArray): Rar {
            val e = LinkedHashMap<String, LongRange>(); var comprimits = 0
            var p = SIG4.size
            while (p + 7 <= b.size) {
                val tipus = b[p + 2].toInt() and 0xFF
                val flags = u16(b, p + 3)
                val mida = u16(b, p + 5)
                if (mida < 7) break
                var dades = if (flags and 0x8000 != 0 && p + 11 <= b.size) u32(b, p + 7) else 0L
                if (tipus == 0x74 && p + 32 <= b.size) { // capçalera de fitxer
                    dades = u32(b, p + 7)
                    val metode = b[p + 25].toInt() and 0xFF
                    val llargNom = u16(b, p + 26)
                    var q = p + 32
                    if (flags and 0x100 != 0) { dades = dades or (u32(b, q) shl 32); q += 8 }
                    val nom = b.copyOfRange(q, (q + llargNom).coerceAtMost(b.size)).let { n ->
                        // amb el bit 0x200 el nom porta una part en Unicode darrere d'un zero: es fa servir la primera
                        val z = n.indexOf(0); (if (z >= 0) n.copyOfRange(0, z) else n).decodeToString()
                    }.replace('\\', '/')
                    val ini = p.toLong() + mida
                    val esCarpeta = flags and 0xE0 == 0xE0
                    if (!esCarpeta) {
                        if (metode == 0x30 && flags and 0x04 == 0 && ini + dades <= b.size) e[nom] = ini until ini + dades
                        else comprimits++
                    }
                }
                if (tipus == 0x7B) break // final de l'arxiu
                p = (p.toLong() + mida + dades).toInt()
            }
            return Rar(b, e, comprimits)
        }

        /** Enter de mida variable de RAR5 (7 bits per byte). Torna (valor, bytes llegits). */
        private fun vint(b: ByteArray, p: Int): Pair<Long, Int> {
            var v = 0L; var i = 0
            while (p + i < b.size && i < 10) {
                val c = b[p + i].toInt() and 0xFF
                v = v or ((c and 0x7F).toLong() shl (7 * i)); i++
                if (c and 0x80 == 0) break
            }
            return v to i
        }

        private fun rar5(b: ByteArray): Rar {
            val e = LinkedHashMap<String, LongRange>(); var comprimits = 0
            var p = SIG5.size
            while (p + 6 < b.size) {
                var q = p + 4 // CRC
                val (midaCap, n1) = vint(b, q); q += n1
                val iniCap = q
                val (tipus, n2) = vint(b, q); q += n2
                val (flags, n3) = vint(b, q); q += n3
                if (flags and 1L != 0L) { q += vint(b, q).second } // àrea extra
                var dades = 0L
                if (flags and 2L != 0L) { val (d, n) = vint(b, q); dades = d; q += n }
                if (tipus == 2L) { // capçalera de fitxer
                    val (fflags, a) = vint(b, q); q += a
                    q += vint(b, q).second // mida desempaquetada
                    q += vint(b, q).second // atributs
                    if (fflags and 2L != 0L) q += 4 // data
                    if (fflags and 4L != 0L) q += 4 // CRC
                    val (comp, c1) = vint(b, q); q += c1
                    q += vint(b, q).second // sistema
                    val (llargNom, c2) = vint(b, q); q += c2
                    val nom = b.copyOfRange(q, (q + llargNom.toInt()).coerceAtMost(b.size)).decodeToString()
                    val esCarpeta = fflags and 1L != 0L
                    val metode = (comp shr 7) and 7
                    val ini = iniCap.toLong() + midaCap
                    if (!esCarpeta) {
                        if (metode == 0L && ini + dades <= b.size) e[nom] = ini until ini + dades else comprimits++
                    }
                } else if (tipus == 4L) comprimits++ // capçalera de xifrat: tot l'arxiu xifrat
                if (tipus == 5L) break // final de l'arxiu
                val seguent = iniCap.toLong() + midaCap + dades
                if (seguent <= p) break
                p = seguent.toInt()
            }
            return Rar(b, e, comprimits)
        }
    }
}
