package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.intl.LocaleList
import cat.xoriguer.lectura.Plataforma
import cat.xoriguer.lectura.formats.Guionat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cat.xoriguer.lectura.DocumentPdf
import cat.xoriguer.lectura.dades.Anotacio
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut
import cat.xoriguer.lectura.dades.TipusBloc
import cat.xoriguer.lectura.tema.TemaLectura
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Com es passa de pàgina. */
enum class Efecte(val nom: String) { GIRAR("Full de paper"), COBRIR("Cobrir"), LLISCAR("Lliscar"), CAP("Sense efecte") }

/** Qui passa les pàgines de debò: el full de paper (EstatFulles) o el pager dels altres efectes. */
interface Passador {
    val actual: Int
    suspend fun ves(p: Int, anima: Boolean)
}

class PassadorPager(private val pager: PagerState, private val efecte: Efecte) : Passador {
    override val actual get() = pager.currentPage
    override suspend fun ves(p: Int, anima: Boolean) = if (anima && efecte != Efecte.CAP) pager.animateScrollToPage(p) else pager.scrollToPage(p)
}

/** Pàgines amb l'efecte triat. `clau` canvia quan s'han de tornar a crear (pàgines noves, efecte nou). */
@Composable
fun Paginador(total: Int, inicial: Int, a: Ajustos, clau: Any, passador: (Passador) -> Unit, llom: Float = 0f, pagina: @Composable (Int) -> Unit) {
    if (a.efecte == Efecte.GIRAR) {
        val e = remember(clau) { EstatFulles(total, inicial) }
        LaunchedEffect(e) { passador(e) }
        Fulles(e, a.tema.fons, a.tema.text, llom = llom) { p -> pagina(p) }
    } else {
        val pager = remember(clau) { PagerState(inicial.coerceIn(0, (total - 1).coerceAtLeast(0))) { total } }
        val pas = remember(pager) { PassadorPager(pager, a.efecte) }
        LaunchedEffect(pas) { passador(pas) }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { p ->
            Box(Modifier.fillMaxSize().efectePagina(pager, p, a.efecte)) { pagina(p) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Navegació: el lector no ha de saber si es llegeix per pàgines o amb desplaçament continu
// ---------------------------------------------------------------------------------------------
interface Navegacio {
    /** Paràgraf (o pàgina del PDF) que hi ha a dalt de tot ara. */
    val blocActual: Int
    val fraccio: Float
    /** "Pàgina 12 de 340" o "34 %". */
    val etiqueta: String
    /** Pàgines totals i actual (per a la barra); en mode continu són paràgrafs. */
    val total: Int
    val actual: Int
    suspend fun vesABloc(bloc: Int, caracter: Int = 0)
    suspend fun vesAFraccio(f: Float)
    suspend fun seguent()
    suspend fun anterior()
    /** Fa visible un paràgraf (lectura en veu alta) sense moure's si ja es veu. */
    suspend fun segueix(bloc: Int)
    /** (posició, desplaçament) per desar el progrés. */
    fun perDesar(): Pair<Int, Int>
}

/** Desplaçament continu (com fins ara). */
class NavLlista(private val l: LazyListState, private val blocs: Int, private val pdf: Boolean) : Navegacio {
    override val blocActual get() = l.firstVisibleItemIndex
    override val fraccio: Float
        get() {
            if (blocs <= 0) return 0f
            if (!l.canScrollForward) return 1f
            val primer = l.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            val dins = if (primer.size > 0) (l.firstVisibleItemScrollOffset.toFloat() / primer.size).coerceIn(0f, 1f) else 0f
            return ((l.firstVisibleItemIndex + dins) / blocs).coerceIn(0f, 1f)
        }
    override val etiqueta get() = if (pdf) "Pàgina ${blocActual + 1} de $blocs" else "${(fraccio * 100).toInt()} %"
    override val total get() = blocs
    override val actual get() = blocActual
    override suspend fun vesABloc(bloc: Int, caracter: Int) = l.scrollToItem(bloc.coerceIn(0, (blocs - 1).coerceAtLeast(0)))
    override suspend fun vesAFraccio(f: Float) {
        if (f >= .999f) { l.scrollToItem(blocs - 1); l.scrollBy(1_000_000f); return }
        val pos = f * blocs
        val index = pos.toInt().coerceIn(0, blocs - 1)
        l.scrollToItem(index)
        val mida = l.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
        if (mida > 0) l.scrollBy((pos - index) * mida)
    }
    override suspend fun seguent() { l.animateScrollBy(l.layoutInfo.viewportSize.height * .9f) }
    override suspend fun anterior() { l.animateScrollBy(-l.layoutInfo.viewportSize.height * .9f) }
    override suspend fun segueix(bloc: Int) {
        val vis = l.layoutInfo.visibleItemsInfo
        if (vis.none { it.index == bloc } || vis.lastOrNull()?.index == bloc) l.animateScrollToItem(bloc)
    }
    override fun perDesar() = l.firstVisibleItemIndex to l.firstVisibleItemScrollOffset
}

/**
 * Per pàgines. `pagines[p]` = (paràgraf, caràcter) on comença la pàgina p. Amb el llibre obert es passen
 * dues pàgines alhora (`perFull` = 2): el passador compta fulls, no pàgines.
 */
class NavPagines(private val pas: Passador, private val pagines: List<Pair<Int, Int>>, private val pdf: Boolean, private val perFull: Int = 1) : Navegacio {
    private val inici = pagines.filterIndexed { i, _ -> i % perFull == 0 }
    private val actualP get() = pas.actual
    override val blocActual get() = inici.getOrNull(actualP)?.first ?: 0
    override val fraccio get() = if (inici.size <= 1) 1f else actualP.toFloat() / (inici.size - 1)
    override val etiqueta: String
        get() {
            val p = actualP * perFull
            return if (perFull == 2 && p + 1 < pagines.size) "Pàgines ${p + 1}–${p + 2} de ${pagines.size}" else "Pàgina ${p + 1} de ${pagines.size}"
        }
    override val total get() = inici.size
    override val actual get() = actualP

    /** Full on cau (paràgraf, caràcter). */
    fun paginaDe(bloc: Int, caracter: Int = 0) = paginaDe(pagines, bloc, caracter) / perFull
    /** Número de pàgina (no de full) on cau (paràgraf, caràcter). */
    fun numPagina(bloc: Int, caracter: Int = 0) = paginaDe(pagines, bloc, caracter)

    private suspend fun ves(p: Int, anima: Boolean) {
        pas.ves(p.coerceIn(0, inici.lastIndex.coerceAtLeast(0)), anima)
    }
    override suspend fun vesABloc(bloc: Int, caracter: Int) = ves(paginaDe(bloc, caracter), false)
    override suspend fun vesAFraccio(f: Float) = ves((f * (inici.size - 1)).toInt(), false)
    override suspend fun seguent() = ves(actualP + 1, true)
    override suspend fun anterior() = ves(actualP - 1, true)
    override suspend fun segueix(bloc: Int) {
        val p = paginaDe(bloc, 0)
        // si el paràgraf comença en aquest full o en un d'anterior que continua aquí, no cal moure's
        if (p != actualP && !(p < actualP && inici.getOrNull(actualP)?.first == bloc)) ves(p, true)
    }
    // el desplaçament es desa en negatiu (caràcter) per distingir-lo dels píxels del mode continu
    override fun perDesar() = inici.getOrNull(actualP)?.let { (b, c) -> b to -(c + 1) } ?: (0 to 0)
}

/** Última pàgina que comença abans de (bloc, caràcter) o just allà. */
fun paginaDe(inici: List<Pair<Int, Int>>, bloc: Int, caracter: Int): Int {
    var lo = 0; var hi = inici.lastIndex; var r = 0
    while (lo <= hi) {
        val m = (lo + hi) / 2
        val (b, c) = inici[m]
        if (b < bloc || (b == bloc && c <= caracter)) { r = m; lo = m + 1 } else hi = m - 1
    }
    return r
}

// ---------------------------------------------------------------------------------------------
// Paginació del text
// ---------------------------------------------------------------------------------------------
/** Un tros d'un paràgraf dins d'una pàgina (caràcters ini..fi). */
data class Tros(val bloc: Int, val ini: Int, val fi: Int)
data class Pagina(val trossos: List<Tros>)

/** Estils i espais comuns a la paginació i al dibuix (si no coincideixen, el text es tallaria). */
class EstilsPagina(a: Ajustos, familia: FontFamily, private val color: Color, val idioma: String = "ca") {
    /** Partir paraules a final de línia (català i castellà). */
    // Només a Android: el motor de text d'escriptori talla pels guions suaus però no hi dibuixa el guionet
    val guiona = a.guionat && a.justificat && (idioma == "ca" || idioma == "es") && Plataforma.nom == "Android"
    private val continua = TextStyle(color = color, fontFamily = familia, fontSize = a.mida.sp, lineHeight = a.interlineat.em,
        textAlign = if (a.justificat) TextAlign.Justify else TextAlign.Start,
        hyphens = if (guiona) Hyphens.Auto else Hyphens.None, localeList = if (guiona) LocaleList(idioma) else null)
    /** Inici de paràgraf: amb sagnat a la primera línia (com als llibres), si està triat. */
    // (el sagnat en sp i no en em: amb em, el mesurador de text d'escriptori falla)
    val paragraf = if (a.sagnat) continua.copy(textIndent = TextIndent(firstLine = (a.mida * 1.4f).sp)) else continua
    val titol = TextStyle(color = color, fontFamily = familia, fontWeight = FontWeight.Bold, fontSize = (a.mida * 1.35f).sp, lineHeight = 1.25.em, textAlign = TextAlign.Center)
    val subtitol = TextStyle(color = color, fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = (a.mida * 1.12f).sp, lineHeight = 1.3.em)
    // amb sagnat, els paràgrafs gairebé no se separen (com en un llibre imprès)
    val espaiParagraf = (a.mida * if (a.sagnat) .12f else .55f).dp
    /** `inici`: el tros comença el paràgraf (un tros que continua d'una pàgina anterior no porta sagnat). */
    fun estil(t: TipusBloc, inici: Boolean = true) = when (t) {
        TipusBloc.TITOL -> titol; TipusBloc.SUBTITOL -> subtitol
        TipusBloc.PARAGRAF -> if (inici) paragraf else continua
    }
    /** Text que es mostra d'un paràgraf (amb guions suaus si es parteixen paraules). */
    fun guionat(t: TipusBloc, text: String): Guionat? = if (guiona && t == TipusBloc.PARAGRAF) Guionat.de(text, idioma) else null
    /** Espai abans d'un tros: a dalt de la pàgina només en deixen els títols. */
    fun abans(t: TipusBloc, primer: Boolean) = when (t) {
        TipusBloc.TITOL -> if (primer) 24.dp else 32.dp
        TipusBloc.SUBTITOL -> if (primer) 0.dp else 18.dp
        TipusBloc.PARAGRAF -> 0.dp
    }
    fun despres(t: TipusBloc) = when (t) { TipusBloc.TITOL -> 16.dp; TipusBloc.SUBTITOL -> 8.dp; TipusBloc.PARAGRAF -> espaiParagraf }
}

/** Reparteix el llibre en pàgines d'`ample`×`alt` píxels. Cada capítol comença en una pàgina nova. */
fun pagina(c: Contingut, m: TextMeasurer, e: EstilsPagina, ample: Int, alt: Int, d: Density): List<Pagina> {
    val pagines = ArrayList<Pagina>()
    var actual = ArrayList<Tros>()
    var y = 0f
    val restriccio = Constraints(maxWidth = ample)
    fun tanca() { if (actual.isNotEmpty()) { pagines += Pagina(actual); actual = ArrayList(); y = 0f } }
    with(d) {
        for ((i, b) in c.blocs.withIndex()) {
            if (b.tipus == TipusBloc.TITOL) tanca()
            var ini = 0
            val text = b.text
            val g = e.guionat(b.tipus, text)
            while (ini < text.length) {
                val primer = actual.isEmpty()
                val abans = e.abans(b.tipus, primer).toPx()
                val despres = e.despres(b.tipus).toPx()
                // es mesura el text tal com es veurà (amb els guions suaus); els talls es tornen a l'original
                val hIni = g?.mostrat(ini) ?: ini
                val mostrat = g?.text ?: text
                val l: TextLayoutResult = m.measure(mostrat.substring(hIni), e.estil(b.tipus, ini == 0), constraints = restriccio)
                val lloc = alt - y - abans
                if (l.size.height <= lloc) {
                    actual += Tros(i, ini, text.length); y += abans + l.size.height + despres; break
                }
                // quantes línies hi caben
                var n = 0
                while (n < l.lineCount && l.getLineBottom(n) <= lloc) n++
                // un títol no es parteix; i no es deixa una sola línia a baix si el paràgraf és llarg (línia vídua)
                if (!primer && (n == 0 || b.tipus != TipusBloc.PARAGRAF || (n == 1 && l.lineCount > 2))) { tanca(); continue }
                if (n == 0) n = 1 // una línia més alta que la pàgina: es posa igualment
                val fi = (hIni + l.getLineEnd(n - 1)).let { h -> g?.original(h) ?: h }
                if (fi <= ini) { actual += Tros(i, ini, text.length); tanca(); break }
                actual += Tros(i, ini, fi)
                tanca()
                ini = fi
                while (ini < text.length && text[ini] == ' ') ini++
            }
        }
    }
    tanca()
    if (pagines.isEmpty()) pagines += Pagina(emptyList())
    return pagines
}

/** Cites d'un paràgraf passades a les coordenades d'un tros. */
private fun citesDelTros(cites: List<Anotacio>, t: Tros) = cites.mapNotNull { a ->
    val i = maxOf(a.inici, t.ini); val f = minOf(a.fi, t.fi)
    if (i < f) a.copy(inici = i - t.ini, fi = f - t.ini) else null
}

// ---------------------------------------------------------------------------------------------
// Vistes per pàgines
// ---------------------------------------------------------------------------------------------
/** Efecte visual de passar pàgina, aplicat a cada pàgina del pager. */
fun Modifier.efectePagina(pager: PagerState, p: Int, efecte: Efecte): Modifier {
    if (efecte == Efecte.LLISCAR || efecte == Efecte.CAP) return this
    // o > 0: la pàgina se'n va cap a l'esquerra (endavant); o < 0: és la que ve per la dreta
    fun o() = (pager.currentPage - p) + pager.currentPageOffsetFraction
    return this
        .zIndex(if ((pager.currentPage - p) + pager.currentPageOffsetFraction >= 0f) 1f else 0f)
        .graphicsLayer {
            val d = o()
            when {
                abs(d) >= 1f -> alpha = 0f
                d >= 0f -> {
                    // la pàgina de sobre: es queda al seu lloc i gira sobre el llom (esquerra) o llisca a sobre
                    if (efecte == Efecte.GIRAR) {
                        translationX = d * size.width
                        transformOrigin = TransformOrigin(0f, .5f)
                        rotationY = 90f * d
                        cameraDistance = 14f * density
                    }
                }
                else -> translationX = d * size.width // la de sota no es mou: es destapa
            }
        }
        .drawWithContent {
            drawContent()
            val d = o()
            // ombra: la pàgina que gira s'enfosqueix i la de sota surt de la penombra
            val a = if (d >= 0f) d * .45f else (1f + d) * .35f
            if (abs(d) < 1f && a > .01f) drawRect(Color.Black.copy(alpha = a))
            if (d > 0f && d < 1f && efecte == Efecte.COBRIR) {
                // vora amb ombra a la dreta de la pàgina que s'aparta
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent), startX = size.width, endX = size.width + 24f),
                    topLeft = Offset(size.width, 0f))
            }
        }
}

/** Fletxes ‹ › als costats i número de pàgina a sota (comú al text i als PDF). */
@Composable
fun MarcPagines(
    nav: Navegacio?, tema: TemaLectura, fletxes: Boolean, capitol: String?, onAnterior: () -> Unit, onSeguent: () -> Unit,
    contingut: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        contingut()
        if (nav != null && fletxes) {
            if (nav.actual > 0) BotoFletxa(Modifier.align(Alignment.CenterStart), Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Pàgina anterior", tema, onAnterior)
            if (nav.actual < nav.total - 1) BotoFletxa(Modifier.align(Alignment.CenterEnd), Icons.AutoMirrored.Filled.KeyboardArrowRight, "Pàgina següent", tema, onSeguent)
        }
        if (nav != null) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(capitol.orEmpty(), color = tema.suau, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(nav.etiqueta, color = tema.suau, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BotoFletxa(m: Modifier, icona: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tema: TemaLectura, f: () -> Unit) =
    IconButton(f, m.padding(horizontal = 2.dp).size(44.dp).background(tema.suau.copy(alpha = .12f), CircleShape)) {
        Icon(icona, desc, tint = tema.suau.copy(alpha = .8f), modifier = Modifier.size(30.dp))
    }

/** Llibre de text per pàgines. Avisa amb `nav` quan la paginació està llesta. */
@Composable
fun TextPaginat(
    c: Contingut, a: Ajustos, idioma: String, cites: Map<Int, List<Anotacio>>, llegint: Int?,
    inicial: Pair<Int, Int>, nav: (NavPagines?) -> Unit, mantePremut: (Int) -> Unit, toca: (Float) -> Unit,
) {
    val m = rememberTextMeasurer(cacheSize = 0)
    val familia = remember(a.font) { familiaDe(a.font) }
    // per mesurar només compta el que canvia la mida del text; el color (tema) només es fa servir per dibuixar
    val mesura = remember(a.mida, a.interlineat, a.justificat, a.sagnat, a.guionat, familia, idioma) { EstilsPagina(a, familia, Color.Black, idioma) }
    val estils = remember(mesura, a.tema) { EstilsPagina(a, familia, a.tema.text, idioma) }
    val densitat = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ampleTot = constraints.maxWidth.toFloat()
        // Llibre obert: dues pàgines quan la pantalla és ampla i apaïsada (ordinador, tauleta o mòbil girat)
        val doble = a.duesPagines && maxWidth > maxHeight && maxWidth >= 840.dp
        val perFull = if (doble) 2 else 1
        val ampleFull = maxWidth / perFull
        val margeLat = a.marge.dp + if (doble) 28.dp else 36.dp // espai per a les fletxes
        val maxAmple = (a.mida * 44).dp
        val ampleText = (ampleFull - margeLat * 2).coerceAtMost(maxAmple).coerceAtLeast(120.dp)
        val dalt = 40.dp; val baix = 40.dp
        val altText = (maxHeight - dalt - baix).coerceAtLeast(120.dp)
        val ampPx = with(densitat) { ampleText.roundToPx() }
        val altPx = with(densitat) { altText.roundToPx() } - 6 // marge per arrodoniments
        // es tornen a fer les pàgines si canvia la mida de la pantalla o la lletra
        val pagines by produceState<List<Pagina>?>(null, c, mesura, ampPx, altPx) {
            value = null
            value = withContext(Dispatchers.Default) { pagina(c, m, mesura, ampPx, altPx, densitat) }
        }
        val pp = pagines
        if (pp == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = a.tema.suau)
                Text("Preparant les pàgines…", color = a.tema.suau, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            return@BoxWithConstraints
        }
        val inici = remember(pp) { pp.map { p -> p.trossos.firstOrNull()?.let { it.bloc to it.ini } ?: (Int.MAX_VALUE to 0) } }
        // on érem es llegeix en el moment de refer les pàgines (es manté en canviar la lletra o girar la pantalla)
        val onEra by rememberUpdatedState(inicial)
        var pas by remember(pp, perFull) { mutableStateOf<Passador?>(null) }
        val nv = remember(pas, inici, perFull) { pas?.let { NavPagines(it, inici, false, perFull) } }
        LaunchedEffect(nv) { nav(nv) }
        DisposableEffect(Unit) { onDispose { nav(null) } }

        @Composable
        fun Pagina(p: Int, xFull: Float) {
            Box(Modifier.fillMaxHeight().width(ampleFull).pointerInput(xFull) { detectTapGestures(onTap = { toca((xFull + it.x) / ampleTot) }) }) {
                if (p >= pp.size) return@Box
                Column(Modifier.align(Alignment.TopCenter).padding(top = dalt).width(ampleText).height(altText)) {
                    pp[p].trossos.forEachIndexed { k, t ->
                        val b = c.blocs[t.bloc]
                        val g = remember(t.bloc, mesura) { estils.guionat(b.tipus, b.text) }
                        val hIni = g?.mostrat(t.ini) ?: t.ini
                        val hFi = g?.mostrat(t.fi) ?: t.fi
                        val meves = remember(cites, t, g) {
                            citesDelTros(cites[t.bloc].orEmpty(), t).map { x ->
                                if (g == null) x else x.copy(inici = g.mostrat(t.ini + x.inici) - hIni, fi = g.mostrat(t.ini + x.fi) - hIni)
                            }
                        }
                        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                        Text(
                            ambCites((g?.text ?: b.text).substring(hIni, hFi), meves),
                            style = estils.estil(b.tipus, t.ini == 0),
                            onTextLayout = { layout = it },
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = estils.abans(b.tipus, k == 0), bottom = estils.despres(b.tipus))
                                .then(if (llegint == t.bloc) Modifier.background(cat.xoriguer.lectura.tema.Xoriguer.Taronja.copy(alpha = .13f)) else Modifier)
                                .drawBehind { pintaCites(layout, meves, a.tema) }
                                .pointerInput(t.bloc, xFull) {
                                    detectTapGestures(
                                        onTap = { o -> toca((xFull + (ampleFull.toPx() - size.width) / 2f + o.x) / ampleTot) },
                                        onLongPress = { mantePremut(t.bloc) },
                                    )
                                },
                        )
                    }
                }
            }
        }

        val fulls = (pp.size + perFull - 1) / perFull
        Paginador(fulls, paginaDe(inici, onEra.first, onEra.second) / perFull, a, Triple(pp, a.efecte, perFull), { pas = it }, llom = if (doble) .5f else 0f) { f ->
            Row(
                Modifier.fillMaxSize().background(a.tema.fons).then(
                    // el llom del llibre obert: una ombra suau al mig
                    if (doble) Modifier.drawBehind {
                        val mig = size.width / 2
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .10f), Color.Black.copy(alpha = .22f)), startX = mig - 46f, endX = mig), topLeft = Offset(mig - 46f, 0f), size = androidx.compose.ui.geometry.Size(46f, size.height))
                        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .22f), Color.Black.copy(alpha = .08f), Color.Transparent), startX = mig, endX = mig + 46f), topLeft = Offset(mig, 0f), size = androidx.compose.ui.geometry.Size(46f, size.height))
                    } else Modifier,
                ),
            ) {
                Pagina(f * perFull, 0f)
                if (doble) Pagina(f * perFull + 1, ampleTot / 2)
            }
        }
    }
}

