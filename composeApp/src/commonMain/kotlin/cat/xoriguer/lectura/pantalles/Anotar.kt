package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import cat.xoriguer.lectura.opcioMenuText
import cat.xoriguer.lectura.dades.Anotacio
import cat.xoriguer.lectura.dades.ColorCita
import cat.xoriguer.lectura.dades.TipusAnotacio
import cat.xoriguer.lectura.tema.TemaLectura
import cat.xoriguer.lectura.tema.Xoriguer

/** Text d'un paràgraf amb les cites que tenen nota subratllades. El color de fons el pinta [pintaCites]. */
fun ambCites(text: String, cites: List<Anotacio>): AnnotatedString {
    if (cites.none { it.teNota }) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        cites.filter { it.teNota }.forEach { c ->
            val a = c.inici.coerceIn(0, text.length); val b = c.fi.coerceIn(a, text.length)
            if (a < b) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b)
        }
    }
}

/**
 * Pinta el fons de les cites sota el text. Es fa a mà (i no amb SpanStyle.background) perquè
 * el fons de cada línia cobreixi tota l'alçada de la línia: si no, queden ratlles entre línies.
 */
fun DrawScope.pintaCites(layout: TextLayoutResult?, cites: List<Anotacio>, tema: TemaLectura) {
    if (layout == null || cites.isEmpty()) return
    val fosc = tema == TemaLectura.NIT || tema == TemaLectura.GRIS
    val n = layout.layoutInput.text.length
    cites.forEach { c ->
        val a = c.inici.coerceIn(0, n); val b = c.fi.coerceIn(a, n)
        if (a >= b) return@forEach
        val color = lerp(tema.fons, c.color.color, if (fosc) .38f else .55f)
        // Un rectangle per línia, en píxels sencers i encavalcats 1 px: així no queden ratlles entre línies
        for (linia in layout.getLineForOffset(a)..layout.getLineForOffset(b - 1)) {
            val s = maxOf(a, layout.getLineStart(linia))
            val e = minOf(b, layout.getLineEnd(linia, visibleEnd = true))
            if (s >= e) continue
            val x1 = floor(layout.getBoundingBox(s).left)
            // Si la cita continua a la línia següent, arriba fins al marge (el text justificat no acaba sempre al mateix píxel)
            val seguix = e == layout.getLineEnd(linia, visibleEnd = true) && b > e
            val x2 = if (seguix) size.width else ceil(layout.getBoundingBox(e - 1).right)
            val y1 = floor(layout.getLineTop(linia))
            val y2 = ceil(layout.getLineBottom(linia)) + 1f
            drawRect(color, Offset(x1, y1), Size(x2 - x1, y2 - y1))
        }
    }
}

/** La paraula que hi ha al caràcter `i` (lletres, xifres, punt volat i guionet; l'apòstrof separa: «l'estufa» → «estufa»). */
fun paraulaA(text: String, i: Int?): TextRange? {
    if (i == null || text.isEmpty()) return null
    fun lletra(c: Char) = c.isLetterOrDigit() || c == '·' || c == '-'
    var a = i.coerceIn(0, text.length - 1)
    if (!lletra(text[a]) && a > 0 && lletra(text[a - 1])) a--
    if (!lletra(text[a])) return null
    var b = a
    while (a > 0 && lletra(text[a - 1])) a--
    while (b < text.length && lletra(text[b])) b++
    return TextRange(a, b)
}

/** Selector de colors (cercles). */
@Composable
fun Colors(actual: ColorCita?, mida: Int = 34, tria: (ColorCita) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorCita.entries.forEach { c ->
            Box(
                Modifier.size(mida.dp).background(c.color, CircleShape)
                    .border(if (c == actual) 3.dp else 0.dp, if (c == actual) Xoriguer.Crema else c.color, CircleShape)
                    .clickable { tria(c) },
            )
        }
    }
}

