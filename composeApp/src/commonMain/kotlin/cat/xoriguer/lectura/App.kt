package cat.xoriguer.lectura

import androidx.compose.runtime.*
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Format
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.dades.Sincronitzacio
import cat.xoriguer.lectura.pantalles.*
import cat.xoriguer.lectura.tema.TemaXoriguer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Es crida quan l'app torna a primer pla (p. ex. després de donar permisos). */
object EnTornar { var accio: (() -> Unit)? = null }

/** Seccions de l'app (a més del lector). */
enum class Seccio { BIBLIOTECA, CITES, GRATUITS, DICCIONARI, DUPLICATS }

private fun nomDeCami(cami: String) = cami.substringAfterLast('/').substringAfterLast('\\')

@Composable
fun App(fitxerInicial: String? = null, seccioInicial: Seccio = Seccio.BIBLIOTECA) {
    TemaXoriguer {
        val bib = remember { Biblioteca() }
        var obert by remember { mutableStateOf<Llibre?>(null) }
        var anarA by remember { mutableStateOf<Int?>(null) }
        var seccio by remember { mutableStateOf(seccioInicial) }
        var torna by remember { mutableStateOf(0) }
        DisposableEffect(Unit) {
            EnTornar.accio = { torna++ }
            onDispose { EnTornar.accio = null }
        }
        LaunchedEffect(torna) {
            if (Plataforma.tePermisFitxers()) {
                bib.escaneja()
                if (Sincronitzacio.carpeta != null) withContext(Dispatchers.Default) { runCatching { Sincronitzacio.sincronitza(bib) } }
            }
        }
        // Obrir un fitxer directament (doble clic / "Obre amb..." a Windows)
        LaunchedEffect(fitxerInicial) {
            val f = fitxerInicial ?: return@LaunchedEffect
            val nom = nomDeCami(f)
            val fmt = Format.perNom(nom) ?: return@LaunchedEffect
            if (!Plataforma.existeix(f)) return@LaunchedEffect
            obert = bib.llibres.firstOrNull { it.cami == f } ?: Llibre(f, nom, fmt, 0, 0, Biblioteca.titolDeNom(nom), null)
        }
        // En tancar un llibre, si hi ha carpeta de sincronització, s'hi desa el progrés
        var tancats by remember { mutableStateOf(0) }
        LaunchedEffect(tancats) {
            if (tancats > 0 && Sincronitzacio.carpeta != null) withContext(Dispatchers.Default) { runCatching { Sincronitzacio.sincronitza(bib) } }
        }

        fun obreLlibre(l: Llibre, a: Int? = null) { anarA = a; obert = l }
        val llibre = obert
        if (llibre != null) {
            GestorEnrere(true) { obert = null; tancats++ }
            key(llibre.cami, anarA) { LectorPantalla(llibre, bib, anarA) { obert = null; tancats++ } }
        } else {
            if (seccio != Seccio.BIBLIOTECA) GestorEnrere(true) { seccio = Seccio.BIBLIOTECA }
            val enrere = { seccio = Seccio.BIBLIOTECA }
            when (seccio) {
                Seccio.BIBLIOTECA -> BibliotecaPantalla(bib, ves = { seccio = it }) { obreLlibre(it) }
                Seccio.CITES -> CitesPantalla(bib, obre = { a ->
                    val nom = nomDeCami(a.cami)
                    Format.perNom(nom)?.let { fmt ->
                        obreLlibre(bib.llibres.firstOrNull { it.cami == a.cami } ?: Llibre(a.cami, nom, fmt, 0, 0, a.titolLlibre, a.autor), a.bloc)
                    }
                }, enrere = enrere)
                Seccio.GRATUITS -> GratuitsPantalla(bib, obre = { obreLlibre(it) }, enrere = enrere)
                Seccio.DICCIONARI -> DiccionariPantalla(bib.diccionari, enrere = enrere)
                Seccio.DUPLICATS -> DuplicatsPantalla(bib, obre = { obreLlibre(it) }, enrere = enrere)
            }
        }
    }
}

/** El botó "enrere" del sistema (Android). A la resta no fa res. */
@Composable
expect fun GestorEnrere(actiu: Boolean, accio: () -> Unit)
