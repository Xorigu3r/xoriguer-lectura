package cat.xoriguer.lectura

import androidx.compose.runtime.*
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Format
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.pantalles.BibliotecaPantalla
import cat.xoriguer.lectura.pantalles.CitesPantalla
import cat.xoriguer.lectura.pantalles.LectorPantalla
import cat.xoriguer.lectura.tema.TemaXoriguer

/** Es crida quan l'app torna a primer pla (p. ex. després de donar permisos). */
object EnTornar { var accio: (() -> Unit)? = null }

private fun nomDeCami(cami: String) = cami.substringAfterLast('/').substringAfterLast('\\')

@Composable
fun App(fitxerInicial: String? = null, obreCites: Boolean = false) {
    TemaXoriguer {
        val bib = remember { Biblioteca() }
        var obert by remember { mutableStateOf<Llibre?>(null) }
        var anarA by remember { mutableStateOf<Int?>(null) }
        var cites by remember { mutableStateOf(obreCites) }
        var torna by remember { mutableStateOf(0) }
        DisposableEffect(Unit) {
            EnTornar.accio = { torna++ }
            onDispose { EnTornar.accio = null }
        }
        LaunchedEffect(torna) { if (Plataforma.tePermisFitxers()) bib.escaneja() }
        // Obrir un fitxer directament (doble clic / "Obre amb..." a Windows)
        LaunchedEffect(fitxerInicial) {
            val f = fitxerInicial ?: return@LaunchedEffect
            val nom = nomDeCami(f)
            val fmt = Format.perNom(nom) ?: return@LaunchedEffect
            if (!Plataforma.existeix(f)) return@LaunchedEffect
            obert = bib.llibres.firstOrNull { it.cami == f } ?: Llibre(f, nom, fmt, 0, 0, Biblioteca.titolDeNom(nom), null)
        }

        val llibre = obert
        if (llibre != null) {
            GestorEnrere(true) { obert = null }
            key(llibre.cami, anarA) { LectorPantalla(llibre, bib, anarA) { obert = null } }
        } else if (cites) {
            GestorEnrere(true) { cites = false }
            CitesPantalla(bib, obre = { a ->
                val nom = nomDeCami(a.cami)
                val fmt = Format.perNom(nom)
                if (fmt != null) {
                    anarA = a.bloc
                    obert = bib.llibres.firstOrNull { it.cami == a.cami } ?: Llibre(a.cami, nom, fmt, 0, 0, a.titolLlibre, a.autor)
                }
            }) { cites = false }
        } else {
            BibliotecaPantalla(bib, obreCites = { cites = true }) { anarA = null; obert = it }
        }
    }
}

/** El botó "enrere" del sistema (Android). A la resta no fa res. */
@Composable
expect fun GestorEnrere(actiu: Boolean, accio: () -> Unit)
