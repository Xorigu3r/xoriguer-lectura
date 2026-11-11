package cat.xoriguer.lectura.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colors de la marca Xoriguer (mateixos que Paraules, Dòmino i Botifarra). */
object Xoriguer {
    val Taronja = Color(0xFFF7931E)
    val TaronjaFosc = Color(0xFFC86F0E)
    val Crema = Color(0xFFFAF4E8)
    val Fons = Color(0xFF0B0A09)
    val Capcalera = Color(0xFF000000)
    val Ui = Color(0xFF2B2622)
    val Ui2 = Color(0xFF3A322B)
    val Ui3 = Color(0xFF4A4037)
    val Txt = Color(0xFFF3ECE2)
    val Mut = Color(0xFFB9AC9C)
}

/** Temes de la pàgina de lectura. */
enum class TemaLectura(val nom: String, val fons: Color, val text: Color, val suau: Color) {
    NIT("Nit", Color(0xFF0B0A09), Color(0xFFE6DCCD), Color(0xFF8A7F72)),
    SEPIA("Sèpia", Color(0xFFF4ECD8), Color(0xFF3B2E1F), Color(0xFF8C7A62)),
    DIA("Dia", Color(0xFFFFFDF8), Color(0xFF1C1A17), Color(0xFF7A746B)),
    GRIS("Gris", Color(0xFF2B2622), Color(0xFFDAD2C6), Color(0xFF9A8E80)),
}

private val esquema = darkColorScheme(
    primary = Xoriguer.Taronja,
    onPrimary = Xoriguer.Fons,
    primaryContainer = Xoriguer.TaronjaFosc,
    onPrimaryContainer = Xoriguer.Crema,
    secondary = Xoriguer.Crema,
    onSecondary = Xoriguer.Fons,
    background = Xoriguer.Fons,
    onBackground = Xoriguer.Txt,
    surface = Xoriguer.Fons,
    onSurface = Xoriguer.Txt,
    surfaceVariant = Xoriguer.Ui,
    onSurfaceVariant = Xoriguer.Mut,
    surfaceContainer = Xoriguer.Ui,
    surfaceContainerHigh = Xoriguer.Ui2,
    surfaceContainerHighest = Xoriguer.Ui3,
    surfaceContainerLow = Color(0xFF191613),
    outline = Xoriguer.Ui3,
    outlineVariant = Xoriguer.Ui2,
)

@Composable
fun TemaXoriguer(content: @Composable () -> Unit) = MaterialTheme(colorScheme = esquema, content = content)
