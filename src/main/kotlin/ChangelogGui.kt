
import java.awt.*
import javax.swing.*
import java.io.File

data class ChangeItem(val type: String, val description: String)
data class ChangelogEntry(val version: String, val date: String, val entries: List<ChangeItem>)

fun parseChangelogFile(filePath: String): List<ChangelogEntry> {
    val lines = File(filePath).readLines()
    val changelogEntries = mutableListOf<ChangelogEntry>()
    var currentVersion: String? = null
    var currentDate: String? = null
    val currentItems = mutableListOf<ChangeItem>()

    var insideDetails = false
    var insideDl = false
    var currentType: String? = null

    for (line in lines) {
        when {
            line.startsWith("## ") -> {
                currentVersion = line.removePrefix("## ").trim()
            }
            line.contains("<details>") -> {
                insideDetails = true
                currentItems.clear()
            }
            line.contains("</details>") -> {
                if (currentVersion != null && currentDate != null) {
                    changelogEntries += ChangelogEntry(
                        version = currentVersion,
                        date = currentDate!!,
                        entries = currentItems.toList()
                    )
                }
                insideDetails = false
                insideDl = false
                currentDate = null
                currentType = null
                currentItems.clear()
            }
            insideDetails && line.contains("<summary>") -> {
                currentDate = line.replace("<summary>", "").replace("</summary>", "").trim()
            }
            insideDetails && line.contains("<dl>") -> {
                insideDl = true
            }
            insideDetails && line.contains("</dl>") -> {
                insideDl = false
            }
            insideDl && line.contains("<dt>") -> {
                currentType = line.replace("<dt>", "").replace("</dt>", "").trim()
            }
            insideDl && line.contains("<dd>") -> {
                val description = line.replace("<dd>", "").replace("</dd>", "").trim()
                if (currentType != null) {
                    currentItems += ChangeItem(currentType!!, description)
                    currentType = null
                }
            }
        }
    }

    return changelogEntries
}

fun appendChangelogEntry(filePath: String, version: String, date: String, items: List<ChangeItem>) {
    val lines = File(filePath).readLines().toMutableList()
    val insertIndex = lines.indexOfFirst { it.trim() == "## $version" }

    val newBlock = buildString {
        appendLine("<details>")
        appendLine("<summary>$date</summary>")
        appendLine("<dl>")
        items.forEach {
            appendLine("  <dt>${it.type}</dt>")
            appendLine("  <dd>${it.description}</dd>")
        }
        appendLine("</dl>")
        appendLine("</details>")
        appendLine()
    }

    if (insertIndex >= 0) {
        var index = insertIndex + 1
        while (index < lines.size && !lines[index].startsWith("## ")) {
            index++
        }
        lines.add(index, newBlock.trimEnd())
    } else {
        lines.add("")
        lines.add("## $version")
        lines.add(newBlock.trimEnd())
    }

    File(filePath).writeText(lines.joinToString("\n"))
}

fun openChangelogGui() {
    //val filePath = "sample_changelog.md"
    val filePath = ChangelogEntry::class.java.getResource("/sample_changelog.md")?.path
        ?: "src/sample_changelog.md"

    val changelog = parseChangelogFile(filePath).toMutableList()

    val frame = JFrame("CHANGELOG Editor")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(800, 600)
    frame.layout = BorderLayout()

    val changelogListModel = DefaultListModel<ChangelogEntry>()
    changelog.forEach { changelogListModel.addElement(it) }

    val changelogList = JList(changelogListModel)
    changelogList.selectionMode = ListSelectionModel.SINGLE_SELECTION

    val changelogScroll = JScrollPane(changelogList)

    val detailArea = JTextArea()
    detailArea.lineWrap = true
    detailArea.isEditable = false
    val detailScroll = JScrollPane(detailArea)

    changelogList.addListSelectionListener {
        val selected = changelogList.selectedValue
        if (selected != null) {
            detailArea.text = buildString {
                appendLine("Version: ${selected.version}")
                appendLine("Datum: ${selected.date}")
                selected.entries.forEach {
                    appendLine("- ${it.type}: ${it.description}")
                }
            }
        }
    }

    val versionField = JTextField()
    val dateField = JTextField()
    val typeField = JTextField()
    val descField = JTextField()
    val entryModel = DefaultListModel<ChangeItem>()
    val entryList = JList(entryModel)

    val addEntryButton = JButton("Eintrag hinzufügen")
    addEntryButton.addActionListener {
        val type = typeField.text.trim()
        val desc = descField.text.trim()
        if (type.isNotEmpty() && desc.isNotEmpty()) {
            entryModel.addElement(ChangeItem(type, desc))
            typeField.text = ""
            descField.text = ""
        }
    }

    val saveButton = JButton("In Datei speichern")
    saveButton.addActionListener {
        val version = versionField.text.trim()
        val date = dateField.text.trim()
        if (version.isEmpty() || date.isEmpty() || entryModel.isEmpty) {
            JOptionPane.showMessageDialog(frame, "Alle Felder ausfüllen und Einträge hinzufügen!", "Fehler", JOptionPane.ERROR_MESSAGE)
            return@addActionListener
        }

        val entries = (0 until entryModel.size()).map { entryModel[it] }
        appendChangelogEntry(filePath, version, date, entries)

        changelogListModel.addElement(ChangelogEntry(version, date, entries))
        entryModel.clear()
        versionField.text = ""
        dateField.text = ""
        JOptionPane.showMessageDialog(frame, "Änderung gespeichert.")
    }

    val formPanel = JPanel(GridLayout(5, 2))
    formPanel.border = BorderFactory.createTitledBorder("Neuer Eintrag")
    formPanel.add(JLabel("Version:"))
    formPanel.add(versionField)
    formPanel.add(JLabel("Datum:"))
    formPanel.add(dateField)
    formPanel.add(JLabel("Typ:"))
    formPanel.add(typeField)
    formPanel.add(JLabel("Beschreibung:"))
    formPanel.add(descField)
    formPanel.add(addEntryButton)
    formPanel.add(saveButton)

    val rightPanel = JPanel(BorderLayout())
    rightPanel.add(detailScroll, BorderLayout.CENTER)
    rightPanel.add(formPanel, BorderLayout.SOUTH)

    frame.add(changelogScroll, BorderLayout.WEST)
    frame.add(rightPanel, BorderLayout.CENTER)

    frame.isVisible = true
}

fun main() {
    SwingUtilities.invokeLater {
        openChangelogGui()
    }
}
