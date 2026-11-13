package cat.xoriguer.lectura

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import cat.xoriguer.lectura.dades.ControlTecles
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var esperantCarpeta: CompletableDeferred<String?>? = null
    private val triaCarpeta = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        esperantCarpeta?.complete(Plataforma.camiDeArbre(uri)); esperantCarpeta = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.BLACK), SystemBarStyle.dark(Color.BLACK))
        super.onCreate(savedInstanceState)
        Plataforma.inicia(this)
        Plataforma.demanaCarpeta = { d -> esperantCarpeta = d; triaCarpeta.launch(null) }
        // "Obre amb…" des d'una altra app (gestor de fitxers, correu…)
        val fitxer = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { if (it.scheme == "file") it.path else null }
        activaMenuText() // per poder afegir «Diccionari» al menú de text del sistema
        setContent { App(fitxer) }
    }

    // Botons de volum = passar pàgina (si el lector està obert i l'opció activada)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val amunt = when (keyCode) { KeyEvent.KEYCODE_VOLUME_UP -> true; KeyEvent.KEYCODE_VOLUME_DOWN -> false; else -> null }
        if (amunt != null && ControlTecles.volum?.invoke(amunt) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && ControlTecles.volum != null) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        Plataforma.inicia(this)
        EnTornar.accio?.invoke()
    }
}
