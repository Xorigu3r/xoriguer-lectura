package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.Diccionari
import cat.xoriguer.lectura.dades.Paraula
import cat.xoriguer.lectura.tema.Xoriguer

private val NOM_IDIOMA = mapOf("ca" to "Català", "es" to "Castellà", "en" to "Anglès", "fr" to "Francès")

/** Definició d'una paraula (Viquidiccionari / Wiktionary). Es desa a la secció Diccionari. */
@Composable
fun DialegDiccionari(paraula: String, idiomaLlibre: String, llibre: String, dic: Diccionari, tanca: () -> Unit) {
    var idioma by remember { mutableStateOf(idiomaLlibre) }
    val resultat by produceState<Result<String?>?>(null, paraula, idioma) {
        value = null
        value = runCatching { Diccionari.busca(paraula, idioma) }
    }
    val definicio = resultat?.getOrNull()
    LaunchedEffect(definicio) { definicio?.let { dic.desa(paraula, it, idioma, llibre) } }
    AlertDialog(
        onDismissRequest = tanca, containerColor = Xoriguer.Ui,
        title = { Text(paraula, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NOM_IDIOMA.forEach { (codi, nom) ->
                        FilterChip(idioma == codi, { idioma = codi }, { Text(nom, fontSize = 12.sp) }, border = null,
                            colors = FilterChipDefaults.filterChipColors(containerColor = Xoriguer.Ui2, selectedContainerColor = Xoriguer.Taronja, selectedLabelColor = Xoriguer.Fons))
                    }
                }
                when {
                    resultat == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Xoriguer.Taronja, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text("Buscant…", color = Xoriguer.Mut)
                    }
                    resultat!!.isFailure -> Text("No s'ha pogut connectar amb el diccionari. Cal connexió a internet.", color = Xoriguer.Taronja)
                    definicio == null -> Text("No s'ha trobat aquesta paraula al Viquidiccionari. Prova-ho en un altre idioma o en un diccionari normatiu.", color = Xoriguer.Mut)
                    else -> TextDefinicio(definicio)
                }
                Text("Font: Viquidiccionari / Wiktionary (CC BY-SA).", color = Xoriguer.Mut, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(tanca) { Text("Tanca", color = Xoriguer.Taronja) } },
        dismissButton = {
            TextButton({
                Plataforma.obreWeb(
                    when (idioma) {
                        "ca" -> "https://dlc.iec.cat/Results?DecEntradaText=${paraula.lowercase()}"
                        "es" -> "https://dle.rae.es/${paraula.lowercase()}"
                        else -> "https://$idioma.wiktionary.org/wiki/${paraula.lowercase()}"
                    },
                )
            }) { Text(if (idioma == "ca") "Obre al DIEC" else if (idioma == "es") "Obre al DLE" else "Obre al web", color = Xoriguer.Crema) }
        },
    )
}

@Composable
private fun TextDefinicio(d: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    d.lines().forEach { l ->
        if (l.startsWith("■ ")) Text(l.removePrefix("■ "), color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        else Text(l, color = Xoriguer.Txt, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Secció Diccionari: totes les paraules buscades de tots els llibres. */
@Composable
fun DiccionariPantalla(dic: Diccionari, enrere: () -> Unit) {
    var cerca by remember { mutableStateOf("") }
    var obert by remember { mutableStateOf<Paraula?>(null) }
    val llista = dic.paraules.filter { cerca.isBlank() || it.paraula.contains(cerca, true) || it.definicio.contains(cerca, true) }
    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        Capcalera()
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enrere) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = Xoriguer.Crema) }
            Text("Diccionari", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${dic.paraules.size}", color = Xoriguer.Mut, fontSize = 13.sp)
        }
        OutlinedTextField(
            cerca, { cerca = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), placeholder = { Text("Cerca una paraula") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja),
        )
        if (dic.paraules.isEmpty()) {
            Text(
                "Encara no has buscat cap paraula.\nMentre llegeixes, mantén premut un paràgraf, selecciona una paraula i toca el botó del diccionari.",
                color = Xoriguer.Mut, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(llista, key = { it.paraula + it.idioma }) { p ->
                Row(Modifier.fillMaxWidth().background(Xoriguer.Ui, RoundedCornerShape(8.dp)).clickable { obert = p }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(p.paraula, color = Xoriguer.Crema, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(NOM_IDIOMA[p.idioma] ?: p.idioma, color = Xoriguer.Taronja, fontSize = 11.sp)
                        }
                        Text(p.definicio.lines().firstOrNull { !it.startsWith("■") }.orEmpty(), color = Xoriguer.Txt, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(p.llibre, color = Xoriguer.Mut, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ dic.esborra(p) }) { Icon(Icons.Default.DeleteOutline, "Esborra", tint = Xoriguer.Mut) }
                }
            }
        }
    }
    obert?.let { p ->
        AlertDialog(
            onDismissRequest = { obert = null }, containerColor = Xoriguer.Ui,
            title = { Text(p.paraula, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { TextDefinicio(p.definicio) } },
            confirmButton = { TextButton({ obert = null }) { Text("Tanca", color = Xoriguer.Taronja) } },
        )
    }
}
