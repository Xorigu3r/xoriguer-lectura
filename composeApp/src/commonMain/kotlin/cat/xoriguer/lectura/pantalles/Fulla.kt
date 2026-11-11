package cat.xoriguer.lectura.pantalles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Passar pàgina com un full de paper de veritat: la pàgina es doblega des de la cantonada.
 *
 * Geometria (el "page curl" clàssic en 2D): C és la cantonada que s'agafa i P on és ara. La línia del plec és la
 * mediatriu de C–P. El tros de pàgina que queda del costat de C es plega i es veu reflectit a l'altre costat de
 * la línia (és el revers del full); a sota apareix la pàgina següent.
 */
class EstatFulles(pagines: Int, inicial: Int) : Passador {
    var total by mutableIntStateOf(pagines)
    override var actual by mutableIntStateOf(inicial.coerceIn(0, (pagines - 1).coerceAtLeast(0)))
        private set
    /** 1 = passant endavant (es doblega l'actual), -1 = enrere (torna la d'abans), 0 = quiet. */
    var direccio by mutableIntStateOf(0)
        private set
    /** On és la cantonada del full que es mou. */
    var punt by mutableStateOf(Offset.Zero)
        private set
    /** Cantonada agafada: a baix (true) o a dalt. */
    var baix by mutableStateOf(true)
        private set
    var mida by mutableStateOf(Offset(1f, 1f)) // amplada, alçada en píxels
    /** On és el llom, en fracció de l'amplada: 0 = a l'esquerra (una pàgina), 0.5 = al mig (llibre obert). */
    var llom by mutableFloatStateOf(0f)
    private val t = Animatable(0f)
    private var arrossega: Offset? = null

    private val w get() = mida.x
    private val h get() = mida.y
    private val lx get() = llom * w
    private val cantonada get() = Offset(w, if (baix) h else 0f)
    /** Full girat del tot: la cantonada ha passat a l'altra banda del llom. */
    private val girada get() = Offset(2 * lx - w, if (baix) h else 0f)

    /** Camí automàtic (fletxes, tecles, tocs): la cantonada s'aixeca en arc i va cap a l'esquerra. */
    private fun cami(x: Float): Offset {
        val alca = h * .12f * sin(PI.toFloat() * x)
        return limita(Offset(w - 2 * (w - lx) * x, if (baix) h - alca else alca))
    }

    /** El full està cosit al llom (esquerra): la cantonada no es pot allunyar més que l'amplada de la pàgina. */
    private fun limita(p: Offset): Offset {
        val llomP = Offset(lx, if (baix) h else 0f)
        val d = p - llomP
        val l = d.getDistance()
        val ample = w - lx
        var q = if (l > ample) llomP + d * (ample / l) else p
        // ni més enllà de la diagonal des de l'altre extrem del llom
        val altre = Offset(lx, if (baix) 0f else h)
        val diag = hypot(ample, h)
        val e = q - altre
        if (e.getDistance() > diag) q = altre + e * (diag / e.getDistance())
        return q
    }

