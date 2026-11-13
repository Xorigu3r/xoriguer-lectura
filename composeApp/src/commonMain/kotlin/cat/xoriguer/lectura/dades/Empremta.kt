package cat.xoriguer.lectura.dades

import cat.xoriguer.lectura.Plataforma

/**
 * Empremta d'un llibre a partir del contingut (la mida i els primers i últims 64 KB), no del nom ni de la carpeta.
 * Serveix per reconèixer un llibre que s'ha mogut o canviat de nom fora de l'app, o el mateix llibre en un altre
 * dispositiu, i no perdre'n el progrés, les cites ni les col·leccions.
 */
object Empremta {
    private const val TROS = 64 * 1024

    fun de(cami: String, mida: Long): String? {
        val (a, b) = Plataforma.extrems(cami, TROS) ?: return null
        return mida.toString(36) + "-" + fnv(a, fnv(b, -0x340d631b7bdddcdbL)).toULong().toString(16)
    }

    /** FNV-1a de 64 bits: ràpid i amb poques col·lisions per a això. */
    fun fnv(bytes: ByteArray, llavor: Long = -0x340d631b7bdddcdbL): Long {
        var h = llavor
        for (x in bytes) { h = h xor (x.toLong() and 0xFF); h *= 0x100000001b3L }
        return h
    }
}

/** Empremta guardada d'un fitxer (per no tornar-la a calcular si el fitxer no ha canviat). */
data class Identitat(val id: String, val mida: Long, val modificat: Long)
