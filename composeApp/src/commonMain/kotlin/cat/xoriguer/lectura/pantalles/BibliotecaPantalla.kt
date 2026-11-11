package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Format
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.res.Res
import cat.xoriguer.lectura.res.logo
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private enum class Ordre(val nom: String) { RECENTS("Llegits fa poc"), TITOL("Títol"), AUTOR("Autor"), AFEGITS("Data del fitxer") }
private enum class Vista { QUADRICULA, LLISTA }

@Composable
fun BibliotecaPantalla(bib: Biblioteca, obreCites: () -> Unit, obre: (Llibre) -> Unit) {
    val scope = rememberCoroutineScope()
    var cerca by remember { mutableStateOf("") }
    var cercant by remember { mutableStateOf(false) }
    var filtre by remember { mutableStateOf<Format?>(null) }
    var nomesLlegint by remember { mutableStateOf(false) }
    var ordre by remember { mutableStateOf(Ordre.RECENTS) }
    var vista by remember { mutableStateOf(Vista.QUADRICULA) }
    var menuOrdre by remember { mutableStateOf(false) }
    var carpetesObert by remember { mutableStateOf(false) }

    val llista = remember(bib.llibres, cerca, filtre, nomesLlegint, ordre, bib.progres.toMap()) {
        val q = cerca.trim().lowercase()
        bib.llibres.asSequence()
            .filter { filtre == null || it.format == filtre }
            .filter { !nomesLlegint || (bib.progres[it.cami]?.percent ?: 0f) in 0.001f..0.98f }
            .filter { q.isEmpty() || it.titol.lowercase().contains(q) || (it.autor?.lowercase()?.contains(q) == true) || it.nomFitxer.lowercase().contains(q) }
            .let { s ->
                when (ordre) {
                    Ordre.RECENTS -> s.sortedWith(compareByDescending<Llibre> { bib.progres[it.cami]?.darreraLectura ?: 0L }.thenByDescending { it.modificat })
                    Ordre.TITOL -> s.sortedBy { it.titol.lowercase() }
                    Ordre.AUTOR -> s.sortedBy { (it.autor ?: "￿").lowercase() }
                    Ordre.AFEGITS -> s.sortedByDescending { it.modificat }
                }
            }.toList()
    }
    val llegint = remember(bib.llibres, bib.progres.toMap()) {
        bib.llibres.filter { (bib.progres[it.cami]?.percent ?: 0f) in 0.001f..0.98f }
            .sortedByDescending { bib.progres[it.cami]?.darreraLectura ?: 0L }.take(10)
    }

    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        // Al mòbil no hi caben els 6 botons: es queden Cerca i Cites, i la resta va al menú ⋮
        BoxWithConstraints {
            val estret = maxWidth < 600.dp
            var menu by remember { mutableStateOf(false) }
            Capcalera {
                IconButton({ cercant = !cercant; if (!cercant) cerca = "" }) { Icon(Icons.Default.Search, "Cerca", tint = Xoriguer.Crema) }
                IconButton(obreCites) {
                    val n = bib.anotacions.totes.size
                    BadgedBox({ if (n > 0) Badge(containerColor = Xoriguer.Taronja, contentColor = Xoriguer.Fons) { Text("$n") } }) {
                        Icon(Icons.Default.FormatQuote, "Cites i notes", tint = Xoriguer.Crema)
                    }
                }
                if (!estret) {
                    IconButton({ vista = if (vista == Vista.QUADRICULA) Vista.LLISTA else Vista.QUADRICULA }) {
                        Icon(if (vista == Vista.QUADRICULA) Icons.Default.ViewList else Icons.Default.GridView, "Vista", tint = Xoriguer.Crema)
                    }
                    IconButton({ menuOrdre = true }) { Icon(Icons.Default.Sort, "Ordena", tint = Xoriguer.Crema) }
                    IconButton({ carpetesObert = true }) { Icon(Icons.Default.Folder, "Carpetes", tint = Xoriguer.Crema) }
                    IconButton({ scope.launch { bib.escaneja() } }, enabled = !bib.escanejant) { Icon(Icons.Default.Refresh, "Torna a cercar", tint = Xoriguer.Crema) }
                }
                Box {
                    if (estret) IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Més opcions", tint = Xoriguer.Crema) }
                    DropdownMenu(menu, { menu = false }, containerColor = Xoriguer.Ui) {
                        val llista = vista == Vista.QUADRICULA
                        DropdownMenuItem(
                            { Text(if (llista) "Vista de llista" else "Vista de quadrícula") },
                            { vista = if (llista) Vista.LLISTA else Vista.QUADRICULA; menu = false },
                            leadingIcon = { Icon(if (llista) Icons.Default.ViewList else Icons.Default.GridView, null) },
                        )
                        DropdownMenuItem({ Text("Ordena: ${ordre.nom.lowercase()}") }, { menu = false; menuOrdre = true }, leadingIcon = { Icon(Icons.Default.Sort, null) })
                        DropdownMenuItem({ Text("Carpetes") }, { menu = false; carpetesObert = true }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                        DropdownMenuItem(
                            { Text("Torna a cercar llibres") }, { menu = false; scope.launch { bib.escaneja() } },
                            enabled = !bib.escanejant, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        )
                    }
                    DropdownMenu(menuOrdre, { menuOrdre = false }, containerColor = Xoriguer.Ui) {
                        Ordre.entries.forEach { o ->
                            DropdownMenuItem({ Text(o.nom, color = if (o == ordre) Xoriguer.Taronja else Xoriguer.Txt) }, { ordre = o; menuOrdre = false })
                        }
                    }
                }
            }
        }
        if (bib.escanejant) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Xoriguer.Taronja, trackColor = Xoriguer.Fons)

        if (cercant) {
            OutlinedTextField(
                cerca, { cerca = it }, Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                placeholder = { Text("Títol o autor") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja),
            )
        }

        // Filtres
        val formats = remember(bib.llibres) { bib.llibres.map { it.format }.distinct().sortedBy { it.ordinal } }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Xip("Tots (${bib.llibres.size})", filtre == null && !nomesLlegint) { filtre = null; nomesLlegint = false }
            Xip("Llegint", nomesLlegint) { nomesLlegint = !nomesLlegint }
            formats.forEach { f -> Xip(f.etiqueta, filtre == f) { filtre = if (filtre == f) null else f } }
        }

        if (!Plataforma.tePermisFitxers()) {
            Buit("Per trobar els llibres del dispositiu cal el permís d'accés als fitxers.", "Dona permís") { Plataforma.demanaPermisFitxers() }
            return@Column
        }
        if (bib.llibres.isEmpty()) {
            if (bib.escanejant) Buit("Cercant llibres…") else
                Buit("No s'ha trobat cap llibre a les carpetes.\nAfegeix-ne una amb els llibres (EPUB, PDF, FB2, TXT…).", "Carpetes") { carpetesObert = true }
            return@Column
        }

        val mostraLlegint = llegint.isNotEmpty() && cerca.isBlank() && filtre == null && !nomesLlegint
        when (vista) {
            Vista.QUADRICULA -> LazyVerticalGrid(
                GridCells.Adaptive(118.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (mostraLlegint) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Continua(llegint, bib, obre) }
                    item(span = { GridItemSpan(maxLineSpan) }) { Seccio("Biblioteca") }
                }
                items(llista, key = { it.cami }) { l -> CasellaLlibre(l, bib.progres[l.cami]?.percent, obre) }
            }
            Vista.LLISTA -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mostraLlegint) {
                    item { Continua(llegint, bib, obre) }
                    item { Seccio("Biblioteca") }
                }
                items(llista, key = { it.cami }) { l -> FilaLlibre(l, bib.progres[l.cami]?.percent, obre) }
            }
        }
    }

    if (carpetesObert) DialegCarpetes(bib, { carpetesObert = false; scope.launch { bib.escaneja() } })
}

