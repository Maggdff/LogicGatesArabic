package com.logicgates

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private fun tt(s: String) = truthTable(parse(s)).map { if (it.second) 1 else 0 }
    @Test fun and() = assertEquals(listOf(0, 0, 0, 1), tt("P ∧ Q"))
    @Test fun or() = assertEquals(listOf(0, 1, 1, 1), tt("P ∨ Q"))
    @Test fun not() = assertEquals(listOf(1, 0), tt("¬P"))
    @Test fun andOr() = assertEquals(listOf(0, 1, 0, 1, 0, 1, 1, 1), tt("(P ∧ Q) ∨ R"))
    @Test fun nor() = assertEquals(listOf(1, 0, 0, 0), tt("¬(P ∨ Q)"))
    @Test fun imp() = assertEquals(listOf(1, 1, 0, 1), tt("P → Q"))
    @Test fun iff() = assertEquals(listOf(1, 0, 0, 1), tt("P ↔ Q"))
    @Test fun words() = assertEquals(tt("(P ∧ Q) ∨ ¬R"), tt("(P AND Q) OR NOT R"))
    @Test fun shortcuts() = assertEquals(tt("(A ∧ B) ∨ ¬C"), tt("(A & B) | !C"))
    @Test fun prefixY() = assertEquals(tt("(P ∨ Q) ∧ R"), tt("Y = (P ∨ Q) ∧ R"))
    @Test fun rowCount() = assertEquals(8, tt("P ∧ Q ∧ R").size)
    @Test fun ast() = assertEquals(Not(Bin(Op.AND, V("P"), Bin(Op.OR, V("Q"), Not(V("R"))))), parse("¬(P ∧ (Q ∨ ¬R))"))
    @Test fun errors() {
        assertTrue(runCatching { parse("(P ∧ Q") }.exceptionOrNull() is ParseError)
        assertTrue(runCatching { parse("P ∧") }.exceptionOrNull() is ParseError)
        assertTrue(runCatching { parse("∧ P") }.exceptionOrNull() is ParseError)
    }
    @Test fun equivalence() {
        assertNull(findDiff(parse("(P ∨ Q) ∧ (P ∨ ¬Q)"), parse("P")))
        assertNotNull(findDiff(parse("P ∨ Q"), parse("P")))
    }
    @Test fun simplify() {
        assertEquals(V("P"), Simplifier.run(parse("(P ∨ Q) ∧ (P ∨ ¬Q)")).last().second)
        assertEquals("¬P ∧ ¬Q", Simplifier.run(parse("¬(P ∨ Q)")).last().second.str())
        assertEquals("¬P ∨ Q", Simplifier.run(parse("P → Q")).last().second.str())
    }
    @Test fun circuitStructure() {
        val c = CircuitGen.build(parse("(P ∨ Q) ∧ R"))
        val types = c.nodes.map { it.t }
        assertTrue(GT.OR in types && GT.AND in types)
        assertEquals(3, c.nodes.count { it.t == GT.IN })
        assertEquals("((P ∨ Q) ∧ R)", c.exprOf(c.outId))
    }
    @Test fun circuitSimulationMatchesTruthTable() {
        for (s in listOf("(P ∧ Q) ∨ R", "¬(P ∨ (Q ∧ R))", "(P → Q) ∧ (Q → R)", "P ↔ Q", "P ⊕ (Q ∧ R)")) {
            val n = parse(s); val c = CircuitGen.build(n)
            for ((bits, y) in truthTable(n)) {
                val env = n.vars().sorted().zip(bits).toMap()
                assertEquals("$s $env", y, c.simulate(env)[c.outId])
            }
        }
    }
    @Test fun noWireSkipsLevels() {
        val c = CircuitGen.build(parse("(P ∧ Q) ∨ R"))
        assertTrue(c.edges.all { c.nodes[it.dst].lvl - c.nodes[it.src].lvl == 1 })
    }
}
