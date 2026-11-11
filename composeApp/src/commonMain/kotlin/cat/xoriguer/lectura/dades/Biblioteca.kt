package cat.xoriguer.lectura.dades

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.formats.Llegidors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Estat de la biblioteca: llibres trobats, progrés de lectura i carpetes. */
class Biblioteca {
    var llibres by mutableStateOf<List<Llibre>>(emptyList()); private set
    var escanejant by mutableStateOf(false); private set
    var carpetes by mutableStateOf(llegeixCarpetes()); private set
    val anotacions = Anotacions()
    val diccionari = Diccionari()
    val progres = mutableStateMapOf<String, Progres>().apply { putAll(llegeixProgres()) }

    /** Cerca tots els llibres de les carpetes i en llegeix les metadades. */
    suspend fun escaneja() {
        if (escanejant) return
        escanejant = true
        try {
            val trobats = withContext(Dispatchers.Default) {
                // la carpeta dels llibres baixats es mira sempre, encara que l'usuari hagi triat altres carpetes
                (carpetes + Plataforma.carpetaBaixades()).distinct().flatMap { Plataforma.llistaFitxers(it) }
                    .distinctBy { it.cami }
                    .mapNotNull { f -> Format.perNom(f.nom)?.let { f to it } }
            }
            // Primer es mostren amb el nom del fitxer; després s'omplen títols i portades.
            val anteriors = llibres.associateBy { it.cami }
            llibres = trobats.map { (f, fmt) ->
                anteriors[f.cami]?.takeIf { it.modificat == f.modificat }
                    ?: Llibre(f.cami, f.nom, fmt, f.mida, f.modificat, titolDeNom(f.nom), null)
            }
            val amb = withContext(Dispatchers.Default) {
                llibres.map { l ->
                    if (l.autor != null || l.portada != null || anteriors[l.cami] === l) l
                    else Llegidors.metadades(l.cami, l.format)?.let { m ->
                        l.copy(titol = m.titol ?: l.titol, autor = m.autor, serie = m.serie, portada = m.portada)
                    } ?: l
                }
            }
            llibres = amb
        } finally {
            escanejant = false
        }
    }

    /** Afegeix un fitxer concret (p. ex. un llibre que s'acaba de baixar) sense tornar a escanejar-ho tot. */
    suspend fun afegeix(cami: String): Llibre? {
        val nom = cami.substringAfterLast('/').substringAfterLast('\\')
        val fmt = Format.perNom(nom) ?: return null
        val base = Llibre(cami, nom, fmt, 0, 0, titolDeNom(nom), null)
        val l = withContext(Dispatchers.Default) {
            Llegidors.metadades(cami, fmt)?.let { m -> base.copy(titol = m.titol ?: base.titol, autor = m.autor, serie = m.serie, portada = m.portada) } ?: base
        }
        llibres = listOf(l) + llibres.filter { it.cami != cami }
        return l
    }

    // ---------- Col·leccions (prestatges propis) ----------
    var colleccions by mutableStateOf(llegeixColleccions()); private set

    fun afegeixAColleccio(nom: String, l: Llibre) = canviaColleccions { it + (nom to (it[nom].orEmpty() + l.cami)) }
    fun treuDeColleccio(nom: String, l: Llibre) = canviaColleccions { it + (nom to (it[nom].orEmpty() - l.cami)) }
    fun creaColleccio(nom: String) = canviaColleccions { if (nom.isBlank() || nom in it) it else it + (nom.trim() to emptySet()) }
    fun esborraColleccio(nom: String) = canviaColleccions { it - nom }

    private fun canviaColleccions(f: (Map<String, Set<String>>) -> Map<String, Set<String>>) {
        colleccions = f(colleccions)
        Plataforma.desaDada("colleccions.tsv", colleccions.entries.joinToString("\n") { (n, c) ->
            if (c.isEmpty()) n else c.joinToString("\n") { "$n\t$it" }
        })
    }

    private fun llegeixColleccions(): Map<String, Set<String>> {
        val m = LinkedHashMap<String, MutableSet<String>>()
        Plataforma.llegeixDada("colleccions.tsv")?.lines()?.filter { it.isNotBlank() }?.forEach { l ->
            val p = l.split('\t', limit = 2)
            val s = m.getOrPut(p[0]) { linkedSetOf() }
            if (p.size == 2) s += p[1]
        }
        return m
    }

