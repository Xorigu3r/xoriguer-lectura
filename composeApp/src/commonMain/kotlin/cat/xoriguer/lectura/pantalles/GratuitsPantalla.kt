package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.*
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private sealed interface EstatBaixada {
    data object Baixant : EstatBaixada
    data class Fet(val llibre: Llibre) : EstatBaixada
    data class Error(val missatge: String) : EstatBaixada
}

/** Secció "Llibres gratuïts": cerca i baixa llibres de domini públic o lliures. */
@Composable
fun GratuitsPantalla(bib: Biblioteca, obre: (Llibre) -> Unit, enrere: () -> Unit) {
    val scope = rememberCoroutineScope()
    var meus by remember { mutableStateOf(Cataleg.meus()) }
    val fonts = FontLlibres.predeterminades + meus
    var font by remember { mutableStateOf(fonts.first()) }
    var cerca by remember { mutableStateOf("") }
    var pagina by remember { mutableStateOf<PaginaCataleg?>(null) }
    var carregant by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // pila de subcatàlegs OPDS que s'han obert (per tornar enrere)
    var pila by remember { mutableStateOf(listOf<String>()) }
    val baixades = remember { mutableStateMapOf<String, EstatBaixada>() }
    var nouCataleg by remember { mutableStateOf(false) }

    fun carrega(q: String, seguent: String? = null, sub: String? = null) {
        carregant = true; error = null
        scope.launch {
            runCatching {
                if (sub != null) Cataleg.opds(sub) else Cataleg.cerca(font, q, seguent)
            }.onSuccess { p ->
                pagina = if (seguent != null && pagina != null) p.copy(entrades = pagina!!.entrades + p.entrades) else p
            }.onFailure { error = "No s'ha pogut connectar (${it.message ?: "error"}). Comprova la connexió a internet." }
            carregant = false
        }
    }

    // En canviar de font: Gutenberg i els catàlegs OPDS mostren llibres de seguida; Viquitexts espera una cerca
    LaunchedEffect(font) {
        pila = emptyList(); pagina = null
        if (font !is FontLlibres.Wikisource || cerca.isNotBlank()) carrega(cerca)
    }

    GestorEnreraIntern(pila.isNotEmpty()) {
        pila = pila.dropLast(1)
        if (pila.isEmpty()) carrega(cerca) else carrega("", sub = pila.last())
    }

    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        Capcalera()
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({
                if (pila.isNotEmpty()) { pila = pila.dropLast(1); if (pila.isEmpty()) carrega(cerca) else carrega("", sub = pila.last()) } else enrere()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere", tint = Xoriguer.Crema) }
            Column(Modifier.weight(1f)) {
                Text("Llibres gratuïts", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(if (pila.isNotEmpty()) pagina?.titol ?: font.nom else font.descripcio, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Fonts
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fonts.forEach { f ->
                FilterChip(
                    f == font, { font = f }, { Text(f.nom, fontSize = 13.sp) }, border = null, shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = Xoriguer.Ui, labelColor = Xoriguer.Txt, selectedContainerColor = Xoriguer.Taronja, selectedLabelColor = Xoriguer.Fons),
                )
            }
            AssistChip({ nouCataleg = true }, { Text("Catàleg OPDS", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Xoriguer.Crema, leadingIconContentColor = Xoriguer.Taronja))
        }

        // Cerca
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                cerca, { cerca = it },
                Modifier.weight(1f).onKeyEvent { if (it.key == Key.Enter) { pila = emptyList(); carrega(cerca); true } else false },
                placeholder = { Text(if (font is FontLlibres.Wikisource) "Títol o autor (p. ex. Canigó)" else "Títol o autor") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { pila = emptyList(); carrega(cerca) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja),
            )
            Spacer(Modifier.width(8.dp))
            Button({ pila = emptyList(); carrega(cerca) }, colors = ButtonDefaults.buttonColors(Xoriguer.Taronja, Xoriguer.Fons)) { Text("Cerca", fontWeight = FontWeight.Bold) }
        }
        if (carregant) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp), color = Xoriguer.Taronja, trackColor = Xoriguer.Fons)

        val f = font
        val entrades = pagina?.entrades.orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { e -> item { Text(e, color = Xoriguer.Taronja, modifier = Modifier.padding(8.dp)) } }
            if (f is FontLlibres.Wikisource && cerca.isBlank() && entrades.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Prova amb un autor:", color = Xoriguer.Mut, fontSize = 13.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            f.suggeriments.forEach { s -> SuggestionChip({ cerca = s; carrega(s) }, { Text(s) }) }
                        }
                    }
                }
            }
            if (!carregant && pagina != null && entrades.isEmpty() && error == null)
                item { Text("No s'ha trobat res. Prova amb una altra paraula.", color = Xoriguer.Mut, modifier = Modifier.padding(8.dp)) }
            items(entrades, key = { it.baixada ?: it.navega ?: it.titol }) { e ->
                FilaCataleg(
                    e, e.baixada?.let { baixades[it] },
                    baixa = {
                        val url = e.baixada ?: return@FilaCataleg
                        baixades[url] = EstatBaixada.Baixant
                        scope.launch {
                            runCatching { Cataleg.baixa(e) }
                                .mapCatching { cami -> bib.afegeix(cami) ?: error("No s'ha pogut afegir a la biblioteca") }
                                .onSuccess { baixades[url] = EstatBaixada.Fet(it) }
                                .onFailure { baixades[url] = EstatBaixada.Error(it.message ?: "Error en baixar") }
                        }
                    },
                    obre = obre,
                    navega = { e.navega?.let { u -> pila = pila + u; carrega("", sub = u) } },
                )
            }
            pagina?.seguent?.let { s ->
                item {
                    OutlinedButton({ carrega(cerca, seguent = s) }, Modifier.fillMaxWidth(), enabled = !carregant) { Text("Carrega'n més", color = Xoriguer.Crema) }
                }
            }
            item {
                Text(
                    "Només hi ha fonts de llibres de domini públic o amb llicència lliure. Els llibres es desen a «${Plataforma.carpetaBaixades()}».",
                    color = Xoriguer.Mut, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (f is FontLlibres.Opds && pila.isEmpty()) item {
                TextButton({ meus = meus.filter { it.url != f.url }; Cataleg.desaMeus(meus); font = fonts.first() }) {
                    Text("Treu aquest catàleg", color = Xoriguer.Mut)
                }
            }
        }
    }

    if (nouCataleg) DialegCataleg({ nouCataleg = false }) { nom, url ->
        val nou = FontLlibres.Opds(nom, url)
        meus = meus + nou; Cataleg.desaMeus(meus); nouCataleg = false; font = nou
    }
}

