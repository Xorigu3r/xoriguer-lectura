package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.dades.Sincronitzacio
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.launch

/** Fitxa d'un llibre (mantenir premut a la biblioteca): dades, col·leccions i gestió del fitxer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitxaLlibre(l: Llibre, bib: Biblioteca, obre: (Llibre) -> Unit, tanca: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reanomena by remember { mutableStateOf(false) }
    var esborra by remember { mutableStateOf(false) }
    var novaColl by remember { mutableStateOf(false) }
    var avis by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Portada(l, Modifier.width(80.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.titol, color = Xoriguer.Crema, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    l.autor?.let { Text(it, color = Xoriguer.Txt) }
                    l.serie?.let { Text("Sèrie: $it", color = Xoriguer.Mut, fontSize = 12.sp) }
                    Text("${l.format.etiqueta} · ${mida(l.mida)}", color = Xoriguer.Mut, fontSize = 12.sp)
                    bib.progres[l.cami]?.let { Text("Llegit: ${(it.percent * 100).toInt()} %", color = Xoriguer.Mut, fontSize = 12.sp) }
                }
            }
            Text(l.cami, color = Xoriguer.Mut, fontSize = 11.sp)
            // marcar-lo com a llegit (compta a les estadístiques com a llibre acabat)
            val acabat = bib.estadistiques.estaAcabat(l.cami)
            Row(Modifier.fillMaxWidth().clickable { if (acabat) bib.estadistiques.desmarcaAcabat(l.cami) else bib.estadistiques.marcaAcabat(l) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(acabat, null, colors = CheckboxDefaults.colors(checkedColor = Xoriguer.Taronja, checkmarkColor = Xoriguer.Fons))
                Text("L'he acabat de llegir", color = Xoriguer.Txt, fontSize = 14.sp)
            }
            Button({ obre(l) }, colors = ButtonDefaults.buttonColors(Xoriguer.Taronja, Xoriguer.Fons), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null); Spacer(Modifier.width(8.dp)); Text("Llegeix", fontWeight = FontWeight.Bold)
            }

            Text("Col·leccions", color = Xoriguer.Mut, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bib.colleccions.forEach { (nom, c) ->
                    val dins = l.cami in c
                    FilterChip(dins, { if (dins) bib.treuDeColleccio(nom, l) else bib.afegeixAColleccio(nom, l) }, { Text(nom) },
                        leadingIcon = if (dins) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null, border = null,
                        colors = FilterChipDefaults.filterChipColors(containerColor = Xoriguer.Ui2, labelColor = Xoriguer.Txt, selectedContainerColor = Xoriguer.Taronja, selectedLabelColor = Xoriguer.Fons, selectedLeadingIconColor = Xoriguer.Fons))
                }
                AssistChip({ novaColl = true }, { Text("Nova col·lecció") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = Xoriguer.Crema, leadingIconContentColor = Xoriguer.Taronja))
            }

            HorizontalDivider(color = Xoriguer.Ui3)
            Text("Fitxer", color = Xoriguer.Mut, fontSize = 12.sp)
            Accio("Canvia el nom del fitxer", Icons.Default.DriveFileRenameOutline) { reanomena = true }
            Accio("Mou a una altra carpeta", Icons.Default.DriveFileMove) {
                scope.launch {
                    val c = Plataforma.triaCarpeta() ?: return@launch
                    avis = if (bib.mou(l, c) != null) "Mogut a $c" else "No s'ha pogut moure (potser ja hi ha un fitxer amb aquest nom)."
                }
            }
            Accio("Esborra el fitxer", Icons.Default.DeleteForever, perill = true) { esborra = true }
            avis?.let { Text(it, color = Xoriguer.Taronja, fontSize = 13.sp) }
        }
    }

    if (reanomena) {
        var nom by remember { mutableStateOf(l.nomFitxer.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { reanomena = false }, containerColor = Xoriguer.Ui,
            title = { Text("Canvia el nom") },
            text = { OutlinedTextField(nom, { nom = it }, singleLine = true, suffix = { Text(".${l.nomFitxer.substringAfterLast('.')}") }) },
            confirmButton = {
                TextButton({
                    avis = if (bib.reanomena(l, nom.trim()) != null) "Nom canviat." else "No s'ha pogut canviar el nom (potser ja n'hi ha un d'igual)."
                    reanomena = false
                }) { Text("Desa", color = Xoriguer.Taronja) }
            },
            dismissButton = { TextButton({ reanomena = false }) { Text("Cancel·la", color = Xoriguer.Mut) } },
        )
    }
    if (esborra) ConfirmaEsborrar(l, { if (bib.esborra(l)) tanca() else avis = "No s'ha pogut esborrar."; esborra = false }) { esborra = false }
    if (novaColl) {
        var nom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { novaColl = false }, containerColor = Xoriguer.Ui,
            title = { Text("Nova col·lecció") },
            text = { OutlinedTextField(nom, { nom = it }, singleLine = true, placeholder = { Text("p. ex. Per llegir, Clàssics…") }) },
            confirmButton = {
                TextButton({ if (nom.isNotBlank()) { bib.creaColleccio(nom.trim()); bib.afegeixAColleccio(nom.trim(), l) }; novaColl = false }) { Text("Crea", color = Xoriguer.Taronja) }
            },
            dismissButton = { TextButton({ novaColl = false }) { Text("Cancel·la", color = Xoriguer.Mut) } },
        )
    }
}

/** Esborrar és definitiu: sempre es demana confirmació, dient quin fitxer és. */
@Composable
fun ConfirmaEsborrar(l: Llibre, si: () -> Unit, no: () -> Unit) = AlertDialog(
    onDismissRequest = no, containerColor = Xoriguer.Ui,
    title = { Text("Esborrar el fitxer?") },
    text = { Text("S'esborrarà «${l.nomFitxer}» del disc i no es podrà recuperar. Les cites i notes que n'hagis fet es mantenen a Cites i notes.", color = Xoriguer.Txt) },
    confirmButton = { TextButton(si) { Text("Esborra", color = Xoriguer.Taronja) } },
    dismissButton = { TextButton(no) { Text("Cancel·la", color = Xoriguer.Crema) } },
)

