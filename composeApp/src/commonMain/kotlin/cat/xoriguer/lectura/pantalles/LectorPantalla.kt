package cat.xoriguer.lectura.pantalles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.DocumentPdf
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.dades.*
import cat.xoriguer.lectura.formats.Llegidors
import cat.xoriguer.lectura.tema.TemaLectura
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preferències de lectura (es desen a lector.txt). */
data class Ajustos(
    val mida: Float = 19f,
    val interlineat: Float = 1.55f,
    val marge: Int = 20,
    val serif: Boolean = true,
    val tema: TemaLectura = TemaLectura.NIT,
    val justificat: Boolean = true,
) {
    fun desa() = Plataforma.desaDada("lector.txt", "$mida\n$interlineat\n$marge\n$serif\n${tema.name}\n$justificat")

    companion object {
        fun carrega(): Ajustos = runCatching {
            val l = Plataforma.llegeixDada("lector.txt")!!.lines()
            Ajustos(l[0].toFloat(), l[1].toFloat(), l[2].toInt(), l[3].toBoolean(), TemaLectura.valueOf(l[4]), l[5].toBoolean())
        }.getOrElse { Ajustos() }
    }
}

private sealed interface Estat {
    data object Carregant : Estat
    data class Error(val missatge: String) : Estat
    data class Text(val c: Contingut) : Estat
    data class Pdf(val doc: DocumentPdf) : Estat
}

@Composable
fun LectorPantalla(llibre: Llibre, bib: Biblioteca, anarA: Int? = null, tanca: () -> Unit) {
    var ajustos by remember { mutableStateOf(Ajustos.carrega()) }
    var controls by remember { mutableStateOf(false) }
    var panellAjustos by remember { mutableStateOf(false) }
    var panellIndex by remember { mutableStateOf(false) }
    var seleccionat by remember { mutableStateOf<Int?>(null) }
    val inicial = remember { bib.progres[llibre.cami] }

    val estat by produceState<Estat>(Estat.Carregant, llibre.cami) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                if (llibre.format == Format.PDF) Plataforma.obrePdf(llibre.cami)?.let { Estat.Pdf(it) } ?: Estat.Error("No s'ha pogut obrir el PDF")
                else Estat.Text(Llegidors.contingut(llibre.cami, llibre.format))
            }.getOrElse { Estat.Error(it.message ?: "Error en obrir el llibre") }
        }
    }
    val obert = estat
    DisposableEffect(obert) { onDispose { (obert as? Estat.Pdf)?.doc?.tanca() } }

    val llista = rememberLazyListState(anarA ?: inicial?.posicio ?: 0, if (anarA != null) 0 else inicial?.desplacament ?: 0)
    val meves = bib.anotacions.totes.filter { it.cami == llibre.cami }
    val citesPerBloc = remember(meves) { meves.filter { it.tipus == TipusAnotacio.CITA }.groupBy { it.bloc } }
    val marcadorAqui = meves.firstOrNull { it.tipus == TipusAnotacio.MARCADOR && it.bloc == llista.firstVisibleItemIndex }
    val total = when (val e = estat) { is Estat.Text -> e.c.blocs.size; is Estat.Pdf -> e.doc.pagines; else -> 0 }

    // Desa el progrés quan es para de desplaçar
    LaunchedEffect(llista, total) {
        if (total == 0) return@LaunchedEffect
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        snapshotFlow { llista.firstVisibleItemIndex to llista.firstVisibleItemScrollOffset }
            .distinctUntilChanged().debounce(400)
            .collect { (i, o) -> bib.desaProgres(llibre, i, o, percent(llista, total)) }
    }

    val tema = ajustos.tema
    Box(Modifier.fillMaxSize().background(tema.fons)) {
        when (val e = estat) {
            Estat.Carregant -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Xoriguer.Taronja)
            is Estat.Error -> Text(e.missatge, Modifier.align(Alignment.Center).padding(32.dp), color = tema.text, textAlign = TextAlign.Center)
            is Estat.Text -> TextLlibre(e.c, llista, ajustos, citesPerBloc, { seleccionat = it }) { controls = !controls }
            is Estat.Pdf -> PdfLlibre(e.doc, llista, tema) { controls = !controls }
        }

        AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Xoriguer.Capcalera.copy(alpha = .94f))) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(tanca) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere", tint = Xoriguer.Crema) }
                    Column(Modifier.weight(1f)) {
                        Text(llibre.titol, color = Xoriguer.Crema, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        llibre.autor?.let { Text(it, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1) }
                    }
                    if (total > 0) IconButton({
                        if (marcadorAqui != null) bib.anotacions.esborra(marcadorAqui)
                        else {
                            val i = llista.firstVisibleItemIndex
                            val t = (estat as? Estat.Text)?.c?.blocs?.getOrNull(i)?.text?.let { if (it.length > 90) it.take(90).trimEnd() + "…" else it } ?: "Pàgina ${i + 1}"
                            bib.anotacions.nova(llibre, TipusAnotacio.MARCADOR, i, 0, 0, t, percent = percent(llista, total))
                        }
                    }) {
                        Icon(if (marcadorAqui != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Marcador", tint = if (marcadorAqui != null) Xoriguer.Taronja else Xoriguer.Crema)
                    }
                    if (total > 0) IconButton({ panellIndex = true }) { Icon(Icons.AutoMirrored.Filled.List, "Índex, marcadors i cites", tint = Xoriguer.Crema) }
                    if (estat is Estat.Text) IconButton({ panellAjustos = true }) { Icon(Icons.Default.TextFields, "Lletra", tint = Xoriguer.Crema) }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Xoriguer.Taronja))
            }
        }

        AnimatedVisibility(controls && total > 0, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            BarraPosicio(llista, total, llibre.format == Format.PDF)
        }
    }

    if (panellAjustos) PanellAjustos(ajustos, { ajustos = it; it.desa() }) { panellAjustos = false }
    if (panellIndex) PanellIndex((estat as? Estat.Text)?.c?.capitols ?: emptyList(), meves, llista, bib.anotacions) { panellIndex = false }
    val sel = seleccionat
    val blocSel = sel?.let { (estat as? Estat.Text)?.c?.blocs?.getOrNull(it) }
    if (sel != null && blocSel != null) PanellCita(
        blocSel.text, meves.filter { it.bloc == sel },
        desa = { a, b, color, nota ->
            bib.anotacions.nova(llibre, TipusAnotacio.CITA, sel, a, b, blocSel.text.substring(a, b), color, nota, sel.toFloat() / (total - 1).coerceAtLeast(1))
        },
        actualitza = bib.anotacions::actualitza,
        esborra = bib.anotacions::esborra,
    ) { seleccionat = null }
}

