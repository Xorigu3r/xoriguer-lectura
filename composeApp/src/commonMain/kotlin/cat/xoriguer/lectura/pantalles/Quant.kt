package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.VERSIO_APP
import cat.xoriguer.lectura.dades.Actualitzacions
import cat.xoriguer.lectura.dades.VersioNova
import cat.xoriguer.lectura.res.Res
import cat.xoriguer.lectura.res.logo
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Franja de la biblioteca quan hi ha una versió nova. */
@Composable
fun AvisVersioNova(v: VersioNova, amaga: () -> Unit) {
    var novetats by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Xoriguer.Ui, RoundedCornerShape(10.dp)).clickable { novetats = true }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.NewReleases, null, tint = Xoriguer.Taronja)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Hi ha una versió nova: ${v.versio}", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Tens la $VERSIO_APP. Toca per veure què hi ha de nou.", color = Xoriguer.Mut, fontSize = 12.sp)
        }
        TextButton({ Plataforma.obreWeb(v.baixada) }) { Text("Baixa", color = Xoriguer.Taronja, fontWeight = FontWeight.Bold) }
        TextButton({ Actualitzacions.ignorada = v.versio; amaga() }) { Text("Ara no", color = Xoriguer.Mut) }
    }
    if (novetats) DialegNovetats(v) { novetats = false }
}

@Composable
private fun DialegNovetats(v: VersioNova, tanca: () -> Unit) = AlertDialog(
    onDismissRequest = tanca, containerColor = Xoriguer.Ui,
    title = { Text("Novetats de la versió ${v.versio}") },
    text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(v.notes.ifBlank { "No hi ha notes per a aquesta versió." }, color = Xoriguer.Txt, fontSize = 14.sp, lineHeight = 20.sp)
            if (Plataforma.nom == "Android") Text(
                "\nEn obrir l'APK, el telèfon et demanarà permís per instal·lar-lo. S'instal·la a sobre de la versió que tens, sense perdre res.",
                color = Xoriguer.Mut, fontSize = 12.sp,
            )
            if (Plataforma.nom == "Windows") Text(
                "\nDescomprimeix el ZIP a la mateixa carpeta d'abans (o en una de nova) i obre «Lectura Xoriguer.exe». Les teves dades es mantenen.",
                color = Xoriguer.Mut, fontSize = 12.sp,
            )
        }
    },
    confirmButton = { TextButton({ Plataforma.obreWeb(v.baixada); tanca() }) { Text("Baixa-la", color = Xoriguer.Taronja) } },
    dismissButton = { TextButton({ Plataforma.obreWeb(v.pagina) }) { Text("Pàgina de la versió", color = Xoriguer.Crema) } },
)

/** "Quant a": versió, comprovar si n'hi ha de noves, enllaços i llicència. */
@Composable
fun DialegQuantA(tanca: () -> Unit, trobada: (VersioNova) -> Unit) {
    val scope = rememberCoroutineScope()
    var estat by remember { mutableStateOf<String?>(null) }
    var nova by remember { mutableStateOf<VersioNova?>(null) }
    AlertDialog(
        onDismissRequest = tanca, containerColor = Xoriguer.Ui,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(Res.drawable.logo), null, Modifier.size(150.dp))
                Text("Versió $VERSIO_APP", color = Xoriguer.Crema, fontWeight = FontWeight.Bold)
                Text("Lector de llibres gratuït i de codi obert (GPL-3.0).", color = Xoriguer.Mut, fontSize = 13.sp, textAlign = TextAlign.Center)
                OutlinedButton({
                    estat = "Comprovant…"
                    scope.launch {
                        estat = runCatching { Actualitzacions.comprova(ara = true) }.fold(
                            { v -> nova = v; if (v == null) "Tens la versió més nova." else "Hi ha la versió ${v.versio}.".also { Actualitzacions.ignorada = null; trobada(v) } },
                            { "No s'ha pogut comprovar (cal connexió a internet)." },
                        )
                    }
                }) { Text("Comprova si hi ha una versió nova", color = Xoriguer.Crema) }
                estat?.let { Text(it, color = Xoriguer.Taronja, fontSize = 13.sp) }
                nova?.let { v -> Button({ Plataforma.obreWeb(v.baixada) }, colors = ButtonDefaults.buttonColors(Xoriguer.Taronja, Xoriguer.Fons)) { Text("Baixa la ${v.versio}", fontWeight = FontWeight.Bold) } }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ Plataforma.obreWeb("https://github.com/${Actualitzacions.REPO}") }) { Text("Codi font", color = Xoriguer.Mut) }
                    TextButton({ Plataforma.obreWeb("https://xorigu3r.github.io/") }) { Text("Altres apps Xoriguer", color = Xoriguer.Mut) }
                }
            }
        },
        confirmButton = { TextButton(tanca) { Text("Tanca", color = Xoriguer.Taronja) } },
    )
}
