package com.logicgates

sealed interface Node
data class V(val n: String) : Node
data class K(val v: Boolean) : Node
data class Not(val a: Node) : Node
enum class Op(val sym: String) { AND("∧"), OR("∨"), XOR("⊕"), NAND("NAND"), NOR("NOR"), XNOR("XNOR"), IMP("→"), IFF("↔") }
data class Bin(val op: Op, val l: Node, val r: Node) : Node
class ParseError(msg: String) : Exception(msg)

private val KW = setOf("AND", "OR", "NOT", "NAND", "NOR", "XOR", "XNOR")

fun lex(s: String): List<String> {
    val t = ArrayList<String>(); var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isWhitespace() -> i++
            c.isLetter() -> { var j = i; while (j < s.length && s[j].isLetterOrDigit()) j++
                val w = s.substring(i, j); t.add(if (w.uppercase() in KW) w.uppercase() else "V:$w"); i = j }
            c == '0' || c == '1' -> { t.add("K:$c"); i++ }
            c == '(' || c == ')' || c == '=' -> { t.add(c.toString()); i++ }
            c == '¬' || c == '!' || c == '~' -> { t.add("NOT"); i++ }
            c == '∧' || c == '&' -> { t.add("AND"); i++ }
            c == '∨' || c == '|' -> { t.add("OR"); i++ }
            c == '→' -> { t.add("IMP"); i++ }
            c == '↔' -> { t.add("IFF"); i++ }
            c == '⊕' || c == '^' -> { t.add("XOR"); i++ }
            s.startsWith("<->", i) -> { t.add("IFF"); i += 3 }
            s.startsWith("->", i) -> { t.add("IMP"); i += 2 }
            else -> throw ParseError("رمز غير مفهوم: $c")
        }
    }
    return t
}

private class Pr(val t: List<String>) {
    var i = 0
    fun peek() = t.getOrNull(i)
    fun iff(): Node { var l = imp(); while (peek() == "IFF") { i++; l = Bin(Op.IFF, l, imp()) }; return l }
    fun imp(): Node { val l = or(); if (peek() == "IMP") { i++; return Bin(Op.IMP, l, imp()) }; return l }
    fun or(): Node { var l = xor()
        while (peek() == "OR" || peek() == "NOR") { val o = if (t[i] == "OR") Op.OR else Op.NOR; i++; l = Bin(o, l, xor()) }; return l }
    fun xor(): Node { var l = and()
        while (peek() == "XOR" || peek() == "XNOR") { val o = if (t[i] == "XOR") Op.XOR else Op.XNOR; i++; l = Bin(o, l, and()) }; return l }
    fun and(): Node { var l = un()
        while (peek() == "AND" || peek() == "NAND") { val o = if (t[i] == "AND") Op.AND else Op.NAND; i++; l = Bin(o, l, un()) }; return l }
    fun un(): Node { if (peek() == "NOT") { i++; return Not(un()) }; return atom() }
    fun atom(): Node {
        val k = peek() ?: throw ParseError("العبارة غير مكتملة.")
        i++
        return when {
            k.startsWith("V:") -> V(k.drop(2))
            k.startsWith("K:") -> K(k[2] == '1')
            k == "(" -> { val e = iff(); if (peek() != ")") throw ParseError("يوجد قوس مفتوح لم يتم إغلاقه."); i++; e }
            k == ")" -> throw ParseError("يوجد قوس إغلاق في غير مكانه أو أقواس فارغة.")
            else -> throw ParseError("لا يمكن وضع ${mapOf("IMP" to "→", "IFF" to "↔").getOrDefault(k, k)} بدون قيمتين.")
        }
    }
}

fun parse(src: String): Node {
    var t = lex(src)
    if (t.size >= 2 && t[0].startsWith("V:") && t[1] == "=") t = t.drop(2)
    if (t.isEmpty()) throw ParseError("اكتب عبارة منطقية أولاً.")
    val p = Pr(t); val n = p.iff()
    if (p.i < t.size) throw ParseError(if (t[p.i] == ")") "يوجد قوس إغلاق بدون قوس فتح." else "العبارة غير صحيحة: يوجد رمز في غير مكانه.")
    return n
}

fun Node.vars(): List<String> = when (this) {
    is V -> listOf(n); is K -> emptyList(); is Not -> a.vars(); is Bin -> (l.vars() + r.vars()).distinct()
}

fun Node.ev(e: Map<String, Boolean>): Boolean = when (this) {
    is V -> e[n] ?: false
    is K -> v
    is Not -> !a.ev(e)
    is Bin -> { val x = l.ev(e); val y = r.ev(e)
        when (op) { Op.AND -> x && y; Op.OR -> x || y; Op.XOR -> x != y; Op.NAND -> !(x && y)
            Op.NOR -> !(x || y); Op.XNOR -> x == y; Op.IMP -> !x || y; Op.IFF -> x == y } }
}

