package gui2

import java.awt.BorderLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class TopInfoPanel : JPanel(BorderLayout()) {
    val infoArea = JTextArea(3, 20).apply {
        text = "Hier können unabhängige Infos angezeigt werden..."
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
        font = font.deriveFont(14f)
        border = EmptyBorder(10, 10, 10, 10)
    }

    init {
        add(JScrollPane(infoArea), BorderLayout.CENTER)
    }

    fun updateInfo(text: String) {
        infoArea.text = text
    }
}
