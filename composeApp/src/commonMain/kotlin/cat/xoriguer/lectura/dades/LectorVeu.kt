package cat.xoriguer.lectura.dades

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cat.xoriguer.lectura.Plataforma

/** Perquè un control de fora de l'app (la notificació d'Android) pugui aturar la lectura. */
object ControlVeu { var atura: (() -> Unit)? = null }

/**
 * Llegeix en veu alta paràgraf a paràgraf. No depèn de la pantalla: continua encara que
 * l'app estigui en segon pla (a Android, amb el servei en primer pla de la veu).
 */
class LectorVeu(private val blocs: List<Bloc>, private val idioma: String, private val titol: String) {
    var actiu by mutableStateOf(false); private set
    var index by mutableStateOf(0); private set
    var velocitat by mutableStateOf(1f)
    private var torn = 0 // per ignorar avisos de "fet" d'una lectura anterior

    fun comenca(desDe: Int) {
        if (!Plataforma.veu.disponible || blocs.isEmpty()) return
        index = desDe.coerceIn(0, blocs.lastIndex)
        if (!actiu) { actiu = true; Plataforma.veu.sessio(true, titol); ControlVeu.atura = ::atura }
        parla()
    }

    private fun parla() {
        if (!actiu) return
        if (index > blocs.lastIndex) { atura(); return }
        val meu = ++torn
        Plataforma.veu.parla(blocs[index].text, idioma, velocitat) { ok ->
            if (meu != torn || !actiu) return@parla
            if (ok) { index++; parla() } else atura()
        }
    }

    fun salta(n: Int) {
        if (!actiu) return
        torn++ // l'avís de l'actual s'ignora
        Plataforma.veu.atura()
        index = (index + n).coerceIn(0, blocs.lastIndex)
        actiu = true
        parla()
    }

    fun pausa() {
        if (!actiu) return
        actiu = false; torn++
        Plataforma.veu.atura()
        Plataforma.veu.sessio(false)
    }

    fun atura() {
        val estava = actiu
        actiu = false; torn++
        Plataforma.veu.atura()
        if (estava) Plataforma.veu.sessio(false)
        if (ControlVeu.atura == ::atura) ControlVeu.atura = null
    }
}

/** Tecles de fora de la pantalla (botons de volum a Android). `volum(amunt)` torna true si l'ha fet servir. */
object ControlTecles { var volum: ((Boolean) -> Boolean)? = null }
