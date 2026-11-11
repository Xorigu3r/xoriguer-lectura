package cat.xoriguer.lectura.formats

/** Lector de JSON mínim (objectes → Map, llistes → List, números → Double). Prou per a les API que fem servir. */
object Json {
    fun llegeix(s: String): Any? = Lector(s).run { val v = valor(); espais(); v }

    private class Lector(val s: String) {
        var i = 0
        fun espais() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun valor(): Any? {
            espais()
            if (i >= s.length) error("JSON incomplet")
            return when (s[i]) {
                '{' -> objecte()
                '[' -> llista()
                '"' -> cadena()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> numero()
            }
        }
        fun objecte(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++
            espais(); if (s[i] == '}') { i++; return m }
            while (true) {
                espais(); val k = cadena(); espais(); i++ // ':'
                m[k] = valor(); espais()
                if (s[i++] == '}') return m
            }
        }
        fun llista(): List<Any?> {
            val l = ArrayList<Any?>(); i++
            espais(); if (s[i] == ']') { i++; return l }
            while (true) {
                l += valor(); espais()
                if (s[i++] == ']') return l
            }
        }
        fun cadena(): String {
            val b = StringBuilder(); i++
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                        'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
            error("Cadena JSON sense acabar")
        }
        fun numero(): Double {
            val ini = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(ini, i).toDouble()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.obj(k: String): Map<String, Any?>? = (this as? Map<String, Any?>)?.get(k) as? Map<String, Any?>
fun Any?.llista(k: String): List<Any?> = ((this as? Map<*, *>)?.get(k) as? List<Any?>).orEmpty()
fun Any?.text(k: String): String? = (this as? Map<*, *>)?.get(k) as? String
