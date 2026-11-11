package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.res.Res
import cat.xoriguer.lectura.res.icona
import cat.xoriguer.lectura.tema.Xoriguer
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

/** Capçalera negra amb el falcó i la línia taronja (com a la resta d'apps Xoriguer). */
@Composable
fun Capcalera(accions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.background(Xoriguer.Capcalera)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(Res.drawable.icona), null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("LECTURA", color = Xoriguer.Crema, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 20.sp)
                Text("X O R I G U E R", color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 11.sp)
            }
            accions()
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Xoriguer.Taronja))
    }
}

private val paletes = listOf(
    Color(0xFF5B2A12) to Color(0xFF2A130A),
    Color(0xFF1F3A3A) to Color(0xFF0E1C1C),
    Color(0xFF3A2A4A) to Color(0xFF1A1324),
    Color(0xFF4A3A1A) to Color(0xFF231B0C),
    Color(0xFF233049) to Color(0xFF101724),
    Color(0xFF4A1E26) to Color(0xFF220D11),
)

/** Portada del llibre: la imatge del fitxer o una de generada amb el títol. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun Portada(llibre: Llibre, modifier: Modifier = Modifier) {
    val imatge: ImageBitmap? = remember(llibre.cami, llibre.portada) {
        llibre.portada?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    Box(modifier.aspectRatio(0.68f).clip(RoundedCornerShape(6.dp)).background(Xoriguer.Ui)) {
        if (imatge != null) {
            Image(imatge, llibre.titol, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val (a, b) = paletes[(llibre.titol.hashCode() and 0x7fffffff) % paletes.size]
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(a, b))).padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Xoriguer.Taronja.copy(alpha = .7f)))
                Text(
                    llibre.titol, color = Xoriguer.Crema, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, lineHeight = 18.sp, maxLines = 5, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    llibre.autor ?: "", color = Xoriguer.Mut, fontSize = 11.sp, maxLines = 2,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            llibre.format.etiqueta, color = Xoriguer.Fons, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).background(Xoriguer.Taronja, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
