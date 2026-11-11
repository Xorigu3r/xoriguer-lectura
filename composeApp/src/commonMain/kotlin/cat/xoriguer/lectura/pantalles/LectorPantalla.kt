package cat.xoriguer.lectura.pantalles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.onEach
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
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
import cat.xoriguer.lectura.dades.ControlTecles
import cat.xoriguer.lectura.formats.Llegidors
import cat.xoriguer.lectura.tema.TemaLectura
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preferències de lectura (es desen a lector.txt). `font`: "serif", "sans" o el camí d'un fitxer .ttf/.otf. */
data class Ajustos(
    val mida: Float = 19f,
    val interlineat: Float = 1.55f,
    val marge: Int = 20,
    val font: String = "serif",
    val tema: TemaLectura = TemaLectura.NIT,
    val justificat: Boolean = true,
    val passaPagina: Boolean = true,
    val velocitatVeu: Float = 1f,
    /** true = per pàgines (com un llibre); false = desplaçament continu. */
    val pagines: Boolean = true,
    val efecte: Efecte = Efecte.GIRAR,
    val fletxes: Boolean = true,
    /** Botons de volum per passar pàgina (Android). */
    val volum: Boolean = true,
    /** Tipografia de llibre: sagnat a la primera línia i paraules partides a final de línia. */
    val sagnat: Boolean = true,
    val guionat: Boolean = true,
    /** Dues pàgines (llibre obert) quan la pantalla és ampla i apaïsada. */
    val duesPagines: Boolean = true,
) {
    fun desa() = Plataforma.desaDada("lector.txt", listOf(mida, interlineat, marge, font, tema.name, justificat, passaPagina, velocitatVeu, pagines, efecte.name, fletxes, volum, sagnat, guionat, duesPagines).joinToString("\n"))

    companion object {
        fun carrega(): Ajustos {
            val l = Plataforma.llegeixDada("lector.txt")?.lines() ?: return Ajustos()
            val d = Ajustos()
            fun s(i: Int) = l.getOrNull(i)
            // la 0.1 desava "true"/"false" (serif) on ara hi ha la font
            val font = when (val f = s(3)) { null, "true" -> "serif"; "false" -> "sans"; else -> f }
            return Ajustos(
                s(0)?.toFloatOrNull() ?: d.mida, s(1)?.toFloatOrNull() ?: d.interlineat, s(2)?.toIntOrNull() ?: d.marge, font,
                s(4)?.let { runCatching { TemaLectura.valueOf(it) }.getOrNull() } ?: d.tema, s(5)?.toBooleanStrictOrNull() ?: d.justificat,
                s(6)?.toBooleanStrictOrNull() ?: d.passaPagina, s(7)?.toFloatOrNull() ?: d.velocitatVeu,
                s(8)?.toBooleanStrictOrNull() ?: d.pagines, s(9)?.let { runCatching { Efecte.valueOf(it) }.getOrNull() } ?: d.efecte,
                s(10)?.toBooleanStrictOrNull() ?: d.fletxes, s(11)?.toBooleanStrictOrNull() ?: d.volum,
                s(12)?.toBooleanStrictOrNull() ?: d.sagnat, s(13)?.toBooleanStrictOrNull() ?: d.guionat, s(14)?.toBooleanStrictOrNull() ?: d.duesPagines,
            )
        }
    }
}

private sealed interface Estat {
    data object Carregant : Estat
    data class Error(val missatge: String) : Estat
    data class Text(val c: Contingut, val idioma: String) : Estat
    data class Pdf(val doc: DocumentPdf) : Estat
}

/** Tipus de lletra a partir de l'ajust (els fitxers de l'usuari es carreguen un sol cop). */
private val fontsCarregades = mutableMapOf<String, FontFamily?>()
fun familiaDe(font: String): FontFamily = when (font) {
    "serif" -> FontFamily.Serif
    "sans" -> FontFamily.SansSerif
    "mono" -> FontFamily.Monospace
    else -> fontsCarregades.getOrPut(font) { Plataforma.font(font) } ?: FontFamily.Serif
}