fun Node.str(top: Boolean = true): String = when (this) {
    is V -> n
    is K -> if (v) "1" else "0"
    is Not -> "¬" + a.str(false)
    is Bin -> { val s = "${l.str(false)} ${op.sym} ${r.str(false)}"; if (top) s else "($s)" }
}

fun truthTable(n: Node, vs: List<String> = n.vars().sorted()): List<Pair<List<Boolean>, Boolean>> =
    (0 until (1 shl vs.size)).map { m ->
        val row = vs.indices.map { ((m shr (vs.size - 1 - it)) and 1) == 1 }
        row to n.ev(vs.zip(row).toMap())
    }

/** أول صف يختلف فيه التعبيران، أو null إذا كانا متكافئين. */
fun findDiff(a: Node, b: Node): Pair<Map<String, Boolean>, Pair<Boolean, Boolean>>? {
    val vs = (a.vars() + b.vars()).distinct().sorted()
    for (m in 0 until (1 shl vs.size)) {
        val e = vs.indices.associate { vs[it] to (((m shr (vs.size - 1 - it)) and 1) == 1) }
        val x = a.ev(e); val y = b.ev(e)
        if (x != y) return e to (x to y)
    }
    return null
}

object Simplifier {
    private fun rule(n: Node): Pair<Node, String>? = when (n) {
        is Not -> { val a = n.a
            when {
                a is Not -> a.a to "قانون النفي المزدوج"
                a is K -> K(!a.v) to "قانون النفي"
                a is Bin && a.op == Op.AND -> Bin(Op.OR, Not(a.l), Not(a.r)) to "قانون دي مورغان"
                a is Bin && a.op == Op.OR -> Bin(Op.AND, Not(a.l), Not(a.r)) to "قانون دي مورغان"
                else -> null
            } }
        is Bin -> binRule(n)
        else -> null
    }

    private fun binRule(n: Bin): Pair<Node, String>? {
        val l = n.l; val r = n.r
        when (n.op) {
            Op.IMP -> return Bin(Op.OR, Not(l), r) to "إزالة الشرط"
            Op.IFF -> return Bin(Op.OR, Bin(Op.AND, l, r), Bin(Op.AND, Not(l), Not(r))) to "إزالة الشرط الثنائي"
            Op.NAND -> return Not(Bin(Op.AND, l, r)) to "تعريف NAND"
            Op.NOR -> return Not(Bin(Op.OR, l, r)) to "تعريف NOR"
            Op.XNOR -> return Not(Bin(Op.XOR, l, r)) to "تعريف XNOR"
            Op.XOR -> return Bin(Op.OR, Bin(Op.AND, l, Not(r)), Bin(Op.AND, Not(l), r)) to "تعريف XOR"
            else -> {}
        }
        val and = n.op == Op.AND
        val id = K(and); val ab = K(!and); val inv = if (and) Op.OR else Op.AND
        return when {
            l == r -> l to "قانون التماثل"
            l == id -> r to "قانون الهوية"
            r == id -> l to "قانون الهوية"
            l == ab || r == ab -> ab to "قانون الهيمنة"
            l == Not(r) || r == Not(l) -> ab to (if (and) "قانون التناقض" else "قانون النفي")
            r is Bin && r.op == inv && (r.l == l || r.r == l) -> l to "قانون الامتصاص"
            l is Bin && l.op == inv && (l.l == r || l.r == r) -> r to "قانون الامتصاص"
            l is Bin && r is Bin && l.op == inv && r.op == inv -> {
                val c = listOf(
                    Triple(l.l, r.l, l.r to r.r), Triple(l.l, r.r, l.r to r.l),
                    Triple(l.r, r.l, l.l to r.r), Triple(l.r, r.r, l.l to r.l)
                ).firstOrNull { it.first == it.second }
                if (c == null) null
                else Bin(inv, c.first, Bin(n.op, c.third.first, c.third.second)) to "قانون التوزيع"
            }
            else -> null
        }
    }

    private fun step(n: Node): Pair<Node, String>? {
        when (n) {
            is Not -> step(n.a)?.let { return Not(it.first) to it.second }
            is Bin -> {
                step(n.l)?.let { return Bin(n.op, it.first, n.r) to it.second }
                step(n.r)?.let { return Bin(n.op, n.l, it.first) to it.second }
            }
            else -> {}
        }
        return rule(n)
    }

    /** قائمة (اسم القانون، العبارة الناتجة). */
    fun run(n: Node): List<Pair<String, Node>> {
        var c = n; val out = ArrayList<Pair<String, Node>>()
        repeat(60) { val s = step(c) ?: return out; c = s.first; out.add(s.second to c) }
        return out
    }
}
