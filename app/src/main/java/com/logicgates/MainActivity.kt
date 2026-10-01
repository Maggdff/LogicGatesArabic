package com.logicgates

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

val EXAMPLES = listOf("P ∧ Q", "P ∨ Q", "¬P", "(P ∧ Q) ∨ R", "(P ∨ Q) ∧ R", "¬(P ∧ Q)",
    "P → Q", "P ↔ Q", "(P → Q) ∧ (Q → R)", "¬(P ∨ (Q ∧ R))")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Color.White) { App() }
                }
            }
        }
    }
}

@Composable
fun B(t: String, onClick: () -> Unit) = Button(onClick, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(t) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(vm: VM = viewModel()) {
    val ctx = LocalContext.current
    val c = vm.circuit
    val vals = remember(c, vm.inputs) { c?.simulate(vm.inputs) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    val ltr = TextStyle(fontSize = 20.sp, textDirection = TextDirection.Ltr)
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("رسم الدوائر المنطقية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(vm.tf, { vm.tf = it }, Modifier.fillMaxWidth(), label = { Text("اكتب العبارة المنطقية") }, textStyle = ltr)
        vm.error?.let { Text(it, color = Color(0xFFC62828)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("P", "Q", "R", "S", "¬", "∧", "∨", "→", "↔", "⊕", "(", ")", "=").forEach {
                FilledTonalButton({ vm.insert(it) }, Modifier.size(48.dp, 44.dp), contentPadding = PaddingValues(0.dp)) { Text(it, fontSize = 18.sp) }
            }
            FilledTonalButton({ vm.backspace() }, Modifier.size(48.dp, 44.dp), contentPadding = PaddingValues(0.dp)) { Text("⌫") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            B("ارسم الدائرة") { vm.draw() }
            OutlinedButton({ vm.clear() }) { Text("مسح") }
            B("جدول الحقيقة") { vm.show("truth") }
            B("تبسيط العبارة") { vm.show("simp") }
            B("شرح الدائرة") { vm.show("explain") }
            B("اختبار التكافؤ") { vm.panel = "eq" }
            B("أمثلة") { vm.panel = "ex" }
            B("دوائري") { vm.panel = "saved" }
            B("حفظ الدائرة") { toast(if (vm.save()) "تم الحفظ" else "لا يمكن حفظ عبارة غير صحيحة") }
            B("تصدير صورة") { c?.let { toast(if (exportPng(ctx, it, vals) != null) "تم الحفظ في Pictures/LogicGates" else "فشل التصدير") } }
            B("مشاركة") { c?.let { cc ->
                exportPng(ctx, cc, vals)?.let { u ->
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "مشاركة")) } } }
        }
        if (c != null && vals != null) {
            CircuitView(c, vals) { vm.toggle(it) }
            Text("INPUT", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.inputs.forEach { (k, v) -> OutlinedButton({ vm.toggle(k) }) { Text("$k = ${if (v) 1 else 0}") } }
            }
            Text("OUTPUT   Y = ${if (vals[c.outId] == true) 1 else 0}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        when (vm.panel) {
            "truth" -> vm.ast?.let { n ->
                val vs = n.vars().sorted()
                if (vs.size > 8) Text("عدد المتغيرات كبير (الحد الأقصى 8).") else {
                    Row { (vs + "Y").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) } }
                    HorizontalDivider()
                    truthTable(n, vs).forEach { (bits, y) ->
                        Row { (bits + y).forEach { Text(if (it) "1" else "0", Modifier.weight(1f), textAlign = TextAlign.Center) } }
                    }
                }
            }
            "simp" -> vm.ast?.let { n ->
                Text("العبارة الأصلية: ${n.str()}", fontWeight = FontWeight.Bold)
                val steps = Simplifier.run(n)
                if (steps.isEmpty()) Text("العبارة مبسطة بالفعل.")
                steps.forEach { (law, r) -> Text("↓ $law"); Text(r.str(), style = ltr) }
            }
            "explain" -> c?.explain()?.forEach { Text(it) }
            "eq" -> {
                Text("العبارة الأولى: ${vm.tf.text}")
                OutlinedTextField(vm.tf2, { vm.tf2 = it }, Modifier.fillMaxWidth(), label = { Text("العبارة الثانية") }, textStyle = ltr)
                B("قارن") { vm.checkEq() }
                vm.eqResult?.let { Text(it, fontWeight = FontWeight.Bold) }
            }
            "ex" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EXAMPLES.forEach { OutlinedButton({ vm.open(it) }) { Text(it) } }
            }
            "saved" -> {
                if (vm.saved.isEmpty()) Text("لا توجد دوائر محفوظة.")
                vm.saved.forEach { (name, expr) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ vm.open(expr) }, Modifier.weight(1f)) { Text(name, style = ltr) }
                        TextButton({ vm.delete(expr) }) { Text("حذف") }
                    }
                }
            }
        }
    }
}

@Composable
fun CircuitView(c: Circuit, vals: Map<Int, Boolean>, onToggle: (String) -> Unit) {
    var sc by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun fit() {
        if (size.width == 0) return
        val b = c.bounds(); val s = minOf(size.width / b[2], size.height / b[3], 1.5f)
        sc = s; off = Offset((size.width - b[2] * s) / 2 - b[0] * s, (size.height - b[3] * s) / 2 - b[1] * s)
    }
    fun zoom(f: Float) {
        val ctr = Offset(size.width / 2f, size.height / 2f)
        sc = (sc * f).coerceIn(0.2f, 5f); off = ctr - (ctr - off) * f
    }
    LaunchedEffect(c, size) { fit() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ zoom(1.25f) }) { Text("+") }
        OutlinedButton({ zoom(0.8f) }) { Text("-") }
        OutlinedButton({ fit() }) { Text("Fit") }
    }
    Box(Modifier.fillMaxWidth().height(340.dp).border(1.dp, Color.LightGray).clipToBounds().background(Color.White)
        .onSizeChanged { size = it }
        .pointerInput(Unit) { detectTransformGestures { _, pan, z, _ -> sc = (sc * z).coerceIn(0.2f, 5f); off += pan } }
        .pointerInput(c) { detectTapGestures { p -> val w = (p - off) / sc; c.hit(w.x, w.y)?.let(onToggle) } }) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({ translate(off.x, off.y); scale(sc, sc, Offset.Zero) }) { drawCircuit(c, vals) }
        }
    }
}