@Composable
fun LectorPantalla(llibre: Llibre, bib: Biblioteca, anarA: Int? = null, tanca: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ajustos by remember { mutableStateOf(Ajustos.carrega()) }
    var controls by remember { mutableStateOf(false) }
    var panellAjustos by remember { mutableStateOf(false) }
    var panellIndex by remember { mutableStateOf(false) }
    var miniatures by remember { mutableStateOf(false) }
    var seleccionat by remember { mutableStateOf<Int?>(null) }
    var paraula by remember { mutableStateOf<String?>(null) }
    var cercant by remember { mutableStateOf(false) }
    var textCercat by remember { mutableStateOf("") }
    var troballa by remember { mutableStateOf<Troballa?>(null) }

    // On érem: el mode per pàgines desa el caràcter en negatiu; el continu, els píxels en positiu
    val inicial = remember { bib.progres[llibre.cami] }
    var posicio by remember {
        mutableStateOf(
            if (anarA != null) anarA to 0
            else (inicial?.posicio ?: 0) to (inicial?.desplacament?.let { if (it < 0) -it - 1 else 0 } ?: 0),
        )
    }
    val pxInicial = remember { if (anarA == null) inicial?.desplacament?.takeIf { it > 0 } ?: 0 else 0 }

    val estat by produceState<Estat>(Estat.Carregant, llibre.cami) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                if (llibre.format.paginat) Llegidors.paginat(llibre.cami, llibre.format)?.let { Estat.Pdf(it) }
                    ?: Estat.Error(if (llibre.format.suportat) "No s'ha pogut obrir el fitxer" else Llegidors.missatgeNoSuportat(llibre.format))
                else Llegidors.contingut(llibre.cami, llibre.format).let { c ->
                    Estat.Text(c, Idioma.detecta(c.blocs.take(200).joinToString(" ") { it.text }))
                }
            }.getOrElse { Estat.Error(it.message ?: "Error en obrir el llibre") }
        }
    }
    val obert = estat
    DisposableEffect(obert) { onDispose { (obert as? Estat.Pdf)?.doc?.tanca() } }

    val total = when (val e = estat) { is Estat.Text -> e.c.blocs.size; is Estat.Pdf -> e.doc.pagines; else -> 0 }
    val text = estat as? Estat.Text
    val esPdf = estat is Estat.Pdf
    val perPagines = ajustos.pagines

    // Navegació: per pàgines (la crea la vista quan té les pàgines fetes) o continu (llista)
    val llista = rememberLazyListState(posicio.first, pxInicial)
    var navPagines by remember { mutableStateOf<NavPagines?>(null) }
    val navLlista = remember(llista, total, esPdf) { NavLlista(llista, total, esPdf) }
    val nav: Navegacio? = if (total == 0) null else if (perPagines) navPagines else navLlista

    val meves = bib.anotacions.totes.filter { it.cami == llibre.cami }
    // les cites, i a més el resultat de la cerca ressaltat com si fos una cita taronja
    val citesPerBloc = remember(meves, troballa) {
        val m = meves.filter { it.tipus == TipusAnotacio.CITA }.groupBy { it.bloc }
        troballa?.let { t ->
            val marca = Anotacio("cerca", llibre.cami, "", null, TipusAnotacio.CITA, t.bloc, t.ini, t.fi, "", "", ColorCita.TARONJA, 0, 0f)
            m + (t.bloc to (m[t.bloc].orEmpty() + marca))
        } ?: m
    }
    val blocActual = nav?.blocActual ?: posicio.first
    val marcadorAqui = meves.firstOrNull { it.tipus == TipusAnotacio.MARCADOR && it.bloc == blocActual }

    // Lectura en veu alta
    val veu = remember(text) { text?.let { LectorVeu(it.c.blocs, it.idioma, llibre.titol) } }
    DisposableEffect(veu) { onDispose { veu?.atura() } }
    LaunchedEffect(ajustos.velocitatVeu, veu) { veu?.velocitat = ajustos.velocitatVeu }
    LaunchedEffect(veu?.index, veu?.actiu, nav) {
        val v = veu ?: return@LaunchedEffect
        if (v.actiu) nav?.segueix(v.index)
    }

    // Recorda on som (per refer les pàgines en canviar la lletra) i desa el progrés quan es para
    LaunchedEffect(nav) {
        val n = nav ?: return@LaunchedEffect
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        snapshotFlow { n.perDesar() }
            .distinctUntilChanged()
            .onEach { (b, d) -> posicio = b to (if (d < 0) -d - 1 else 0) }
            .debounce(400)
            .collect { (b, d) -> bib.desaProgres(llibre, b, d, n.fraccio) }
    }

    fun endavant() { scope.launch { nav?.seguent() } }
    fun enrere() { scope.launch { nav?.anterior() } }

    // Tocar: costats = passar pàgina, centre = mostrar/amagar els controls
    fun toca(x: Float) {
        val costats = perPagines || ajustos.passaPagina
        if (controls || !costats || x in 0.3f..0.7f) { controls = !controls; return }
        if (x > .5f) endavant() else enrere()
    }

    // Tecles: fletxes, AvPàg/RePàg i espai (ordinador); botons de volum (Android)
    val focus = remember { FocusRequester() }
    LaunchedEffect(estat) { runCatching { focus.requestFocus() } }
    DisposableEffect(nav, ajustos.volum) {
        ControlTecles.volum = if (ajustos.volum && nav != null) { amunt -> if (veu?.actiu == true) false else { if (amunt) enrere() else endavant(); true } } else null
        onDispose { ControlTecles.volum = null }
    }

    val tema = ajustos.tema
    val capitol = text?.c?.capitols?.let { capitolDe(it, blocActual) }
    Box(
        Modifier.fillMaxSize().background(tema.fons).focusRequester(focus).focusable()
            .onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (k.key) {
                    Key.DirectionRight, Key.PageDown, Key.Spacebar -> { endavant(); true }
                    Key.DirectionLeft, Key.PageUp -> { enrere(); true }
                    else -> false
                }
            },
    ) {
        when (val e = estat) {
            Estat.Carregant -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Xoriguer.Taronja)
            is Estat.Error -> Text(e.missatge, Modifier.align(Alignment.Center).padding(32.dp), color = tema.text, textAlign = TextAlign.Center)
            is Estat.Text ->
                if (perPagines) MarcPagines(nav, tema, ajustos.fletxes, capitol, ::enrere, ::endavant) {
                    TextPaginat(e.c, ajustos, e.idioma, citesPerBloc, veu?.takeIf { it.actiu }?.index, posicio, { navPagines = it }, { seleccionat = it }, ::toca)
                }
                else TextLlibre(e.c, llista, ajustos, citesPerBloc, veu?.takeIf { it.actiu }?.index, { seleccionat = it }, ::toca)
            is Estat.Pdf ->
                if (perPagines) MarcPagines(nav, tema, ajustos.fletxes, null, ::enrere, ::endavant) {
                    DocumentPaginat(e.doc, ajustos, posicio.first, { navPagines = it }, ::toca)
                }
                else PdfLlibre(e.doc, llista, tema, ::toca)
        }

        AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Xoriguer.Capcalera.copy(alpha = .94f))) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(tanca) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere", tint = Xoriguer.Crema) }
                    Column(Modifier.weight(1f)) {
                        Text(llibre.titol, color = Xoriguer.Crema, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        llibre.autor?.let { Text(it, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1) }
                    }
                    if (text != null) IconButton({ cercant = true }) { Icon(Icons.Default.Search, "Cerca dins del llibre", tint = Xoriguer.Crema) }
                    if (veu != null && Plataforma.veu.disponible) IconButton({
                        controls = false
                        if (veu.actiu) veu.pausa() else veu.comenca(blocActual)
                    }) { Icon(Icons.Default.Headphones, "Llegeix en veu alta", tint = if (veu.actiu) Xoriguer.Taronja else Xoriguer.Crema) }
                    if (total > 0) IconButton({
                        if (marcadorAqui != null) bib.anotacions.esborra(marcadorAqui)
                        else {
                            val i = blocActual
                            val t = text?.c?.blocs?.getOrNull(i)?.text?.let { if (it.length > 90) it.take(90).trimEnd() + "…" else it } ?: "Pàgina ${i + 1}"
                            bib.anotacions.nova(llibre, TipusAnotacio.MARCADOR, i, 0, 0, t, percent = nav?.fraccio ?: 0f)
                        }
                    }) {
                        Icon(if (marcadorAqui != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Marcador", tint = if (marcadorAqui != null) Xoriguer.Taronja else Xoriguer.Crema)
                    }
                    if (esPdf) IconButton({ miniatures = true }) { Icon(Icons.Default.GridView, "Miniatures de les pàgines", tint = Xoriguer.Crema) }
                    if (total > 0) IconButton({ panellIndex = true }) { Icon(Icons.AutoMirrored.Filled.List, "Índex, marcadors i cites", tint = Xoriguer.Crema) }
                    if (total > 0) IconButton({ panellAjustos = true }) { Icon(Icons.Default.TextFields, "Ajustos de lectura", tint = Xoriguer.Crema) }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Xoriguer.Taronja))
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (veu != null && (veu.actiu || controls)) ReproductorVeu(veu, ajustos) { ajustos = it; it.desa() }
            AnimatedVisibility(controls && nav != null, enter = fadeIn(), exit = fadeOut()) {
                nav?.let { BarraPosicio(it) }
            }
        }
    }

    if (panellAjustos) PanellAjustos(ajustos, esPdf, { ajustos = it; it.desa() }) { panellAjustos = false }
    if (panellIndex) PanellIndex(text?.c?.capitols ?: emptyList(), meves, blocActual, { b -> scope.launch { nav?.vesABloc(b) } }, bib.anotacions) { panellIndex = false }
    if (miniatures) (estat as? Estat.Pdf)?.let { e -> PanellMiniatures(e.doc, blocActual, { scope.launch { nav?.vesABloc(it) } }) { miniatures = false } }
    val sel = seleccionat
    val blocSel = sel?.let { text?.c?.blocs?.getOrNull(it) }
    if (sel != null && blocSel != null) PanellCita(
        blocSel.text, meves.filter { it.bloc == sel },
        desa = { a, b, color, nota ->
            bib.anotacions.nova(llibre, TipusAnotacio.CITA, sel, a, b, blocSel.text.substring(a, b), color, nota, sel.toFloat() / (total - 1).coerceAtLeast(1))
        },
        actualitza = bib.anotacions::actualitza,
        esborra = bib.anotacions::esborra,
        buscaParaula = { paraula = it },
        llegeixDesDaqui = if (veu != null && Plataforma.veu.disponible) ({ veu.comenca(sel) }) else null,
    ) { seleccionat = null }
    if (cercant && text != null) PanellCerca(
        text.c, text.c.capitols, navPagines?.let { n -> { t: Troballa -> n.numPagina(t.bloc, t.ini) } }, textCercat,
        ves = { t, q -> troballa = t; textCercat = q; cercant = false; controls = false; scope.launch { nav?.vesABloc(t.bloc, t.ini) } },
    ) { cercant = false }
    paraula?.let { p -> DialegDiccionari(p, text?.idioma ?: "ca", llibre.titol, bib.diccionari) { paraula = null } }
}

