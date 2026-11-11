package cat.xoriguer.lectura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import cat.xoriguer.lectura.pantalles.EstatFulles
import cat.xoriguer.lectura.pantalles.Fulles
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Dibuixa el full de paper a mig passar (per mirar-lo). Només amb PROVA_IMATGES=carpeta. */
class EfectePaginaTest {
    private val paper = Color(0xFFF4ECD8)
    private val tinta = Color(0xFF3B2E1F)
    private val text = "La boira baixava pel coll quan la Mariona va obrir la porta del refugi. Feia tres dies que no veia ningú, " +
        "i el silenci de la muntanya li semblava ja una veu coneguda. A dins, l'estufa encara guardava una mica d'escalf. "

    @Test fun dibuixa() {
        val dir = System.getenv("PROVA_IMATGES")?.let(::File) ?: return
        val W = 600; val H = 860
        // (nom, on s'agafa, on es porta) en fraccions de la pàgina
        val casos = listOf(
            Triple("1-comenca", Offset(.98f, .97f), Offset(.80f, .86f)),
            Triple("2-mig", Offset(.98f, .97f), Offset(.45f, .74f)),
            Triple("3-quasi", Offset(.98f, .97f), Offset(.05f, .80f)),
            Triple("4-dalt", Offset(.98f, .03f), Offset(.55f, .20f)),
        )
        for ((nom, ini, fi) in casos) {
            val estat = EstatFulles(3, 0)
            ImageComposeScene(W, H) {
                Fulles(estat, paper, tinta) { p ->
                    Column(Modifier.fillMaxSize().background(paper).padding(36.dp)) {
                        Text("Capítol ${p + 1}", color = tinta, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 18.dp).fillMaxSize(0f))
                        Text("Pàgina ${p + 1}. " + text.repeat(4), color = tinta, fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 29.sp, textAlign = TextAlign.Justify)
                    }
                }
            }.use { s ->
                s.render(0)
                estat.comenca(Offset(ini.x * W, ini.y * H))
                estat.mou(Offset(fi.x * W, fi.y * H))
                s.render(16_000_000)
                val img = s.render(32_000_000)
                File(dir, "fulla-$nom.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }

    /** Llibre obert (llom al mig): el revers del full ha de ser la pàgina esquerra següent, ben orientada. */
    @Test fun dibuixaDoble() {
        val dir = System.getenv("PROVA_IMATGES")?.let(::File) ?: return
        val W = 1200; val H = 800
        for ((nom, fi) in listOf("doble-1" to Offset(.80f, .85f), "doble-2" to Offset(.62f, .70f), "doble-3" to Offset(.52f, .80f), "doble-4" to Offset(.60f, .30f))) {
            val estat = EstatFulles(3, 0)
            ImageComposeScene(W, H) {
                Fulles(estat, paper, tinta, llom = .5f) { f ->
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(paper)) {
                        for (k in 0..1) Column(Modifier.weight(1f).padding(36.dp)) {
                            Text(if (f == 0) "Pàgina ${k + 1}" else "PÀGINA ${f * 2 + k + 1} (següent)", color = tinta, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                            Text(text.repeat(3), color = tinta, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Justify)
                        }
                    }
                }
            }.use { s ->
                s.render(0)
                val ini = if (nom == "doble-4") Offset(.98f * W, .03f * H) else Offset(.98f * W, .97f * H)
                estat.comenca(ini)
                estat.mou(Offset(fi.x * W, fi.y * H) + (ini - Offset(.98f * W, if (nom == "doble-4") .03f * H else .97f * H)))
                s.render(16_000_000)
                File(dir, "fulla-$nom.png").writeBytes(s.render(32_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}