/** El botó enrere del sistema, dins de la secció (tornar al catàleg anterior). */
@Composable
private fun GestorEnreraIntern(actiu: Boolean, accio: () -> Unit) = cat.xoriguer.lectura.GestorEnrere(actiu, accio)

@Composable
private fun FilaCataleg(e: EntradaCataleg, estat: EstatBaixada?, baixa: () -> Unit, obre: (Llibre) -> Unit, navega: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Xoriguer.Ui)
            .clickable { if (e.navega != null) navega() else (estat as? EstatBaixada.Fet)?.let { obre(it.llibre) } ?: baixa() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(4.dp)).background(Xoriguer.Ui2), contentAlignment = Alignment.Center) {
            val img = e.portada?.let { imatgeWeb(it) }
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (e.navega != null) Icons.Default.Folder else Icons.AutoMirrored.Filled.MenuBook, null, tint = Xoriguer.Taronja)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.titol, color = Xoriguer.Txt, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            e.autor?.let { Text(it, color = Xoriguer.Crema, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            e.resum?.let { Text(it, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (estat is EstatBaixada.Error) Text(estat.missatge, color = Xoriguer.Taronja, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        when {
            e.navega != null -> Icon(Icons.Default.ChevronRight, null, tint = Xoriguer.Mut)
            estat is EstatBaixada.Baixant -> CircularProgressIndicator(Modifier.size(28.dp), color = Xoriguer.Taronja, strokeWidth = 3.dp)
            estat is EstatBaixada.Fet -> FilledTonalButton({ obre(estat.llibre) }) { Text("Llegeix") }
            else -> IconButton(baixa) { Icon(Icons.Default.Download, "Baixa", tint = Xoriguer.Taronja) }
        }
    }
}

@Composable
private fun DialegCataleg(tanca: () -> Unit, afegeix: (String, String) -> Unit) {
    var nom by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = tanca, containerColor = Xoriguer.Ui,
        title = { Text("Afegeix un catàleg OPDS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Molts llocs de llibres lliures i servidors de casa (com el de Calibre) tenen una adreça OPDS.", color = Xoriguer.Mut, fontSize = 13.sp)
                OutlinedTextField(nom, { nom = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Adreça") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({ if (url.startsWith("http") && url.length > 10) afegeix(nom.ifBlank { url.substringAfter("//").substringBefore('/') }, url.trim()) }) {
                Text("Afegeix", color = Xoriguer.Taronja)
            }
        },
        dismissButton = { TextButton(tanca) { Text("Cancel·la", color = Xoriguer.Mut) } },
    )
}

private val memoriaImatges = mutableMapOf<String, ImageBitmap?>()

/** Imatge d'internet (portades del catàleg), amb memòria cau. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun imatgeWeb(url: String): ImageBitmap? {
    val v by produceState(memoriaImatges[url], url) {
        if (!memoriaImatges.containsKey(url)) {
            val img = runCatching { Plataforma.descarrega(url).decodeToImageBitmap() }.getOrNull()
            if (memoriaImatges.size > 300) memoriaImatges.clear()
            memoriaImatges[url] = img; value = img
        }
    }
    return v
}