@Composable
private fun Xip(text: String, actiu: Boolean, onClick: () -> Unit) = FilterChip(
    actiu, onClick, { Text(text, fontSize = 13.sp) },
    colors = FilterChipDefaults.filterChipColors(
        containerColor = Xoriguer.Ui, labelColor = Xoriguer.Txt,
        selectedContainerColor = Xoriguer.Taronja, selectedLabelColor = Xoriguer.Fons,
    ),
    border = null, shape = RoundedCornerShape(16.dp),
)

@Composable
private fun Seccio(t: String) = Text(t.uppercase(), color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Continua(llibres: List<Llibre>, bib: Biblioteca, obre: (Llibre) -> Unit) = Column {
    Seccio("Continua llegint")
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(llibres, key = { "c" + it.cami }) { l -> Box(Modifier.width(100.dp)) { CasellaLlibre(l, bib.progres[l.cami]?.percent, obre) } }
    }
}

@Composable
private fun CasellaLlibre(l: Llibre, percent: Float?, obre: (Llibre) -> Unit) = Column(Modifier.clickable { obre(l) }) {
    Portada(l, Modifier.fillMaxWidth())
    BarraProgres(percent)
    Text(l.titol, color = Xoriguer.Txt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
    l.autor?.let { Text(it, color = Xoriguer.Mut, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun FilaLlibre(l: Llibre, percent: Float?, obre: (Llibre) -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Xoriguer.Ui).clickable { obre(l) }.padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Portada(l, Modifier.width(54.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(l.titol, color = Xoriguer.Txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(l.autor, l.serie).joinToString(" · ").ifEmpty { l.nomFitxer }, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${l.format.etiqueta} · ${mida(l.mida)}" + (percent?.let { " · ${(it * 100).toInt()} %" } ?: ""), color = Xoriguer.Mut, fontSize = 11.sp)
        BarraProgres(percent)
    }
}

@Composable
private fun BarraProgres(percent: Float?) {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).height(3.dp).background(Xoriguer.Ui2, RoundedCornerShape(2.dp))) {
        if (percent != null && percent > 0f) Box(Modifier.fillMaxWidth(percent.coerceIn(0f, 1f)).fillMaxHeight().background(Xoriguer.Taronja, RoundedCornerShape(2.dp)))
    }
}

private fun mida(b: Long): String = when {
    b >= 1_000_000 -> "${(b / 100_000) / 10.0} MB"
    b >= 1_000 -> "${b / 1_000} KB"
    else -> "$b B"
}

@Composable
private fun Buit(missatge: String, boto: String? = null, accio: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(Res.drawable.logo), null, Modifier.size(220.dp))
        Spacer(Modifier.height(16.dp))
        Text(missatge, color = Xoriguer.Mut, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif)
        if (boto != null) {
            Spacer(Modifier.height(16.dp))
            Button(accio, colors = ButtonDefaults.buttonColors(Xoriguer.Taronja, Xoriguer.Fons)) { Text(boto, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DialegCarpetes(bib: Biblioteca, tanca: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = tanca,
        containerColor = Xoriguer.Ui,
        title = { Text("Carpetes amb llibres") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bib.carpetes.isEmpty()) Text("Encara no n'hi ha cap.", color = Xoriguer.Mut)
                bib.carpetes.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = Xoriguer.Taronja, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton({ bib.treuCarpeta(c) }) { Icon(Icons.Default.Close, "Treu", tint = Xoriguer.Mut) }
                    }
                }
            }
        },
        confirmButton = { TextButton(tanca) { Text("Fet", color = Xoriguer.Taronja) } },
        dismissButton = {
            TextButton({ scope.launch { Plataforma.triaCarpeta()?.let(bib::afegeixCarpeta) } }) { Text("Afegeix carpeta", color = Xoriguer.Crema) }
        },
    )
}
