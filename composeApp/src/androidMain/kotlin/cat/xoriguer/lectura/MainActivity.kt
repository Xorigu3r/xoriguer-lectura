package cat.xoriguer.lectura

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.BLACK), SystemBarStyle.dark(Color.BLACK))
        super.onCreate(savedInstanceState)
        Plataforma.inicia(this)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        Plataforma.inicia(this)
        EnTornar.accio?.invoke()
    }
}