/**
 * Panell que s'obre en mantenir premut un paràgraf: seleccionar el tros, ressaltar-lo amb un color,
 * afegir-hi una nota, copiar-lo; i veure, editar o esborrar les cites que ja hi ha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun PanellCita(
    /** Tots els paràgrafs del llibre (per poder allargar la cita a l'anterior o al següent). */
    paragrafs: List<String>,
    /** Paràgraf on s'ha mantingut premut. */
    blocInicial: Int,
    existents: List<Anotacio>,
    /** (paràgraf on comença, caràcter, paràgraf on acaba, caràcter, text, color, nota) */
    desa: (blocIni: Int, inici: Int, blocFi: Int, fi: Int, text: String, color: ColorCita, nota: String) -> Unit,
    actualitza: (Anotacio) -> Unit,
    esborra: (Anotacio) -> Unit,
    buscaParaula: ((String) -> Unit)? = null,
    llegeixDesDaqui: (() -> Unit)? = null,
    /** Caràcter on s'ha mantingut premut: se selecciona la paraula d'allà (si no, tot el paràgraf). */
    puntInicial: Int? = null,
    tanca: () -> Unit,
) {
    val porta = LocalClipboardManager.current
    // tros de paràgrafs que es mostren (es pot allargar), separats per una línia en blanc
    var desDe by remember { mutableIntStateOf(blocInicial) }
    var finsA by remember { mutableIntStateOf(blocInicial) }
    val sep = "\n\n"
    var valor by remember {
        val t = paragrafs[blocInicial]
        mutableStateOf(TextFieldValue(t, paraulaA(t, puntInicial) ?: TextRange(0, t.length)))
    }
    val text = valor.text
    /** Inici de cada paràgraf mostrat dins de `text`. */
    fun inicis(): List<Int> { var x = 0; return (desDe..finsA).map { b -> x.also { x += paragrafs[b].length + sep.length } } }
    /** Caràcter de `text` → (paràgraf, caràcter dins del paràgraf). */
    fun aParagraf(o: Int, esFinal: Boolean): Pair<Int, Int> {
        val ii = inicis()
        var k = ii.indexOfLast { it <= o }.coerceAtLeast(0)
        // un final just a l'inici d'un paràgraf vol dir el final de l'anterior
        if (esFinal && k > 0 && o - ii[k] <= 0) { k--; return (desDe + k) to paragrafs[desDe + k].length }
        val b = desDe + k
        return b to (o - ii[k]).coerceIn(0, paragrafs[b].length)
    }
    fun afegeixAnterior() {
        if (desDe <= 0) return
        val afegit = paragrafs[desDe - 1].length + sep.length
        desDe--
        val nou = paragrafs[desDe] + sep + valor.text
        valor = TextFieldValue(nou, TextRange(valor.selection.start + afegit, valor.selection.end + afegit))
    }
    fun afegeixSeguent() {
        if (finsA >= paragrafs.lastIndex) return
        finsA++
        valor = valor.copy(text = valor.text + sep + paragrafs[finsA])
    }
    var color by remember { mutableStateOf(ColorCita.GROC) }
    var nota by remember { mutableStateOf("") }
    var ambNota by remember { mutableStateOf(false) }
    var editant by remember { mutableStateOf<Anotacio?>(null) }

    fun rang(): Pair<Int, Int> {
        val s = valor.selection
        return if (s.collapsed) 0 to text.length else minOf(s.start, s.end) to maxOf(s.start, s.end)
    }

    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Selecciona el tros que vols guardar", color = Xoriguer.Mut, fontSize = 12.sp)
            // allargar la cita a l'altre paràgraf (una cita pot ocupar-ne uns quants)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (desDe > 0) TextButton({ afegeixAnterior() }) { Text("↑ Afegeix el paràgraf anterior", color = Xoriguer.Crema, fontSize = 13.sp) }
                if (finsA < paragrafs.lastIndex) TextButton({ afegeixSeguent() }) { Text("↓ Afegeix el següent", color = Xoriguer.Crema, fontSize = 13.sp) }
            }
            BasicTextField(
                valor, { valor = it.copy(text = valor.text) }, readOnly = true,
                textStyle = TextStyle(color = Xoriguer.Txt, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp),
                cursorBrush = SolidColor(Xoriguer.Taronja),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Xoriguer.Fons, RoundedCornerShape(8.dp)).padding(12.dp)
                    // «Diccionari» també al menú del sistema (al costat de Copia / Tradueix)
                    .then(if (buscaParaula != null) Modifier.opcioMenuText("Diccionari") {
                        val (a, b) = rang(); text.substring(a, b).trim().takeIf { it.isNotEmpty() }?.let { buscaParaula(it); tanca() }
                    } else Modifier),
            )
            // Diccionari ben a la vista quan hi ha seleccionada una paraula (o una expressió curta)
            val (a0, b0) = rang()
            val tros = text.substring(a0, b0).trim()
            if (buscaParaula != null && !valor.selection.collapsed && tros.isNotEmpty() && tros.split(Regex("\\s+")).size <= 3)
                FilledTonalButton({ buscaParaula(tros); tanca() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null); Spacer(Modifier.width(8.dp))
                    Text("Diccionari: «$tros»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            Colors(color) { color = it }
            if (ambNota) OutlinedTextField(
                nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("Nota") }, minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja, focusedLabelColor = Xoriguer.Taronja),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    {
                        val (a, b) = rang()
                        val (bi, oi) = aParagraf(a, false); val (bf, of) = aParagraf(b, true)
                        desa(bi, oi, bf, of, text.substring(a, b).trim(), color, nota.trim()); tanca()
                    },
                    colors = ButtonDefaults.buttonColors(Xoriguer.Taronja, Xoriguer.Fons),
                ) { Icon(Icons.Default.FormatQuote, null); Spacer(Modifier.width(6.dp)); Text(if (ambNota && nota.isNotBlank()) "Desa la nota" else "Ressalta", fontWeight = FontWeight.Bold) }
                if (!ambNota) OutlinedButton({ ambNota = true }) { Icon(Icons.Default.EditNote, null, tint = Xoriguer.Crema); Spacer(Modifier.width(6.dp)); Text("Nota", color = Xoriguer.Crema) }
                IconButton({ val (a, b) = rang(); porta.setText(AnnotatedString(text.substring(a, b))); tanca() }) {
                    Icon(Icons.Default.ContentCopy, "Copia", tint = Xoriguer.Crema)
                }
                if (llegeixDesDaqui != null) IconButton({ llegeixDesDaqui(); tanca() }) { Icon(Icons.Default.Headphones, "Llegeix en veu alta des d'aquí", tint = Xoriguer.Crema) }
            }
            if (buscaParaula != null && valor.selection.collapsed)
                Text("Per buscar una paraula al diccionari, selecciona-la.", color = Xoriguer.Mut, fontSize = 11.sp)

            if (existents.isNotEmpty()) {
                HorizontalDivider(color = Xoriguer.Ui3)
                Text("En aquest paràgraf", color = Xoriguer.Mut, fontSize = 12.sp)
                existents.filter { it.tipus == TipusAnotacio.CITA }.forEach { a ->
                    TargetaCita(a, mostraLlibre = false, onClick = { editant = a }) {
                        IconButton({ esborra(a) }) { Icon(Icons.Default.DeleteOutline, "Esborra", tint = Xoriguer.Mut) }
                    }
                }
            }
        }
    }

    editant?.let { a -> DialegEditaCita(a, { actualitza(it); editant = null }, { esborra(a); editant = null }) { editant = null } }
}

