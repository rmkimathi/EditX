package com.editx.app

import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var lineNumbers: TextView
    private lateinit var verticalScrollView: ScrollView
    private lateinit var horizontalScrollView: CustomHorizontalScrollView
    private var currentName = "Untitled"
    private var currentUri: android.net.Uri? = null
    private var dirty = false
    private var wrapping = true
    private var showLines = true
    private var syntax = "Plain text"
    private var internalChange = false
    private var previousText = ""
    private val undoStack = ArrayDeque<String>()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        if (uri != null) readDocument(uri)
    }
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) { currentUri = uri; currentName = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled"; writeDocument(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        editor = findViewById(R.id.editor); titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText); lineNumbers = findViewById(R.id.lineNumbers)
        verticalScrollView = findViewById(R.id.verticalScrollView)
        horizontalScrollView = findViewById(R.id.horizontalScrollView)
        editor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateLineNumbers()
        }
        findViewById<View>(R.id.menuButton).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.undoButton).setOnClickListener { undo() }
        findViewById<View>(R.id.findButton).setOnClickListener { findText() }
        findViewById<View>(R.id.replaceButton).setOnClickListener { replaceText() }
        findViewById<View>(R.id.wrapButton).setOnClickListener { toggleWrap() }
        findViewById<View>(R.id.linesButton).setOnClickListener { toggleLines() }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { if (!internalChange) previousText = s?.toString() ?: "" }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                if (!internalChange) { dirty = true; if (undoStack.isEmpty() || undoStack.last() != previousText) { undoStack.addLast(previousText); if (undoStack.size > 100) undoStack.removeFirst() } }
                updateLineNumbers(); updateTitle(); highlightSyntax(); updateStatus()
            }
        })
        updateLineNumbers(); updateStatus(); applyWrap()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        listOf("New", "Open", "Save", "Save As", "Undo", "Select All", "Copy", "Paste", "Word Wrap", "Line numbers", "Language / Syntax").forEachIndexed { i, label -> popup.menu.add(Menu.NONE, i, i, label) }
        popup.setOnMenuItemClickListener { item -> when (item.itemId) {
            0 -> newFile(); 1 -> openDocument.launch(arrayOf("*/*")); 2 -> saveFile(false); 3 -> saveFile(true); 4 -> undo()
            5 -> editor.selectAll(); 6 -> editor.onTextContextMenuItem(android.R.id.copy); 7 -> editor.onTextContextMenuItem(android.R.id.paste); 8 -> toggleWrap(); 9 -> toggleLines(); 10 -> chooseSyntax()
        }; true }
        popup.show()
    }

    private fun newFile() { if (dirty) confirmDiscard { editor.setText(""); currentUri = null; currentName = "Untitled"; dirty = false; undoStack.clear(); syntax = "Plain text"; updateTitle(); updateStatus() } else { editor.setText(""); currentUri = null; currentName = "Untitled"; dirty = false; undoStack.clear(); updateTitle() } }
    private fun confirmDiscard(action: () -> Unit) { AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Discard unsaved changes?").setNegativeButton("Cancel", null).setPositiveButton("Discard") { _, _ -> action() }.show() }
    private fun readDocument(uri: android.net.Uri) { try { val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""; internalChange = true; editor.setText(text); internalChange = false; currentUri = uri; currentName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"; dirty = false; undoStack.clear(); detectSyntax(); updateTitle(); updateStatus() } catch (e: Exception) { toast("Could not open file: ${e.message}") } }
    private fun saveFile(asNew: Boolean) { if (!asNew && currentUri != null) writeDocument(currentUri!!) else createDocument.launch(if (currentName == "Untitled") "untitled.txt" else currentName) }
    private fun writeDocument(uri: android.net.Uri) { try { contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(editor.text.toString()) }; dirty = false; updateTitle(); toast("Saved") } catch (e: Exception) { toast("Could not save: ${e.message}") } }
    private fun undo() { if (undoStack.isNotEmpty()) { val old = undoStack.removeLast(); internalChange = true; editor.setText(old); editor.setSelection(editor.text.length.coerceAtLeast(0)); internalChange = false; dirty = true; updateLineNumbers(); highlightSyntax(); updateTitle() } else toast("Nothing to undo") }
    private fun applyWrap() {
        horizontalScrollView.isWrapEnabled = wrapping
        editor.setHorizontallyScrolling(!wrapping)
        if (wrapping) {
            editor.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        findViewById<Button>(R.id.wrapButton).text = if (wrapping) "Wrap: On" else "Wrap: Off"
        editor.post { updateLineNumbers() }
    }

    private fun toggleWrap() {
        wrapping = !wrapping
        applyWrap()
    }
    private fun toggleLines() { showLines = !showLines; lineNumbers.visibility = if (showLines) View.VISIBLE else View.GONE; findViewById<Button>(R.id.linesButton).text = if (showLines) "Lines: On" else "Lines: Off" }
    private fun updateLineNumbers() {
        if (!::editor.isInitialized) return
        val text = editor.text ?: return
        val layout = editor.layout
        if (layout == null) {
            editor.post { updateLineNumbers() }
            return
        }

        val visualLineCount = layout.lineCount
        if (visualLineCount == 0) {
            lineNumbers.text = "1"
            return
        }

        val sb = StringBuilder()
        var currentLogicalLine = 1

        for (i in 0 until visualLineCount) {
            val startCharOffset = layout.getLineStart(i)
            val isLogicalStart = (i == 0 || (startCharOffset > 0 && text[startCharOffset - 1] == '\n'))
            if (isLogicalStart) {
                sb.append(currentLogicalLine)
                currentLogicalLine++
            }
            if (i < visualLineCount - 1) {
                sb.append("\n")
            }
        }

        lineNumbers.text = sb.toString()
    }
    private fun updateTitle() { titleText.text = "EditX · $currentName${if (dirty) " *" else ""}" }
    private fun updateStatus() { if (!::editor.isInitialized) return; val pos = editor.selectionStart.coerceAtLeast(0); val before = editor.text.substring(0, pos); val line = before.count { it == '\n' } + 1; val col = pos - (before.lastIndexOf('\n') + 1) + 1; statusText.text = "$syntax  ·  UTF-8  ·  Ln $line, Col $col${if (dirty) "  ·  Modified" else ""}" }
    private fun smoothScrollToPosition(at: Int) {
        editor.post {
            val layout = editor.layout
            if (layout != null && at in 0..editor.length()) {
                val line = layout.getLineForOffset(at)
                val y = layout.getLineTop(line)
                val x = layout.getPrimaryHorizontal(at).toInt()
                verticalScrollView.smoothScrollTo(0, y)
                horizontalScrollView.smoothScrollTo(x, 0)
            }
        }
    }
    private fun countMatches(query: String): Int {
        if (query.isEmpty()) return 0
        val text = editor.text.toString()
        var count = 0
        var pos = 0
        while (true) {
            val i = text.indexOf(query, pos, ignoreCase = true)
            if (i < 0) break
            count++
            pos = i + query.length.coerceAtLeast(1)
        }
        return count
    }

    private fun findNext(query: String) {
        if (query.isEmpty()) return
        val text = editor.text.toString()
        val start = editor.selectionEnd.coerceAtLeast(0)
        var at = text.indexOf(query, start, ignoreCase = true)
        if (at < 0) {
            at = text.indexOf(query, 0, ignoreCase = true)
        }
        if (at >= 0) {
            editor.requestFocus()
            editor.setSelection(at, at + query.length)
            smoothScrollToPosition(at)
        } else {
            toast("Not found")
        }
    }

    private fun findPrev(query: String) {
        if (query.isEmpty()) return
        val text = editor.text.toString()
        val start = (editor.selectionStart - 1).coerceAtLeast(0)
        var at = if (start >= 0) text.lastIndexOf(query, start, ignoreCase = true) else -1
        if (at < 0) {
            at = text.lastIndexOf(query, text.length, ignoreCase = true)
        }
        if (at >= 0) {
            editor.requestFocus()
            editor.setSelection(at, at + query.length)
            smoothScrollToPosition(at)
        } else {
            toast("Not found")
        }
    }

    private fun findText() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 12)
        }

        val input = EditText(this).apply {
            hint = "Find text"
            isSingleLine = true
        }

        val statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF718096.toInt())
            setPadding(0, 12, 0, 12)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val prevBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "◄ Prev"
        }

        val nextBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Next ►"
        }

        buttonRow.addView(prevBtn)
        buttonRow.addView(nextBtn)

        layout.addView(input)
        layout.addView(statusText)
        layout.addView(buttonRow)

        fun updateStatus(q: String) {
            val total = countMatches(q)
            if (q.isEmpty()) {
                statusText.text = ""
            } else if (total == 0) {
                statusText.text = "No matches"
            } else {
                val text = editor.text.toString()
                val selStart = editor.selectionStart
                var currentMatch = 0
                var pos = 0
                var matchIdx = 1
                while (true) {
                    val i = text.indexOf(q, pos, ignoreCase = true)
                    if (i < 0) break
                    if (i <= selStart) {
                        currentMatch = matchIdx
                    }
                    matchIdx++
                    pos = i + q.length.coerceAtLeast(1)
                }
                if (currentMatch == 0) currentMatch = 1
                statusText.text = "Match $currentMatch of $total"
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                if (q.isNotEmpty()) {
                    val start = editor.selectionStart.coerceAtLeast(0)
                    var at = editor.text.indexOf(q, start, true)
                    if (at < 0) at = editor.text.indexOf(q, 0, true)
                    if (at >= 0) {
                        editor.requestFocus()
                        editor.setSelection(at, at + q.length)
                        smoothScrollToPosition(at)
                    }
                }
                updateStatus(q)
            }
        })

        prevBtn.setOnClickListener {
            val q = input.text.toString()
            findPrev(q)
            updateStatus(q)
        }

        nextBtn.setOnClickListener {
            val q = input.text.toString()
            findNext(q)
            updateStatus(q)
        }

        AlertDialog.Builder(this)
            .setTitle("Find")
            .setView(layout)
            .setNegativeButton("Done", null)
            .show()

        val selectedText = editor.text.substring(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))
        if (selectedText.isNotEmpty() && !selectedText.contains('\n')) {
            input.setText(selectedText)
            input.selectAll()
        }
    }
    private fun replaceText() { val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(24, 8, 24, 0); val find = EditText(this); find.hint = "Find"; val repl = EditText(this); repl.hint = "Replace with"; box.addView(find); box.addView(repl); AlertDialog.Builder(this).setTitle("Replace").setView(box).setNegativeButton("Cancel", null).setNeutralButton("Replace") { _, _ -> val q = find.text.toString(); if (q.isNotEmpty()) { val i = editor.text.indexOf(q, editor.selectionStart.coerceAtLeast(0), true); if (i >= 0) { editor.text.replace(i, i + q.length, repl.text); editor.setSelection((i + repl.length()).coerceAtMost(editor.length())); smoothScrollToPosition(i) } else toast("Not found") } }.setPositiveButton("Replace all") { _, _ -> val q = find.text.toString(); if (q.isNotEmpty()) editor.setText(editor.text.toString().replace(q, repl.text.toString(), true)) }.show() }
    private fun chooseSyntax() { val languages = arrayOf("Auto detect", "Plain text", "C / C++", "C#", "Java", "Kotlin", "XML", "HTML", "JavaScript", "Markdown", "PHP", "Perl", "Python", "Ruby", "Smali", "Swift", "JSON", "Shell") ; AlertDialog.Builder(this).setTitle("Language / Syntax").setItems(languages) { _, which -> syntax = languages[which]; if (syntax == "Auto detect") detectSyntax(); highlightSyntax(); updateStatus() }.show() }
    private fun detectSyntax() { val n = currentName.lowercase(); syntax = when { n.endsWith(".kt") -> "Kotlin"; n.endsWith(".java") -> "Java"; n.endsWith(".xml") -> "XML"; n.endsWith(".html") || n.endsWith(".htm") -> "HTML"; n.endsWith(".js") -> "JavaScript"; n.endsWith(".py") -> "Python"; n.endsWith(".md") -> "Markdown"; n.endsWith(".php") -> "PHP"; n.endsWith(".c") || n.endsWith(".cpp") || n.endsWith(".h") -> "C / C++"; n.endsWith(".cs") -> "C#"; n.endsWith(".rb") -> "Ruby"; n.endsWith(".pl") -> "Perl"; n.endsWith(".swift") -> "Swift"; n.endsWith(".smali") -> "Smali"; n.endsWith(".json") -> "JSON"; n.endsWith(".sh") -> "Shell"; else -> "Plain text" } }
    private fun highlightSyntax() { if (!::editor.isInitialized || syntax == "Plain text") return; val e = editor.text; val patterns = listOf(Regex("//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/|<!--([\\s\\S]*?)-->") to 0xFF718096.toInt(), Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'") to 0xFF13805C.toInt(), Regex("\\b(fun|class|object|def|function|return|if|else|for|while|when|val|var|const|let|int|float|double|string|public|private|static|void|new|import|package|true|false|null|None|self|this|extends|implements|try|catch|throw|async|await)\\b") to 0xFF7C3AED.toInt(), Regex("\\b[0-9]+(\\.[0-9]+)?\\b") to 0xFFB45309.toInt())
        // Rebuild highlighting spans while preserving the editable text.
        val selectionStart = editor.selectionStart; val selectionEnd = editor.selectionEnd
        e.getSpans(0, e.length, ForegroundColorSpan::class.java).forEach { e.removeSpan(it) }
        patterns.forEach { (regex, color) -> regex.findAll(e).forEach { e.setSpan(ForegroundColorSpan(color), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } }
        if (selectionStart >= 0 && selectionEnd >= 0) try { editor.setSelection(selectionStart.coerceAtMost(e.length), selectionEnd.coerceAtMost(e.length)) } catch (_: Exception) { }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