    override suspend fun ves(p: Int, anima: Boolean) {
        val q = p.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (!anima || q == actual || kotlin.math.abs(q - actual) > 1) { direccio = 0; actual = q; return }
        if (direccio != 0) return
        baix = true
        if (q > actual) {
            direccio = 1; t.snapTo(0f)
            t.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) { punt = cami(value) }
            actual = q
        } else {
            direccio = -1; t.snapTo(1f); punt = cami(1f)
            t.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) { punt = cami(value) }
            actual = q
        }
        direccio = 0
    }

    // ---- amb el dit o el ratolí ----
    fun comenca(o: Offset) {
        if (direccio != 0) return
        baix = o.y > h / 2
        arrossega = o
        // una pàgina: meitat dreta endavant, esquerra enrere; llibre obert: pàgina dreta endavant, esquerra enrere
        val partio = if (llom > 0f) lx else w / 2
        if (o.x > partio && actual < total - 1) { direccio = 1; punt = cantonada }
        else if (o.x <= partio && actual > 0) { direccio = -1; punt = girada }
        else arrossega = null
    }

    fun mou(o: Offset) {
        val ini = arrossega ?: return
        punt = if (direccio == 1) limita(cantonada + (o - ini))
        // enrere: el plec segueix el dit (el full ve des de l'esquerra)
        else limita(Offset(2 * o.x - w, (if (baix) h else 0f) + (o.y - ini.y) * .5f))
    }

    suspend fun acaba() {
        if (arrossega == null) return
        arrossega = null
        // fins on ha arribat, de 0 (sense girar) a 1 (girat del tot)
        val fet = ((w - punt.x) / (2 * (w - lx))).coerceIn(0f, 1f)
        val passa = if (direccio == 1) fet > .25f else fet < .75f
        val desti = if (passa == (direccio == 1)) girada else cantonada
        val anim = Animatable(punt, Offset.VectorConverter)
        anim.animateTo(desti, tween(350, easing = FastOutSlowInEasing)) { punt = limita(value) }
        if (passa) actual += direccio
        direccio = 0
    }
}

/**
 * Mostra la pàgina `actual` i, mentre es passa, el full que es doblega. Les pàgines es dibuixen en capes
 * (GraphicsLayer) i es componen a mà; a sota hi ha el contingut de veritat (per tocar i mantenir premut).
 */
@Composable
fun Fulles(estat: EstatFulles, paper: Color, tinta: Color, modifier: Modifier = Modifier, llom: Float = 0f, pagina: @Composable (Int) -> Unit) {
    estat.llom = llom
    val anterior = rememberGraphicsLayer()
    val actual = rememberGraphicsLayer()
    val seguent = rememberGraphicsLayer()
    val tot = rememberGraphicsLayer()
    BoxWithConstraints(
        modifier.fillMaxSize()
            .pointerInput(estat) {
                coroutineScope {
                    detectDragGestures(
                        onDragStart = { estat.comenca(it) },
                        onDrag = { c, _ -> c.consume(); estat.mou(c.position) },
                        onDragEnd = { launch { estat.acaba() } },
                        onDragCancel = { launch { estat.acaba() } },
                    )
                }
            }
            .drawWithContent {
                estat.mida = Offset(size.width, size.height)
                // fa que cada pàgina es gravi a la seva capa (no es dibuixen directament)
                tot.record { this@drawWithContent.drawContent() }
                val revers = lerp(paper, tinta, .06f)
                when (estat.direccio) {
                    0 -> drawLayer(actual)
                    1 -> fullaDoblegada(actual, seguent, estat.punt, Offset(size.width, if (estat.baix) size.height else 0f), revers, llom * size.width)
                    else -> fullaDoblegada(anterior, actual, estat.punt, Offset(size.width, if (estat.baix) size.height else 0f), revers, llom * size.width)
                }
            },
    ) {
        val a = estat.actual
        // ordre: les de sota primer; l'actual l'última perquè sigui la que rep els tocs
        if (a > 0) key(a - 1) { Box(Modifier.fillMaxSize().drawWithContent { anterior.record { this@drawWithContent.drawContent() } }) { pagina(a - 1) } }
        if (a < estat.total - 1) key(a + 1) { Box(Modifier.fillMaxSize().drawWithContent { seguent.record { this@drawWithContent.drawContent() } }) { pagina(a + 1) } }
        key(a) { Box(Modifier.fillMaxSize().drawWithContent { actual.record { this@drawWithContent.drawContent() } }) { pagina(a) } }
    }
}

