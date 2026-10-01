package com.logicgates

import kotlin.math.max

enum class GT(val sym: String) { IN(""), AND("∧"), OR("∨"), XOR("⊕"), NAND("NAND"), NOR("NOR"), XNOR("XNOR"), NOT("¬"), WIRE(""), OUT("") }
class CN(val id: Int, val t: GT, val name: String, var ins: List<Int>) { var lvl = 0; var x = 0f; var y = 0f }
class Edge(val src: Int, val dst: Int, val k: Int, val cx: Float)

class Circuit(val nodes: List<CN>, val outId: Int, val edges: List<Edge>) {
    fun outPin(n: CN) = if (n.t == GT.IN) Pair(n.x + 20f, n.y) else Pair(n.x + 80f, n.y)
    fun inPin(n: CN, k: Int) = if (n.ins.size == 2) Pair(n.x, n.y + (if (k == 0) -15f else 15f)) else Pair(n.x, n.y)

    fun simulate(inp: Map<String, Boolean>): Map<Int, Boolean> {
        val v = HashMap<Int, Boolean>()
        for (n in nodes.sortedBy { it.lvl }) {
            val a = n.ins.map { v.getValue(it) }
            v[n.id] = when (n.t) {
                GT.IN -> when (n.name) { "1" -> true; "0" -> false; else -> inp[n.name] ?: false }
                GT.NOT -> !a[0]
                GT.WIRE, GT.OUT -> a[0]
                GT.AND -> a[0] && a[1]
                GT.OR -> a[0] || a[1]
                GT.XOR -> a[0] != a[1]
                GT.NAND -> !(a[0] && a[1])
                GT.NOR -> !(a[0] || a[1])
                GT.XNOR -> a[0] == a[1]
            }
        }
        return v
    }

    fun exprOf(id: Int): String {
        val n = nodes[id]
        return when (n.t) {
            GT.IN -> n.name
            GT.WIRE, GT.OUT -> exprOf(n.ins[0])
            GT.NOT -> "¬" + exprOf(n.ins[0])
            else -> "(${exprOf(n.ins[0])} ${n.t.sym} ${exprOf(n.ins[1])})"
        }
    }

    fun explain(): List<String> {
        val r = ArrayList<String>(); var i = 1
        for (n in nodes.sortedBy { it.lvl }) {
            if (n.t == GT.IN || n.t == GT.WIRE || n.t == GT.OUT) continue
            val a = n.ins.map { exprOf(it) }
            r.add(if (n.t == GT.NOT) "$i. تدخل ${a[0]} إلى بوابة NOT فتعكس قيمتها وينتج ${exprOf(n.id)}."
            else "$i. تدخل ${a[0]} و ${a[1]} إلى بوابة ${n.t.name} وينتج ${exprOf(n.id)}.")
            i++
        }
        r.add("ثم تنتقل الإشارة إلى المخرج، فيكون Y = ${exprOf(outId)}")
        return r
    }

    /** [left, top, width, height] */
    fun bounds(): FloatArray {
        val l = -40f; val r = nodes[outId].x + 60f
        val t = nodes.minOf { it.y } - 50f; val b = nodes.maxOf { it.y } + 50f
        return floatArrayOf(l, t, r - l, b - t)
    }

    fun hit(px: Float, py: Float): String? =
        nodes.firstOrNull { it.t == GT.IN && it.name != "0" && it.name != "1" &&
            px in (it.x - 26)..(it.x + 20) && py in (it.y - 18)..(it.y + 18) }?.name
}

object CircuitGen {
    const val COL = 170f
    const val GAP = 80f

    fun build(root: Node): Circuit {
        val nodes = ArrayList<CN>(); val memo = HashMap<Node, Int>()
        fun add(t: GT, name: String, ins: List<Int>): Int {
            val c = CN(nodes.size, t, name, ins)
            c.lvl = if (ins.isEmpty()) 0 else 1 + ins.maxOf { nodes[it].lvl }
            nodes.add(c); return c.id
        }
        fun go(n: Node): Int = memo.getOrPut(n) {
            when (n) {
                is V -> add(GT.IN, n.n, emptyList())
                is K -> add(GT.IN, if (n.v) "1" else "0", emptyList())
                is Not -> add(GT.NOT, "", listOf(go(n.a)))
                is Bin -> when (n.op) {
                    Op.IMP -> add(GT.OR, "", listOf(go(Not(n.l)), go(n.r)))
                    Op.IFF -> add(GT.XNOR, "", listOf(go(n.l), go(n.r)))
                    else -> add(GT.valueOf(n.op.name), "", listOf(go(n.l), go(n.r)))
                }
            }
        }
        val r = go(root)
        // عقد وسيطة (أسلاك) حتى لا يعبر أي سلك فوق بوابة في عمود آخر
        val dm = HashMap<Pair<Int, Int>, Int>()
        fun feed(src: Int, lv: Int): Int {
            if (nodes[src].lvl >= lv - 1) return src
            return dm.getOrPut(src to (lv - 1)) {
                val prev = feed(src, lv - 1)
                val d = CN(nodes.size, GT.WIRE, "", listOf(prev)); d.lvl = lv - 1; nodes.add(d); d.id
            }
        }
        for (n in nodes.toList()) n.ins = n.ins.map { feed(it, n.lvl) }
        val outId = add(GT.OUT, "Y", listOf(r))
        // ترتيب: الأعمدة حسب المستوى، والصفوف بمتوسط مواقع المدخلات (barycenter)
        for ((l, g) in nodes.groupBy { it.lvl }.toSortedMap()) {
            if (l == 0) g.sortedBy { it.name }.forEachIndexed { i, n -> n.y = i * GAP }
            else {
                var last = -1e9f
                for (n in g.sortedBy { c -> c.ins.map { nodes[it].y }.average().toFloat() }) {
                    n.y = max(n.ins.map { nodes[it].y }.average().toFloat(), last + GAP); last = n.y
                }
            }
            g.forEach { it.x = l * COL }
        }
        val edges = ArrayList<Edge>()
        for ((_, g) in nodes.groupBy { it.lvl }) {
            val es = g.flatMap { n -> n.ins.mapIndexed { k, s -> Triple(n, k, s) } }.sortedBy { nodes[it.third].y }
            es.forEachIndexed { i, (n, k, s) -> edges.add(Edge(s, n.id, k, n.x - 90f + (i + 1) * 90f / (es.size + 1))) }
        }
        return Circuit(nodes, outId, edges)
    }
}