/** Fracció llegida (0..1). Compta també quant s'ha avançat dins del paràgraf de dalt: n'hi ha de molt llargs. */
private fun percent(l: LazyListState, total: Int): Float {
    if (total <= 0) return 0f
    if (!l.canScrollForward) return 1f
    val primer = l.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val dins = if (primer.size > 0) (l.firstVisibleItemScrollOffset.toFloat() / primer.size).coerceIn(0f, 1f) else 0f
    return ((l.firstVisibleItemIndex + dins) / total).coerceIn(0f, 1f)
}

/** Va a una fracció del llibre (inversa de [percent]). */
private suspend fun anaA(l: LazyListState, total: Int, v: Float) {
    if (v >= .999f) { l.scrollToItem(total - 1); l.scrollBy(1_000_000f); return }
    val pos = v * total
    val index = pos.toInt().coerceIn(0, total - 1)
    l.scrollToItem(index)
    val mida = l.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
    if (mida > 0) l.scrollBy((pos - index) * mida)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextLlibre(
    c: Contingut, llista: LazyListState, a: Ajustos, cites: Map<Int, List<Anotacio>>,
    mantePremut: (Int) -> Unit, toca: () -> Unit,
) {
    val familia = if (a.serif) FontFamily.Serif else FontFamily.SansSerif
    // A les pantalles amples el text no passa de ~44 em, com en un llibre de paper
    val maxAmple = (a.mida * 44).dp + (a.marge * 2).dp
    run {
        LazyColumn(
            state = llista,
            modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = toca),
            contentPadding = PaddingValues(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(c.blocs) { i, b ->
                val meves = cites[i].orEmpty()
                val text = ambCites(b.text, meves)
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val fons = Modifier.drawBehind { pintaCites(layout, meves, a.tema) }
                Box(
                    Modifier.widthIn(max = maxAmple).fillMaxWidth()
                        .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { mantePremut(i) }, onClick = toca)
                        .padding(horizontal = a.marge.dp),
                ) {
                when (b.tipus) {
                    TipusBloc.TITOL -> Text(
                        text, color = a.tema.text, fontFamily = familia, fontWeight = FontWeight.Bold,
                        fontSize = (a.mida * 1.35f).sp, lineHeight = 1.25.em, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp).then(fons), onTextLayout = { layout = it },
                    )
                    TipusBloc.SUBTITOL -> Text(
                        text, color = a.tema.text, fontFamily = familia, fontWeight = FontWeight.SemiBold,
                        fontSize = (a.mida * 1.12f).sp, lineHeight = 1.3.em, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).then(fons), onTextLayout = { layout = it },
                    )
                    TipusBloc.PARAGRAF -> Text(
                        text, color = a.tema.text, fontFamily = familia, fontSize = a.mida.sp, lineHeight = a.interlineat.em,
                        textAlign = if (a.justificat) TextAlign.Justify else TextAlign.Start,
                        modifier = Modifier.padding(bottom = (a.mida * .55f).dp).then(fons), onTextLayout = { layout = it },
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun PdfLlibre(doc: DocumentPdf, llista: LazyListState, tema: TemaLectura, toca: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ample = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2400)
        LazyColumn(
            state = llista,
            modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = toca),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(doc.pagines) { i ->
                val img by produceState<ImageBitmap?>(null, i, ample) { value = doc.pagina(i, ample) }
                val im = img
                if (im != null) Image(im, "Pàgina ${i + 1}", Modifier.fillMaxWidth().aspectRatio(im.width.toFloat() / im.height), contentScale = ContentScale.FillWidth)
                else Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(tema.suau.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = tema.suau)
                }
            }
        }
    }
}

@Composable
private fun BarraPosicio(llista: LazyListState, total: Int, pdf: Boolean) {
    val scope = rememberCoroutineScope()
    var arrossegant by remember { mutableStateOf<Float?>(null) }
    val actual = arrossegant ?: percent(llista, total)
    val pagina = arrossegant?.let { (it * total).toInt().coerceIn(0, total - 1) } ?: llista.firstVisibleItemIndex
    Column(Modifier.fillMaxWidth().background(Xoriguer.Capcalera.copy(alpha = .94f)).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                actual, { arrossegant = it }, Modifier.weight(1f),
                onValueChangeFinished = {
                    arrossegant?.let { v -> scope.launch { anaA(llista, total, v) } }
                    arrossegant = null
                },
                colors = SliderDefaults.colors(thumbColor = Xoriguer.Taronja, activeTrackColor = Xoriguer.Taronja, inactiveTrackColor = Xoriguer.Ui3),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (pdf) "${pagina + 1} / $total" else "${(actual * 100).toInt()} %",
                color = Xoriguer.Crema, fontSize = 13.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanellAjustos(a: Ajustos, canvia: (Ajustos) -> Unit, tanca: () -> Unit) {
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tema", color = Xoriguer.Mut, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TemaLectura.entries.forEach { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { canvia(a.copy(tema = t)) }) {
                        Box(
                            Modifier.size(44.dp).background(t.fons, CircleShape)
                                .border(if (t == a.tema) 3.dp else 1.dp, if (t == a.tema) Xoriguer.Taronja else Xoriguer.Ui3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("Aa", color = t.text, fontFamily = FontFamily.Serif) }
                        Text(t.nom, color = Xoriguer.Txt, fontSize = 11.sp)
                    }
                }
            }
            Fila("Mida de la lletra", "${a.mida.toInt()}") { Slider(a.mida, { canvia(a.copy(mida = it)) }, valueRange = 12f..34f, colors = colorsSlider()) }
            Fila("Interlineat", ((a.interlineat * 100).toInt() / 100.0).toString()) { Slider(a.interlineat, { canvia(a.copy(interlineat = it)) }, valueRange = 1.1f..2.2f, colors = colorsSlider()) }
            Fila("Marges", "${a.marge}") { Slider(a.marge.toFloat(), { canvia(a.copy(marge = it.toInt())) }, valueRange = 4f..64f, colors = colorsSlider()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opcio("Serif", a.serif) { canvia(a.copy(serif = true)) }
                Opcio("Sans", !a.serif) { canvia(a.copy(serif = false)) }
                Spacer(Modifier.width(12.dp))
                Opcio("Justificat", a.justificat) { canvia(a.copy(justificat = !a.justificat)) }
            }
        }
    }
}

