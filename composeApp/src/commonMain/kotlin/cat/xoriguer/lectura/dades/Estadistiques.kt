package cat.xoriguer.lectura.dades

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import cat.xoriguer.lectura.Plataforma

/** Un dia de lectura. */
data class DiaLectura(val segons: Long = 0, val pagines: Int = 0)

/** Un llibre acabat (dia = dies des de l'1-1-1970, hora local). */
data class LlibreAcabat(val cami: String, val titol: String, val autor: String?, val dia: Int)

/**
 * Estadístiques de lectura: temps i pàgines per dia, temps per llibre, llibres acabats i l'objectiu diari.
 * Es desa a `estadistiques.tsv` (línies D = dia, L = llibre, A = acabat, O = objectiu).
 */
class Estadistiques(private val fitxer: String? = "estadistiques.tsv") {
    val dies = mutableStateMapOf<Int, DiaLectura>()
    val perLlibre = mutableStateMapOf<String, Long>()
    val acabats = mutableStateListOf<LlibreAcabat>()
    var objectiuMinuts by mutableIntStateOf(20)

    init { carrega() }

    fun avui() = Plataforma.diaLocal()

    fun afegeixTemps(cami: String, segons: Long) {
        if (segons <= 0) return
        val d = avui()
        dies[d] = (dies[d] ?: DiaLectura()).let { it.copy(segons = it.segons + segons) }
        perLlibre[cami] = (perLlibre[cami] ?: 0) + segons
        desa()
    }

    fun afegeixPagina() {
        val d = avui()
        dies[d] = (dies[d] ?: DiaLectura()).let { it.copy(pagines = it.pagines + 1) }
        desa()
    }

    fun estaAcabat(cami: String) = acabats.any { it.cami == cami }

    fun marcaAcabat(l: Llibre) {
        if (estaAcabat(l.cami)) return
        acabats.add(0, LlibreAcabat(l.cami, l.titol, l.autor, avui())); desa()
    }

    fun desmarcaAcabat(cami: String) { if (acabats.removeAll { it.cami == cami }) desa() }

    fun canviaObjectiu(minuts: Int) { objectiuMinuts = minuts.coerceIn(5, 240); desa() }

    fun canviaCami(vell: String, nou: String) {
        var canvi = false
        perLlibre.remove(vell)?.let { perLlibre[nou] = (perLlibre[nou] ?: 0) + it; canvi = true }
        for (i in acabats.indices) if (acabats[i].cami == vell) { acabats[i] = acabats[i].copy(cami = nou); canvi = true }
        if (canvi) desa()
    }

    // ---- càlculs ----
    fun minutsAvui(): Int = ((dies[avui()]?.segons ?: 0) / 60).toInt()

    /** Dies seguits llegint (almenys 1 minut), comptant avui si ja s'hi ha llegit, o fins ahir si encara no. */
    fun ratxa(): Int {
        var d = avui()
        if ((dies[d]?.segons ?: 0) < 60) d--
        var n = 0
        while ((dies[d]?.segons ?: 0) >= 60) { n++; d-- }
        return n
    }

    fun segonsTotals() = dies.values.sumOf { it.segons }
    fun paginesTotals() = dies.values.sumOf { it.pagines }

    /** Els últims `n` dies, del més antic al d'avui. */
    fun ultims(n: Int): List<Pair<Int, DiaLectura>> { val a = avui(); return (a - n + 1..a).map { it to (dies[it] ?: DiaLectura()) } }

    fun acabatsAquestAny(): Int { val any = Calendari.data(avui()).first; return acabats.count { Calendari.data(it.dia).first == any } }

    // ---- fitxer ----
    private fun carrega() {
        val text = fitxer?.let { Plataforma.llegeixDada(it) } ?: return
        for (l in text.lines()) {
            val p = l.split('\t')
            when (p.getOrNull(0)) {
                "D" -> if (p.size >= 4) dies[p[1].toIntOrNull() ?: continue] = DiaLectura(p[2].toLongOrNull() ?: 0, p[3].toIntOrNull() ?: 0)
                "L" -> if (p.size >= 3) perLlibre[p[1]] = p[2].toLongOrNull() ?: 0
                "A" -> if (p.size >= 5) acabats += LlibreAcabat(p[1], p[3], p[4].ifEmpty { null }, p[2].toIntOrNull() ?: 0)
                "O" -> p.getOrNull(1)?.toIntOrNull()?.let { objectiuMinuts = it }
            }
        }
    }

    private fun desa() {
        val f = fitxer ?: return
        val net = { s: String -> s.replace('\t', ' ').replace('\n', ' ') }
        Plataforma.desaDada(f, buildString {
            append("O\t$objectiuMinuts\n")
            dies.entries.sortedBy { it.key }.forEach { (d, v) -> append("D\t$d\t${v.segons}\t${v.pagines}\n") }
            perLlibre.forEach { (c, s) -> append("L\t${net(c)}\t$s\n") }
            acabats.forEach { a -> append("A\t${net(a.cami)}\t${a.dia}\t${net(a.titol)}\t${net(a.autor.orEmpty())}\n") }
        })
    }
}

/** Dates a partir del número de dia (sense dependre de cap llibreria de dates). */
object Calendari {
    private val MESOS = listOf("gener", "febrer", "març", "abril", "maig", "juny", "juliol", "agost", "setembre", "octubre", "novembre", "desembre")
    private val DIES = listOf("dl", "dt", "dc", "dj", "dv", "ds", "dg")

    /** (any, mes 1-12, dia 1-31) del dia número `d` (algorisme civil de Howard Hinnant). */
    fun data(d: Int): Triple<Int, Int, Int> {
        val z = d + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = doy - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        return Triple(yoe + era * 400 + if (mes <= 2) 1 else 0, mes, dia)
    }

    /** "13 de novembre de 2026" (amb «d'» davant de vocal: «d'abril»). */
    fun llarga(d: Int): String {
        val (a, m, di) = data(d); val nom = MESOS[m - 1]
        return "$di ${if (nom[0] in "ao") "d'" else "de "}$nom de $a"
    }

    /** Inicial del dia de la setmana: dl, dt… (l'1-1-1970 va ser dijous). */
    fun diaSetmana(d: Int) = DIES[((d % 7) + 7 + 3) % 7]
}