@Composable
private fun Accio(text: String, icona: ImageVector, perill: Boolean = false, f: () -> Unit) =
    TextButton(f, Modifier.fillMaxWidth()) {
        Icon(icona, null, tint = if (perill) Xoriguer.Taronja else Xoriguer.Crema)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (perill) Xoriguer.Taronja else Xoriguer.Txt, modifier = Modifier.weight(1f))
    }

/** Llibres duplicats: el mateix llibre en diversos fitxers. */
@Composable
fun DuplicatsPantalla(bib: Biblioteca, obre: (Llibre) -> Unit, enrere: () -> Unit) {
    val grups = remember(bib.llibres) { bib.duplicats() }
    var esborra by remember { mutableStateOf<Llibre?>(null) }
    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        Capcalera()
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enrere) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = Xoriguer.Crema) }
            Text("Llibres duplicats", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${grups.size}", color = Xoriguer.Mut, fontSize = 13.sp)
        }
        if (grups.isEmpty()) {
            Text("No hi ha cap llibre repetit. 👍", color = Xoriguer.Mut, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(grups, key = { g -> g.joinToString { it.cami } }) { g ->
                Column(Modifier.fillMaxWidth().background(Xoriguer.Ui, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.first().titol, color = Xoriguer.Crema, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    g.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.cami, color = Xoriguer.Txt, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${l.format.etiqueta} · ${mida(l.mida)}" + (bib.progres[l.cami]?.let { " · llegit ${(it.percent * 100).toInt()} %" } ?: ""), color = Xoriguer.Mut, fontSize = 11.sp)
                            }
                            IconButton({ obre(l) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Obre", tint = Xoriguer.Crema) }
                            IconButton({ esborra = l }) { Icon(Icons.Default.DeleteOutline, "Esborra", tint = Xoriguer.Mut) }
                        }
                    }
                }
            }
        }
    }
    esborra?.let { l -> ConfirmaEsborrar(l, { bib.esborra(l); esborra = null }) { esborra = null } }
}

/** Còpia i sincronització a través d'una carpeta que el núvol ja sincronitza. */
@Composable
fun DialegSincronitzacio(bib: Biblioteca, tanca: () -> Unit) {
    val scope = rememberCoroutineScope()
    var carpeta by remember { mutableStateOf(Sincronitzacio.carpeta) }
    var resultat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = tanca, containerColor = Xoriguer.Ui,
        title = { Text("Còpia i sincronització") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tria una carpeta que el núvol ja sincronitzi (Google Drive per a ordinador, Dropbox, OneDrive…). " +
                        "S'hi desaran el progrés, les cites i notes, les col·leccions i el diccionari, i es combinaran amb els dels altres dispositius que facin servir la mateixa carpeta.",
                    color = Xoriguer.Txt, fontSize = 13.sp,
                )
                Text(carpeta ?: "Cap carpeta triada", color = if (carpeta != null) Xoriguer.Crema else Xoriguer.Mut, fontSize = 12.sp)
                resultat?.let { Text(it, color = Xoriguer.Taronja, fontSize = 13.sp) }
                Text("Es torna a sincronitzar sol cada cop que obres l'app i quan tanques un llibre.", color = Xoriguer.Mut, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton({ resultat = runCatching { Sincronitzacio.sincronitza(bib) }.getOrElse { "Error: ${it.message}" } }, enabled = carpeta != null) {
                Text("Sincronitza ara", color = Xoriguer.Taronja)
            }
        },
        dismissButton = {
            Row {
                TextButton({ scope.launch { Plataforma.triaCarpeta()?.let { Sincronitzacio.carpeta = it; carpeta = it; resultat = null } } }) { Text("Tria carpeta", color = Xoriguer.Crema) }
                if (carpeta != null) TextButton({ Sincronitzacio.carpeta = null; carpeta = null }) { Text("Desactiva", color = Xoriguer.Mut) }
            }
        },
    )
}