    // ---------- Duplicats ----------
    /** Grups de fitxers que semblen el mateix llibre (mateix títol i autor, o mateix nom i mida). */
    fun duplicats(): List<List<Llibre>> {
        fun clau(l: Llibre) = (l.titol.lowercase().filter { it.isLetterOrDigit() }) + "|" + (l.autor?.lowercase()?.filter { it.isLetterOrDigit() } ?: "")
        val perTitol = llibres.groupBy(::clau).values.filter { it.size > 1 }
        val perFitxer = llibres.filter { it.mida > 0 }.groupBy { it.nomFitxer.lowercase() to it.mida }.values.filter { it.size > 1 }
        return (perTitol + perFitxer).map { g -> g.sortedBy { it.cami } }.distinctBy { g -> g.map { it.cami }.toSet() }
    }

    // ---------- Gestió de fitxers ----------
    fun reanomena(l: Llibre, nouNom: String): String? {
        val ext = l.nomFitxer.substringAfterLast('.', "")
        val nom = if (ext.isNotEmpty() && !nouNom.endsWith(".$ext", true)) "$nouNom.$ext" else nouNom
        return Plataforma.reanomena(l.cami, nom)?.also { canviaCami(l, it) }
    }

    fun mou(l: Llibre, carpeta: String): String? = Plataforma.mou(l.cami, carpeta)?.also { canviaCami(l, it) }

    /** Esborra el fitxer del disc (no es pot desfer). Les cites es mantenen a la secció Cites i notes. */
    fun esborra(l: Llibre): Boolean {
        if (!Plataforma.esborra(l.cami)) return false
        llibres = llibres.filter { it.cami != l.cami }
        return true
    }

    private fun canviaCami(l: Llibre, nou: String) {
        val nom = nou.substringAfterLast('/').substringAfterLast('\\')
        llibres = llibres.map { if (it.cami == l.cami) it.copy(cami = nou, nomFitxer = nom) else it }
        progres.remove(l.cami)?.let { progres[nou] = it; desaProgresTot() }
        anotacions.canviaCami(l.cami, nou)
        if (colleccions.values.any { l.cami in it }) canviaColleccions { m -> m.mapValues { (_, c) -> if (l.cami in c) c - l.cami + nou else c } }
    }

    private fun desaProgresTot() = Plataforma.desaDada("progres.tsv", progres.entries.joinToString("\n") { (k, p) ->
        "$k\t${p.posicio}\t${p.desplacament}\t${p.percent}\t${p.darreraLectura}"
    })

    /** Torna a llegir progrés, cites i col·leccions del disc (després de sincronitzar). */
    fun recarrega() {
        progres.clear(); progres.putAll(llegeixProgres())
        anotacions.recarrega()
        colleccions = llegeixColleccions()
    }

    fun afegeixCarpeta(c: String) {
        if (c in carpetes) return
        carpetes = carpetes + c
        Plataforma.desaDada("carpetes.txt", carpetes.joinToString("\n"))
    }

    fun treuCarpeta(c: String) {
        carpetes = carpetes - c
        Plataforma.desaDada("carpetes.txt", carpetes.joinToString("\n"))
    }

    @OptIn(ExperimentalTime::class)
    fun desaProgres(l: Llibre, posicio: Int, desplacament: Int, percent: Float) {
        progres[l.cami] = Progres(posicio, desplacament, percent.coerceIn(0f, 1f), Clock.System.now().toEpochMilliseconds())
        desaProgresTot()
    }

    private fun llegeixCarpetes(): List<String> =
        Plataforma.llegeixDada("carpetes.txt")?.lines()?.filter { it.isNotBlank() }?.ifEmpty { null }
            ?: Plataforma.carpetesPerDefecte()

    private fun llegeixProgres(): Map<String, Progres> =
        Plataforma.llegeixDada("progres.tsv")?.lines()?.mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 5) return@mapNotNull null
            p[0] to Progres(p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3].toFloatOrNull() ?: 0f, p[4].toLongOrNull() ?: 0)
        }?.toMap() ?: emptyMap()

    companion object {
        fun titolDeNom(nom: String) = nom.substringBeforeLast('.').replace('_', ' ').trim()
    }
}