/** Petit reproductor de la lectura en veu alta: anterior, pausa/continua, següent, velocitat. */
@Composable
private fun ReproductorVeu(veu: LectorVeu, a: Ajustos, canvia: (Ajustos) -> Unit) {
    Row(
        Modifier.padding(12.dp).fillMaxWidth().background(Xoriguer.Ui, RoundedCornerShape(28.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Headphones, null, tint = Xoriguer.Taronja, modifier = Modifier.padding(start = 8.dp))
        IconButton({ veu.salta(-1) }, enabled = veu.actiu) { Icon(Icons.Default.SkipPrevious, "Paràgraf anterior", tint = Xoriguer.Crema) }
        IconButton({ if (veu.actiu) veu.pausa() else veu.comenca(veu.index) }) {
            Icon(if (veu.actiu) Icons.Default.Pause else Icons.Default.PlayArrow, if (veu.actiu) "Pausa" else "Continua", tint = Xoriguer.Crema)
        }
        IconButton({ veu.salta(1) }, enabled = veu.actiu) { Icon(Icons.Default.SkipNext, "Paràgraf següent", tint = Xoriguer.Crema) }
        Spacer(Modifier.weight(1f))
        val velocitats = listOf(.75f, 1f, 1.25f, 1.5f, 2f)
        TextButton({ canvia(a.copy(velocitatVeu = velocitats[(velocitats.indexOf(a.velocitatVeu) + 1) % velocitats.size])) }) {
            Text("${a.velocitatVeu}×".replace(".0×", "×"), color = Xoriguer.Crema)
        }
        if (veu.actiu) IconButton({ veu.atura() }) { Icon(Icons.Default.Close, "Atura", tint = Xoriguer.Mut) }
    }
}

/** Gest de tocar (x en píxels dins de l'element) i de mantenir premut. */
private fun Modifier.tocs(toca: (Float) -> Unit, mantePremut: (() -> Unit)? = null) = pointerInput(toca, mantePremut) {
    detectTapGestures(onTap = { toca(it.x) }, onLongPress = mantePremut?.let { f -> { f() } })
}

@Composable
private fun TextLlibre(
    c: Contingut, llista: LazyListState, a: Ajustos, cites: Map<Int, List<Anotacio>>, llegint: Int?,
    mantePremut: (Int) -> Unit, toca: (Float) -> Unit,
) {
    val familia = remember(a.font) { familiaDe(a.font) }
    // A les pantalles amples el text no passa de ~44 em, com en un llibre de paper
    val maxAmple = (a.mida * 44).dp + (a.marge * 2).dp
    var xArrel by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { xArrel = it.positionInRoot().x }) {
        val ampleTotal = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        LazyColumn(
            state = llista,
            modifier = Modifier.fillMaxSize().tocs({ toca(it / ampleTotal) }),
            contentPadding = PaddingValues(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(c.blocs) { i, b ->
                val meves = cites[i].orEmpty()
                val text = ambCites(b.text, meves)
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val fons = Modifier.drawBehind { pintaCites(layout, meves, a.tema) }
                var x0 by remember { mutableStateOf(0f) }
                Box(
                    Modifier.widthIn(max = maxAmple).fillMaxWidth()
                        .then(if (llegint == i) Modifier.background(Xoriguer.Taronja.copy(alpha = .13f), RoundedCornerShape(6.dp)) else Modifier)
                        .onGloballyPositioned { x0 = it.positionInRoot().x - xArrel }
                        // la posició del toc es passa respecte de tota la pantalla, no del paràgraf
                        .tocs({ x -> toca((x0 + x) / ampleTotal) }, { mantePremut(i) })
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
private fun PdfLlibre(doc: DocumentPdf, llista: LazyListState, tema: TemaLectura, toca: (Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ample = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2400)
        LazyColumn(
            state = llista,
            modifier = Modifier.fillMaxSize().tocs({ toca(it / constraints.maxWidth.coerceAtLeast(1)) }),
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

/** Miniatures de totes les pàgines d'un PDF o còmic: navegació visual ràpida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanellMiniatures(doc: DocumentPdf, actual: Int, ves: (Int) -> Unit, tanca: () -> Unit) {
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Pàgines", color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val estat = androidx.compose.foundation.lazy.grid.rememberLazyGridState((actual - 3).coerceAtLeast(0))
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxWidth().heightIn(max = 640.dp), estat, PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(doc.pagines) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { ves(i); tanca() }) {
                    val img by produceState<ImageBitmap?>(null, i) { value = doc.pagina(i, 220) }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(.707f).background(Xoriguer.Ui2, RoundedCornerShape(4.dp))
                            .border(if (i == actual) 2.dp else 0.dp, if (i == actual) Xoriguer.Taronja else Xoriguer.Ui2, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) { img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
                    Text("${i + 1}", color = if (i == actual) Xoriguer.Taronja else Xoriguer.Mut, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BarraPosicio(nav: Navegacio) {
    val scope = rememberCoroutineScope()
    var arrossegant by remember { mutableStateOf<Float?>(null) }
    val actual = arrossegant ?: nav.fraccio
    val etiqueta = arrossegant?.let { v ->
        if (nav is NavPagines || nav.etiqueta.startsWith("Pàgina")) "Pàgina ${(v * (nav.total - 1)).toInt() + 1} de ${nav.total}" else "${(v * 100).toInt()} %"
    } ?: nav.etiqueta
    Column(Modifier.fillMaxWidth().background(Xoriguer.Capcalera.copy(alpha = .94f)).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                actual, { arrossegant = it }, Modifier.weight(1f),
                onValueChangeFinished = {
                    arrossegant?.let { v -> scope.launch { nav.vesAFraccio(v) } }
                    arrossegant = null
                },
                colors = SliderDefaults.colors(thumbColor = Xoriguer.Taronja, activeTrackColor = Xoriguer.Taronja, inactiveTrackColor = Xoriguer.Ui3),
            )
            Spacer(Modifier.width(12.dp))
            Text(etiqueta, color = Xoriguer.Crema, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanellAjustos(a: Ajustos, esPdf: Boolean, canvia: (Ajustos) -> Unit, tanca: () -> Unit) {
    // fonts de l'usuari: fitxers .ttf/.otf de la carpeta de fonts
    val propies = remember {
        runCatching { Plataforma.llistaFitxers(Plataforma.carpetaFonts(), 2) }.getOrDefault(emptyList())
            .filter { it.nom.substringAfterLast('.').lowercase() in setOf("ttf", "otf") }.sortedBy { it.nom.lowercase() }
    }
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScrollSiCal(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Com es llegeix", color = Xoriguer.Mut, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opcio("Per pàgines", a.pagines) { canvia(a.copy(pagines = true)) }
                Opcio("Desplaçament continu", !a.pagines) { canvia(a.copy(pagines = false)) }
            }
            if (a.pagines) {
                Text("Efecte en passar pàgina", color = Xoriguer.Mut, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Efecte.entries.forEach { e -> Opcio(e.nom, a.efecte == e) { canvia(a.copy(efecte = e)) } }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Opcio("Fletxes ‹ ›", a.fletxes) { canvia(a.copy(fletxes = !a.fletxes)) }
                    Opcio("Botons de volum", a.volum) { canvia(a.copy(volum = !a.volum)) }
                    if (!esPdf) Opcio("Dues pàgines si cap", a.duesPagines) { canvia(a.copy(duesPagines = !a.duesPagines)) }
                }
                if (!esPdf) {
                    Text("Tipografia de llibre", color = Xoriguer.Mut, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Opcio("Sagnat", a.sagnat) { canvia(a.copy(sagnat = !a.sagnat)) }
                        if (Plataforma.nom == "Android") Opcio("Partir paraules (guionet)", a.guionat) { canvia(a.copy(guionat = !a.guionat)) }
                    }
                }
            }
            if (esPdf) return@Column
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
            Text("Tipus de lletra", color = Xoriguer.Mut, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opcio("Serif", a.font == "serif") { canvia(a.copy(font = "serif")) }
                Opcio("Sans", a.font == "sans") { canvia(a.copy(font = "sans")) }
                Opcio("Mono", a.font == "mono") { canvia(a.copy(font = "mono")) }
                propies.forEach { f -> Opcio(f.nom.substringBeforeLast('.'), a.font == f.cami) { canvia(a.copy(font = f.cami)) } }
            }
            Text(
                "Els meus tipus de lletra: posa fitxers .ttf o .otf a «${Plataforma.carpetaFonts()}» i torna a obrir aquest panell.",
                color = Xoriguer.Mut, fontSize = 11.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opcio("Justificat", a.justificat) { canvia(a.copy(justificat = !a.justificat)) }
                if (!a.pagines) Opcio("Passa pantalla tocant els costats", a.passaPagina) { canvia(a.copy(passaPagina = !a.passaPagina)) }
            }
        }
    }
}

@Composable
private fun Modifier.verticalScrollSiCal() = this.then(Modifier.verticalScroll(rememberScrollState()))

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
) { Text(text, color = if (actiu) Xoriguer.Fons else Xoriguer.Txt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanellIndex(caps: List<Capitol>, meves: List<Anotacio>, actual: Int, vesA: (Int) -> Unit, anotacions: Anotacions, tanca: () -> Unit) {
    val marcadors = meves.filter { it.tipus == TipusAnotacio.MARCADOR }.sortedBy { it.bloc }
    val cites = meves.filter { it.tipus == TipusAnotacio.CITA }.sortedWith(compareBy({ it.bloc }, { it.inici }))
    var pestanya by remember { mutableStateOf(if (caps.isEmpty()) 1 else 0) }
    var editant by remember { mutableStateOf<Anotacio?>(null) }
    fun ves(bloc: Int) { vesA(bloc); tanca() }
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
                        TargetaCita(a, false, { ves(a.bloc) }) { IconButton({ editant = a }) { Icon(Icons.Default.Edit, "Edita", tint = Xoriguer.Mut) } }
                    }
                }
            }
        }
    }
    editant?.let { a -> DialegEditaCita(a, { anotacions.actualitza(it); editant = null }, { anotacions.esborra(a); editant = null }) { editant = null } }
}

@Composable
private fun Buidet(t: String) = Text(t, color = Xoriguer.Mut, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
