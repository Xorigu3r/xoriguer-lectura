package cat.xoriguer.lectura.dades

import cat.xoriguer.lectura.Plataforma

/**
 * Còpia i sincronització a través d'una carpeta (per exemple, una carpeta de Google Drive, Dropbox o OneDrive
 * que el programa del núvol ja sincronitza entre dispositius). Es fusionen el progrés, les cites, les col·leccions
 * i el diccionari: no es perd res de cap dispositiu.
 */
object Sincronitzacio {
    private const val SUBCARPETA = "Lectura Xoriguer - dades"

    var carpeta: String?
        get() = Plataforma.llegeixDada("sincro.txt")?.trim()?.ifEmpty { null }
        set(v) = Plataforma.desaDada("sincro.txt", v.orEmpty())

    private fun remot(nom: String) = "${carpeta}/$SUBCARPETA/$nom"

    private fun llegeixRemot(nom: String): String? = runCatching {
        if (Plataforma.existeix(remot(nom))) Plataforma.llegeix(remot(nom)).decodeToString() else null
    }.getOrNull()

    /** Fusiona el que hi ha a la carpeta amb el que hi ha aquí i ho desa als dos llocs. Torna un resum. */
    fun sincronitza(bib: Biblioteca): String {
        val c = carpeta ?: return "No hi ha cap carpeta triada."
        Plataforma.creaCarpeta("$c/$SUBCARPETA")
        // Els camins dels llibres canvien d'un dispositiu a l'altre: es reconeixen per l'empremta del contingut
        // (identitats.tsv de l'altre dispositiu) i, si no, pel nom del fitxer
        val perNom = bib.llibres.associateBy { it.nomFitxer.lowercase() }
        val empremtesRemotes = llegeixRemot("identitats.tsv")?.lines()?.mapNotNull { l ->
            val p = l.split('\t'); if (p.size >= 2) p[0] to p[1] else null
        }?.toMap().orEmpty()
        fun local(cami: String): String =
            if (bib.llibres.any { it.cami == cami }) cami
            else empremtesRemotes[cami]?.let(bib::camiDeEmpremta)
                ?: perNom[cami.substringAfterLast('/').substringAfterLast('\\').lowercase()]?.cami ?: cami

        var nous = 0

        // Progrés: es queda el més recent de cada llibre
        val progres = linkedMapOf<String, List<String>>()
        fun afegeixProgres(text: String?, remap: Boolean) = text?.lines()?.forEach { l ->
            val p = l.split('\t'); if (p.size < 5) return@forEach
            val k = if (remap) local(p[0]) else p[0]
            val ara = progres[k]
            if (ara == null || (p[4].toLongOrNull() ?: 0) > (ara[4].toLongOrNull() ?: 0)) { if (ara == null && remap) nous++; progres[k] = listOf(k) + p.drop(1) }
        }
        afegeixProgres(Plataforma.llegeixDada("progres.tsv"), false)
        afegeixProgres(llegeixRemot("progres.tsv"), true)
        desaTot("progres.tsv", progres.values.joinToString("\n") { it.joinToString("\t") })

        // Cites, notes i marcadors: unió per identificador
        val anot = linkedMapOf<String, Anotacio>()
        Plataforma.llegeixDada("anotacions.tsv")?.lines()?.mapNotNull(Anotacions::deLinia)?.forEach { anot[it.id] = it }
        llegeixRemot("anotacions.tsv")?.lines()?.mapNotNull(Anotacions::deLinia)?.forEach { a ->
            if (a.id !in anot) { anot[a.id] = a.copy(cami = local(a.cami)); nous++ }
        }
        desaTot("anotacions.tsv", anot.values.joinToString("\n", transform = Anotacions::aLinia))

        // Col·leccions: unió de cada col·lecció
        val coll = linkedMapOf<String, MutableSet<String>>()
        fun afegeixColl(text: String?, remap: Boolean) = text?.lines()?.filter { it.isNotBlank() }?.forEach { l ->
            val p = l.split('\t', limit = 2)
            val s = coll.getOrPut(p[0]) { linkedSetOf() }
            if (p.size == 2) s += if (remap) local(p[1]) else p[1]
        }
        afegeixColl(Plataforma.llegeixDada("colleccions.tsv"), false)
        afegeixColl(llegeixRemot("colleccions.tsv"), true)
        desaTot("colleccions.tsv", coll.entries.joinToString("\n") { (n, s) -> if (s.isEmpty()) n else s.joinToString("\n") { "$n\t$it" } })

        // Empremtes d'aquest dispositiu, perquè els altres puguin reconèixer els llibres
        // (s'hi sumen les dels altres dispositius: cada camí amb la seva empremta)
        val totes = empremtesRemotes + bib.llibres.mapNotNull { l -> l.id?.let { l.cami to it } }
        Plataforma.escriu(remot("identitats.tsv"), totes.entries.joinToString("\n") { (c, id) -> "$c\t$id" }.encodeToByteArray())

        // Diccionari i catàlegs: unió de línies
        for (f in listOf("diccionari.tsv", "cataleg.tsv")) {
            val linies = linkedMapOf<String, String>()
            (Plataforma.llegeixDada(f).orEmpty().lines() + llegeixRemot(f).orEmpty().lines()).filter { it.isNotBlank() }.forEach { l ->
                val clau = l.split('\t').take(if (f == "diccionari.tsv") 3 else 2).let { if (f == "diccionari.tsv") it[0].lowercase() + "|" + it.getOrElse(2) { "" } else it.joinToString() }
                linies.putIfAbsent(clau, l)
            }
            desaTot(f, linies.values.joinToString("\n"))
        }

        bib.recarrega()
        return if (nous == 0) "Tot està al dia." else "S'han afegit $nous elements d'altres dispositius."
    }

    private fun desaTot(nom: String, text: String) {
        Plataforma.desaDada(nom, text)
        Plataforma.escriu(remot(nom), text.encodeToByteArray())
    }

}