/** Targeta d'una cita, nota o marcador (al lector i a la secció "Cites i notes"). */
@Composable
fun TargetaCita(a: Anotacio, mostraLlibre: Boolean, onClick: () -> Unit, accions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Xoriguer.Fons.copy(alpha = .55f), RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (a.tipus == TipusAnotacio.MARCADOR) Xoriguer.Taronja else a.color.color, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (a.tipus == TipusAnotacio.MARCADOR) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmark, null, tint = Xoriguer.Taronja, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.text, color = Xoriguer.Txt, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else Text("«${a.text}»", color = Xoriguer.Txt, fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            if (a.teNota) Row {
                Icon(Icons.Default.EditNote, null, tint = Xoriguer.Taronja, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.nota, color = Xoriguer.Crema, fontStyle = FontStyle.Italic, fontSize = 13.sp)
            }
            Text(
                (if (mostraLlibre) "${a.titolLlibre} · " else "") + "${(a.percent * 100).toInt()} % · ${a.dataCurta()}",
                color = Xoriguer.Mut, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.align(Alignment.CenterVertically), content = accions)
    }
}

@Composable
fun DialegEditaCita(a: Anotacio, desa: (Anotacio) -> Unit, esborra: () -> Unit, tanca: () -> Unit) {
    var nota by remember { mutableStateOf(a.nota) }
    var color by remember { mutableStateOf(a.color) }
    AlertDialog(
        onDismissRequest = tanca,
        containerColor = Xoriguer.Ui,
        title = { Text(if (a.tipus == TipusAnotacio.MARCADOR) "Marcador" else "Cita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (a.tipus == TipusAnotacio.MARCADOR) a.text else "«${a.text}»", fontFamily = FontFamily.Serif, color = Xoriguer.Txt, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (a.tipus == TipusAnotacio.CITA) Colors(color, 30) { color = it }
                OutlinedTextField(
                    nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("Nota") }, minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja, focusedLabelColor = Xoriguer.Taronja),
                )
            }
        },
        confirmButton = { TextButton({ desa(a.copy(nota = nota.trim(), color = color)) }) { Text("Desa", color = Xoriguer.Taronja) } },
        dismissButton = { TextButton(esborra) { Text("Esborra", color = Xoriguer.Mut) } },
    )
}