/** PDF o còmic per pàgines: cada pàgina sencera a la pantalla. */
@Composable
fun DocumentPaginat(doc: DocumentPdf, a: Ajustos, inicial: Int, nav: (NavPagines?) -> Unit, toca: (Float) -> Unit) {
    val onEra by rememberUpdatedState(inicial)
    var pas by remember(doc) { mutableStateOf<Passador?>(null) }
    val nv = remember(pas) { pas?.let { NavPagines(it, List(doc.pagines) { i -> i to 0 }, true) } }
    LaunchedEffect(nv) { nav(nv) }
    DisposableEffect(Unit) { onDispose { nav(null) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ample = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2400)
        Paginador(doc.pagines, onEra, a, doc to a.efecte, { pas = it }) { p ->
            Box(
                Modifier.fillMaxSize().background(a.tema.fons)
                    .pointerInput(Unit) { detectTapGestures(onTap = { toca(it.x / size.width) }) },
                contentAlignment = Alignment.Center,
            ) {
                val img by produceState<ImageBitmap?>(null, p, ample) { value = doc.pagina(p, ample) }
                img?.let { Image(it, "Pàgina ${p + 1}", Modifier.fillMaxSize().padding(bottom = 28.dp), contentScale = ContentScale.Fit) }
                    ?: CircularProgressIndicator(color = a.tema.suau)
            }
        }
    }
}

/** Títol del capítol on és un paràgraf. */
fun capitolDe(caps: List<Capitol>, bloc: Int): String? = caps.lastOrNull { it.bloc <= bloc }?.titol