@Composable
private fun colorsSlider() = SliderDefaults.colors(thumbColor = Xoriguer.Taronja, activeTrackColor = Xoriguer.Taronja, inactiveTrackColor = Xoriguer.Ui3)

@Composable
private fun Fila(nom: String, valor: String, control: @Composable () -> Unit) = Column {
    Row { Text(nom, color = Xoriguer.Mut, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(valor, color = Xoriguer.Crema, fontSize = 12.sp) }
    control()
}

@Composable
private fun Opcio(text: String, actiu: Boolean, onClick: () -> Unit) = Box(
    Modifier.background(if (actiu) Xoriguer.Taronja else Xoriguer.Ui2, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
) { Text(text, color = if (actiu) Xoriguer.Fons else Xoriguer.Txt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanellIndex(caps: List<Capitol>, meves: List<Anotacio>, llista: LazyListState, anotacions: Anotacions, tanca: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actual = llista.firstVisibleItemIndex
    val marcadors = meves.filter { it.tipus == TipusAnotacio.MARCADOR }.sortedBy { it.bloc }
    val cites = meves.filter { it.tipus == TipusAnotacio.CITA }.sortedWith(compareBy({ it.bloc }, { it.inici }))
    var pestanya by remember { mutableStateOf(if (caps.isEmpty()) 1 else 0) }
    var editant by remember { mutableStateOf<Anotacio?>(null) }
    fun ves(bloc: Int) { scope.launch { llista.scrollToItem(bloc); tanca() } }
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui) {
        PrimaryTabRow(pestanya, containerColor = Xoriguer.Ui, contentColor = Xoriguer.Taronja) {
            listOf("Índex", "Marcadors (${marcadors.size})", "Cites (${cites.size})").forEachIndexed { i, t ->
                Tab(pestanya == i, { pestanya = i }, text = { Text(t, color = if (pestanya == i) Xoriguer.Taronja else Xoriguer.Txt) })
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (pestanya == 0) 0.dp else 8.dp),
        ) {
            when (pestanya) {
                0 -> {
                    if (caps.isEmpty()) item { Buidet("Aquest llibre no té capítols.") }
                    itemsIndexed(caps) { i, c ->
                        val aqui = c.bloc <= actual && (i == caps.lastIndex || caps[i + 1].bloc > actual)
                        Text(
                            c.titol, color = if (aqui) Xoriguer.Taronja else Xoriguer.Txt, fontWeight = if (aqui) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { ves(c.bloc) }.padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                    }
                }
                1 -> {
                    if (marcadors.isEmpty()) item { Buidet("Toca el marcador de dalt per guardar on ets.") }
                    itemsIndexed(marcadors) { _, a ->
                        TargetaCita(a, false, { ves(a.bloc) }) { IconButton({ anotacions.esborra(a) }) { Icon(Icons.Default.Bookmark, "Treu", tint = Xoriguer.Taronja) } }
                    }
                }
                else -> {
                    if (cites.isEmpty()) item { Buidet("Mantén premut un paràgraf per ressaltar-lo o afegir-hi una nota.") }
                    itemsIndexed(cites) { _, a ->
                        TargetaCita(a, false, { ves(a.bloc) }) { IconButton({ editant = a }) { Icon(Icons.Default.TextFields, "Edita", tint = Xoriguer.Mut) } }
                    }
                }
            }
        }
    }
    editant?.let { a -> DialegEditaCita(a, { anotacions.actualitza(it); editant = null }, { anotacions.esborra(a); editant = null }) { editant = null } }
}

@Composable
private fun Buidet(t: String) = Text(t, color = Xoriguer.Mut, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
