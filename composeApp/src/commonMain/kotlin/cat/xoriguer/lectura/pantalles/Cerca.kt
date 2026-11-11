package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.dades.Capitol
import cat.xoriguer.lectura.dades.Contingut
import cat.xoriguer.lectura.tema.Xoriguer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Una aparició del text buscat: paràgraf i caràcters. */
data class Troballa(val bloc: Int, val ini: Int, val fi: Int)

object CercaText {
    private const val AMB = "àáâäãèéêëìíîïòóôöõùúûüçñ"
    private const val SENSE = "aaaaaeeeeiiiiooooouuuucn"

    /** Minúscules i sense accents, lletra per lletra (els índexs no canvien). */
    fun normalitza(s: String): String = buildString(s.length) {
        for (c in s) { val l = c.lowercaseChar(); val i = AMB.indexOf(l); append(if (i >= 0) SENSE[i] else l) }
    }

    fun cerca(c: Contingut, q: String, maxim: Int = 500): List<Troballa> {
        val n = normalitza(q.trim())
        if (n.length < 2) return emptyList()
        val r = ArrayList<Troballa>()
        for ((i, b) in c.blocs.withIndex()) {
            val t = normalitza(b.text)
            var p = t.indexOf(n)
            while (p >= 0) {
                r += Troballa(i, p, p + n.length)
                if (r.size >= maxim) return r
                p = t.indexOf(n, p + n.length)
            }
        }
        return r
    }
}

/** Panell de cerca dins del llibre: cada resultat amb el seu context i la pàgina (o el capítol). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanellCerca(c: Contingut, capitols: List<Capitol>, paginaDe: ((Troballa) -> Int)?, inicial: String, ves: (Troballa, String) -> Unit, tanca: () -> Unit) {
    var q by remember { mutableStateOf(inicial) }
    var resultats by remember { mutableStateOf<List<Troballa>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        delay(250) // s'espera que s'acabi d'escriure
        resultats = withContext(Dispatchers.Default) { CercaText.cerca(c, q) }
    }
    ModalBottomSheet(tanca, containerColor = Xoriguer.Ui, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text("Cerca dins del llibre") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Xoriguer.Taronja, cursorColor = Xoriguer.Taronja),
            )
            val r = resultats
            Text(
                when {
                    q.trim().length < 2 -> "Escriu almenys dues lletres. No cal posar els accents."
                    r == null -> "Cercant…"
                    r.isEmpty() -> "No hi surt enlloc."
                    r.size >= 500 -> "Més de 500 resultats (es mostren els primers)."
                    else -> "${r.size} resultat${if (r.size == 1) "" else "s"}"
                },
                color = Xoriguer.Mut, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(resultats.orEmpty()) { t ->
                val text = c.blocs[t.bloc].text
                val a = (t.ini - 50).coerceAtLeast(0); val b = (t.fi + 70).coerceAtMost(text.length)
                Column(Modifier.fillMaxWidth().clickable { ves(t, q) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        listOfNotNull(paginaDe?.let { "Pàgina ${it(t) + 1}" }, capitolDe(capitols, t.bloc)).joinToString(" · "),
                        color = Xoriguer.Taronja, fontSize = 11.sp,
                    )
                    Text(
                        buildAnnotatedString {
                            if (a > 0) append("…")
                            append(text.substring(a, t.ini))
                            withStyle(SpanStyle(color = Xoriguer.Fons, background = Xoriguer.Taronja, fontWeight = FontWeight.Bold)) { append(text.substring(t.ini, t.fi)) }
                            append(text.substring(t.fi, b))
                            if (b < text.length) append("…")
                        },
                        color = Xoriguer.Txt, fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3,
                    )
                }
            }
        }
    }
}
