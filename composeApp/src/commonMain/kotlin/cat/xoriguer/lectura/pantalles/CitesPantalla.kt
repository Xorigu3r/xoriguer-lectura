package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.dades.*
import cat.xoriguer.lectura.res.Res
import cat.xoriguer.lectura.res.logo
import cat.xoriguer.lectura.tema.Xoriguer
import org.jetbrains.compose.resources.painterResource

private enum class Filtre(val nom: String) { TOTES("Totes"), CITES("Cites"), NOTES("Notes"), MARCADORS("Marcadors") }
private enum class OrdreCites(val nom: String) { RECENTS("Més recents"), LLIBRE("Per llibre"), COLOR("Per color") }

/** Secció "Cites i notes": totes les cites, notes i marcadors de tots els llibres en un sol lloc. */
@Suppress("DEPRECATION")
@Composable
fun CitesPantalla(bib: Biblioteca, obre: (Anotacio) -> Unit, enrere: () -> Unit) {
    val porta = LocalClipboardManager.current
    var cerca by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf(Filtre.TOTES) }
    var color by remember { mutableStateOf<ColorCita?>(null) }
    var ordre by remember { mutableStateOf(OrdreCites.RECENTS) }
    var menuOrdre by remember { mutableStateOf(false) }
    var editant by remember { mutableStateOf<Anotacio?>(null) }
    var copiat by remember { mutableStateOf(false) }
    val totes = bib.anotacions.totes

    val llista = remember(totes.toList(), cerca, filtre, color, ordre) {
        val q = cerca.trim().lowercase()
        totes.asSequence()
            .filter {
                when (filtre) {
                    Filtre.TOTES -> true
                    Filtre.CITES -> it.tipus == TipusAnotacio.CITA
                    Filtre.NOTES -> it.teNota
                    Filtre.MARCADORS -> it.tipus == TipusAnotacio.MARCADOR
                }
            }
            .filter { color == null || (it.tipus == TipusAnotacio.CITA && it.color == color) }
            .filter { q.isEmpty() || it.text.lowercase().contains(q) || it.nota.lowercase().contains(q) || it.titolLlibre.lowercase().contains(q) || it.autor?.lowercase()?.contains(q) == true }
            .let { s ->
                when (ordre) {
                    OrdreCites.RECENTS -> s.sortedByDescending { it.data }
                    OrdreCites.LLIBRE -> s.sortedWith(compareBy({ it.titolLlibre.lowercase() }, { it.bloc }, { it.inici }))
                    OrdreCites.COLOR -> s.sortedWith(compareBy({ it.tipus }, { it.color.ordinal }, { -it.data }))
                }
            }.toList()
    }

    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        Capcalera {
            Box {
                IconButton({ menuOrdre = true }) { Icon(Icons.Default.Sort, "Ordena", tint = Xoriguer.Crema) }
                DropdownMenu(menuOrdre, { menuOrdre = false }) {
                    OrdreCites.entries.forEach { o ->
                        DropdownMenuItem({ Text(o.nom, color = if (o == ordre) Xoriguer.Taronja else Xoriguer.Txt) }, { ordre = o; menuOrdre = false })
                    }
                }
            }
            IconButton({ porta.setText(AnnotatedString(Anotacions.comText(llista))); copiat = true }, enabled = llista.isNotEmpty()) {
                Icon(if (copiat) Icons.Default.Check else Icons.Default.ContentCopy, "Copia-ho tot", tint = Xoriguer.Crema)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enrere) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = Xoriguer.Crema) }
            Text("Cites i notes", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${llista.size}", color = Xoriguer.Mut, fontSize = 13.sp)
        }
        OutlinedTextField(
            cerca, { cerca = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text("Cerca a les cites i notes") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Filtre.entries.forEach { f ->
                FilterChip(
                    filtre == f, { filtre = f }, { Text(f.nom, fontSize = 13.sp) }, border = null, shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = Xoriguer.Ui, labelColor = Xoriguer.Txt, selectedContainerColor = Xoriguer.Taronja, selectedLabelColor = Xoriguer.Fons),
                )
            }
            Spacer(Modifier.width(8.dp))
            ColorCita.entries.forEach { c ->
                Box(
                    Modifier.size(26.dp).background(c.color, CircleShape)
                        .border(if (c == color) 3.dp else 0.dp, if (c == color) Xoriguer.Crema else c.color, CircleShape)
                        .clickable { color = if (color == c) null else c },
                )
            }
        }

        if (llista.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(painterResource(Res.drawable.logo), null, Modifier.size(180.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (totes.isEmpty()) "Encara no tens cites ni notes.\nMentre llegeixes, mantén premut un paràgraf per ressaltar-lo amb un color o afegir-hi una nota."
                    else "Cap cita no coincideix amb la cerca.",
                    color = Xoriguer.Mut, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ordre == OrdreCites.LLIBRE) {
                llista.groupBy { it.cami }.forEach { (cami, grup) ->
                    item(key = "h$cami") { CapLlibre(grup.first(), grup.size) }
                    items(grup, key = { it.id }) { a -> Fila(a, false, obre) { editant = a } }
                }
            } else items(llista, key = { it.id }) { a -> Fila(a, true, obre) { editant = a } }
        }
    }

    editant?.let { a -> DialegEditaCita(a, { bib.anotacions.actualitza(it); editant = null }, { bib.anotacions.esborra(a); editant = null }) { editant = null } }
}

@Composable
private fun CapLlibre(a: Anotacio, n: Int) = Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.weight(1f)) {
        Text(a.titolLlibre, color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        a.autor?.let { Text(it, color = Xoriguer.Mut, fontSize = 12.sp) }
    }
    Text("$n", color = Xoriguer.Mut, fontSize = 12.sp)
}

@Composable
private fun Fila(a: Anotacio, mostraLlibre: Boolean, obre: (Anotacio) -> Unit, edita: () -> Unit) =
    TargetaCita(a, mostraLlibre, { obre(a) }) {
        IconButton(edita) { Icon(Icons.Default.MoreVert, "Edita", tint = Xoriguer.Mut) }
    }
