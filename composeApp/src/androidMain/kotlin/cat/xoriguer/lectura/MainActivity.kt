package cat.xoriguer.lectura

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
        setContent { App(fitxer) }
    }

    override fun onResume() {
        super.onResume()
        Plataforma.inicia(this)
        EnTornar.accio?.invoke()
    }
}