/** Dibuixa la pàgina A doblegant-se cap a P (des de la cantonada C) sobre la pàgina B. */
fun DrawScope.fullaDoblegada(a: GraphicsLayer, b: GraphicsLayer, p: Offset, c: Offset, revers: Color, llom: Float = 0f) {
    val w = size.width; val h = size.height
    val cp = c - p
    val dist = cp.getDistance()
    if (dist < 1f) { drawLayer(a); return }
    val n = cp / dist                       // normal del plec, cap a la cantonada
    val m = (p + c) / 2f                    // un punt del plec
    val rect = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    fun costat(x: Offset) = (x - m).x * n.x + (x - m).y * n.y // > 0: part que es plega
    val davant = retalla(rect) { costat(it) <= 0f }
    val plec = retalla(rect) { costat(it) > 0f }
    fun reflex(x: Offset): Offset { val d = costat(x); return x - n * (2f * d) }
    val darrere = plec.map(::reflex)
    fun cami(punts: List<Offset>) = Path().apply { punts.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }

    // 1. la pàgina de sota
    drawLayer(b)
    // 2. ombra que fa el full sobre la de sota, al llarg del plec (més ampla com més aixecat)
    val ombra = min(dist * .35f, w * .12f).coerceAtLeast(6f)
    if (plec.size >= 3) drawPath(
        cami(plec),
        Brush.linearGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent), start = m, end = m + n * ombra),
    )
    // 3. la part del full que encara és plana
    if (davant.size >= 3) clipPath(cami(davant)) {
        drawLayer(a)
        // el paper es corba cap al plec: s'enfosqueix una mica abans de doblegar-se
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .16f)), start = m - n * (ombra * 1.4f), end = m))
    }
    // 4. el revers del full (la part doblegada, reflectida)
    if (darrere.size >= 3) {
        val camiDarrere = cami(darrere)
        clipPath(camiDarrere) {
            // el text de l'altra cara es transparenta, al revés
            val dMn = m.x * n.x + m.y * n.y
            val reflexio = Matrix().apply {
                values[Matrix.ScaleX] = 1 - 2 * n.x * n.x; values[Matrix.SkewX] = -2 * n.x * n.y; values[Matrix.TranslateX] = 2 * dMn * n.x
                values[Matrix.SkewY] = -2 * n.x * n.y; values[Matrix.ScaleY] = 1 - 2 * n.y * n.y; values[Matrix.TranslateY] = 2 * dMn * n.y
            }
            if (llom > 0f) {
                // Llibre obert: el revers del full és la pàgina esquerra del full següent (B). Un punt X del full
                // (pàgina dreta d'A) cau a R(X) i hi mostra el punt de B simètric respecte del llom: S = R·M.
                val mirall = Matrix().apply { values[Matrix.ScaleX] = -1f; values[Matrix.TranslateX] = 2 * llom }
                // (Matrix de Compose: a.timesAssign(b) aplica primer a i després b; aquí cal primer el mirall i després la reflexió)
                val t = Matrix(mirall.values.copyOf()).apply { timesAssign(reflexio) }
                withTransform({ transform(t) }) { drawLayer(b) }
            } else {
                // Una sola pàgina: el revers és paper, amb el text de l'altra cara que s'hi transparenta
                withTransform({ transform(reflexio) }) { drawLayer(a) }
                drawRect(revers.copy(alpha = .9f))
            }
            // volum del paper: fosc tocant al plec, llum al mig, una mica fosc a la vora
            val ample = dist / 2f
            drawRect(
                Brush.linearGradient(
                    0f to Color.Black.copy(alpha = .22f), .18f to Color.White.copy(alpha = .10f),
                    .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .08f),
                    start = m, end = m - n * ample,
                ),
            )
        }
    }
}

/** Retalla un polígon convex amb un semiplà (Sutherland–Hodgman amb una sola vora). */
private fun retalla(poligon: List<Offset>, dins: (Offset) -> Boolean): List<Offset> {
    val r = ArrayList<Offset>()
    for (i in poligon.indices) {
        val a = poligon[i]; val b = poligon[(i + 1) % poligon.size]
        val ia = dins(a); val ib = dins(b)
        if (ia) r += a
        if (ia != ib) {
            // punt de tall: es busca amb bisecció (el semiplà és una funció lineal; n'hi ha prou amb 24 passos)
            var lo = 0f; var hi = 1f
            repeat(24) { val mid = (lo + hi) / 2; if (dins(a + (b - a) * mid) == ia) lo = mid else hi = mid }
            r += a + (b - a) * ((lo + hi) / 2)
        }
    }
    return r
}
