package com.logicgates

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VM(app: Application) : AndroidViewModel(app) {
    var tf by mutableStateOf(TextFieldValue("(P ∨ Q) ∧ R"))
    var tf2 by mutableStateOf(TextFieldValue("P"))
    var ast by mutableStateOf<Node?>(null)
    var circuit by mutableStateOf<Circuit?>(null)
    var error by mutableStateOf<String?>(null)
    var inputs by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var panel by mutableStateOf("")
    var eqResult by mutableStateOf<String?>(null)
    var saved by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private val file get() = File(getApplication<Application>().filesDir, "circuits.json")

    init { saved = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("name") to o.getString("expr") } }
    } catch (e: Exception) { emptyList() } }

    fun insert(s: String) {
        val sel = tf.selection
        val bin = s in listOf("∧", "∨", "→", "↔", "⊕", "=")
        val ins = if (bin) " $s " else s
        tf = TextFieldValue(tf.text.replaceRange(sel.min, sel.max, ins), TextRange(sel.min + ins.length))
    }
    fun backspace() {
        val sel = tf.selection
        if (sel.min != sel.max) tf = TextFieldValue(tf.text.removeRange(sel.min, sel.max), TextRange(sel.min))
        else if (sel.min > 0) tf = TextFieldValue(tf.text.removeRange(sel.min - 1, sel.min), TextRange(sel.min - 1))
    }
    fun clear() { tf = TextFieldValue(""); circuit = null; ast = null; error = null; panel = "" }
    fun draw() {
        try {
            val n = parse(tf.text); ast = n; circuit = CircuitGen.build(n)
            inputs = n.vars().sorted().associateWith { false }; error = null
        } catch (e: ParseError) { error = e.message; circuit = null; ast = null }
    }
    fun show(p: String) { draw(); if (error == null) panel = p }
    fun toggle(k: String) { inputs = inputs + (k to !(inputs[k] ?: false)) }
    fun open(expr: String) { tf = TextFieldValue(expr); draw(); panel = "" }

    private fun b(x: Boolean) = if (x) "1" else "0"
    fun checkEq() {
        eqResult = try {
            val d = findDiff(parse(tf.text), parse(tf2.text))
            if (d == null) "العبارتان متكافئتان منطقيًا ✓"
            else "العبارتان غير متكافئتين. الاختلاف عند: " + d.first.entries.joinToString("، ") { "${it.key}=${b(it.value)}" } +
                " ← الأولى = ${b(d.second.first)} والثانية = ${b(d.second.second)}"
        } catch (e: ParseError) { e.message }
    }

    private fun persist() {
        val a = JSONArray()
        saved.forEach { a.put(JSONObject().put("name", it.first).put("expr", it.second).put("ast", parse(it.second).toString())) }
        file.writeText(a.toString())
    }
    fun save(): Boolean {
        draw(); val n = ast ?: return false
        saved = saved.filter { it.second != tf.text } + (n.str() to tf.text); persist(); return true
    }
    fun delete(expr: String) { saved = saved.filter { it.second != expr }; persist() }
}
