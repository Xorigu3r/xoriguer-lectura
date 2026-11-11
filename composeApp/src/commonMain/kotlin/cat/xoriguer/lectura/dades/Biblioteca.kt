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
    val progres = mutableStateMapOf<String, Progres>().apply { putAll(llegeixProgres()) }

    /** Cerca tots els llibres de les carpetes i en llegeix les metadades. */
    suspend fun escaneja() {
        if (escanejant) return
        escanejant = true
        try {
            val trobats = withContext(Dispatchers.Default) {
                carpetes.flatMap { Plataforma.llistaFitxers(it) }
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
        Plataforma.desaDada("progres.tsv", progres.entries.joinToString("\n") { (k, p) ->
            "$k\t${p.posicio}\t${p.desplacament}\t${p.percent}\t${p.darreraLectura}"
        })
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
