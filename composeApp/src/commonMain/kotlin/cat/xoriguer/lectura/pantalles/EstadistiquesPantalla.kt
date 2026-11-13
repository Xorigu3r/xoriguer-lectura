package cat.xoriguer.lectura.pantalles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.xoriguer.lectura.dades.Biblioteca
import cat.xoriguer.lectura.dades.Calendari
import cat.xoriguer.lectura.dades.Llibre
import cat.xoriguer.lectura.tema.Xoriguer

/** "1 h 25 min", "12 min", "0 min". */
fun durada(segons: Long): String {
    val m = segons / 60
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** Temps de lectura, pàgines per dia, ratxa, objectiu diari i llibres acabats. */
@Composable
fun EstadistiquesPantalla(bib: Biblioteca, obre: (Llibre) -> Unit, enrere: () -> Unit) {
    val est = bib.estadistiques
    val perCami = bib.llibres.associateBy { it.cami }
    fun titolDe(cami: String) = perCami[cami]?.titol ?: cami.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

    Column(Modifier.fillMaxSize().background(Xoriguer.Fons)) {
        Capcalera()
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enrere) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = Xoriguer.Crema) }
            Text("Estadístiques", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- avui i objectiu ----
            Targeta {
                val avui = est.minutsAvui()
                val obj = est.objectiuMinuts
                Text("Avui", color = Xoriguer.Mut, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$avui", color = Xoriguer.Crema, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text(" de $obj min", color = Xoriguer.Mut, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (avui >= obj) Text("Objectiu complert ✓", color = Xoriguer.Taronja, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                LinearProgressIndicator(
                    { (avui.toFloat() / obj).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp),
                    color = Xoriguer.Taronja, trackColor = Xoriguer.Ui3, drawStopIndicator = {},
                )
                Spacer(Modifier.height(10.dp))
                Text("Objectiu diari: $obj min", color = Xoriguer.Txt, fontSize = 14.sp)
                var lliscant by remember { mutableFloatStateOf(obj.toFloat()) }
                LaunchedEffect(obj) { lliscant = obj.toFloat() }
                Slider(
                    lliscant, { lliscant = (it / 5).toInt() * 5f }, valueRange = 5f..240f,
                    onValueChangeFinished = { est.canviaObjectiu(lliscant.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Xoriguer.Taronja, activeTrackColor = Xoriguer.Taronja, inactiveTrackColor = Xoriguer.Ui3),
                )
            }

            // ---- ratxa i totals ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val r = est.ratxa()
                Xifra(Modifier.weight(1f), "$r", if (r == 1) "dia seguit" else "dies seguits")
                Xifra(Modifier.weight(1f), durada(est.segonsTotals()), "en total")
                Xifra(Modifier.weight(1f), "${est.paginesTotals()}", "pàgines")
            }

            // ---- últims 7 dies ----
            Targeta {
                Text("Últims 7 dies", color = Xoriguer.Mut, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                val dies = est.ultims(7)
                val max = (dies.maxOf { it.second.segons } / 60f).coerceAtLeast(est.objectiuMinuts.toFloat())
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    dies.forEach { (d, v) ->
                        val min = v.segons / 60
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(if (min > 0) "$min" else "", color = Xoriguer.Txt, fontSize = 11.sp)
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((min / max).coerceIn(0.02f, 1f) * .8f)
                                    .background(if (min >= est.objectiuMinuts) Xoriguer.Taronja else Xoriguer.TaronjaFosc.copy(alpha = .6f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                            )
                            Text(Calendari.diaSetmana(d), color = if (d == est.avui()) Xoriguer.Taronja else Xoriguer.Mut, fontSize = 12.sp)
                        }
                    }
                }
                val pag = dies.sumOf { it.second.pagines }
                Spacer(Modifier.height(6.dp))
                Text("${durada(dies.sumOf { it.second.segons })} i $pag pàgines aquesta setmana · minuts per dia", color = Xoriguer.Mut, fontSize = 12.sp)
            }

            // ---- llibres acabats ----
            Targeta {
                Text("Llibres acabats", color = Xoriguer.Mut, fontSize = 13.sp)
                Text("${est.acabatsAquestAny()} aquest any · ${est.acabats.size} en total", color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (est.acabats.isEmpty())
                    Text("Quan arribis al final d'un llibre, sortirà aquí. També pots marcar-lo com a llegit des de la fitxa del llibre (mantén-lo premut a la biblioteca).", color = Xoriguer.Mut, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                est.acabats.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = a.cami in perCami) { perCami[a.cami]?.let(obre) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.titol, color = Xoriguer.Txt, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(a.autor, Calendari.llarga(a.dia)).joinToString(" · "), color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton({ est.desmarcaAcabat(a.cami) }) { Icon(Icons.Default.Close, "Treu-lo dels acabats", tint = Xoriguer.Mut) }
                    }
                }
            }

            // ---- més temps ----
            if (est.perLlibre.isNotEmpty()) Targeta {
                Text("On has passat més estona", color = Xoriguer.Mut, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                est.perLlibre.entries.sortedByDescending { it.value }.take(10).forEach { (cami, s) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = cami in perCami) { perCami[cami]?.let(obre) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(titolDe(cami), color = Xoriguer.Txt, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(durada(s), color = Xoriguer.Taronja, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "El temps només compta mentre llegeixes: si deixes el llibre obert sense tocar-lo durant 3 minuts, s'atura (llevat que l'estigui llegint la veu).",
                color = Xoriguer.Mut, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Targeta(contingut: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().background(Xoriguer.Ui, RoundedCornerShape(10.dp)).padding(14.dp), content = contingut)

@Composable
private fun Xifra(m: Modifier, valor: String, etiqueta: String) =
    Column(m.background(Xoriguer.Ui, RoundedCornerShape(10.dp)).padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = Xoriguer.Crema, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        Text(etiqueta, color = Xoriguer.Mut, fontSize = 12.sp, maxLines = 1)
    }
