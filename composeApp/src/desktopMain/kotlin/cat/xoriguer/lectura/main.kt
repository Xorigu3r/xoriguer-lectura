package cat.xoriguer.lectura

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cat.xoriguer.lectura.res.Res
import cat.xoriguer.lectura.res.icona
import org.jetbrains.compose.resources.painterResource

fun main(args: Array<String>) {
    activaMenuText() // per poder afegir «Diccionari» al menú de text
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Lectura · Xoriguer",
            icon = painterResource(Res.drawable.icona),
            state = rememberWindowState(width = 1100.dp, height = 800.dp),
        ) { App(args.firstOrNull { !it.startsWith("--") }, args.firstNotNullOfOrNull { a -> Seccio.entries.firstOrNull { "--${it.name.lowercase()}" == a } } ?: Seccio.BIBLIOTECA) }
    }
}
