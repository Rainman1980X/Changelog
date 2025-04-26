package gui2

import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class DetailView : JPanel(GridBagLayout()) {

    /* ------------------------------------------------------ */
    /* interne Daten                                         */
    /* ------------------------------------------------------ */

    private var currentInfo: NodeInfo? = null

    /* Modelle für die Listen */
    private val list1Model = DefaultListModel<String>()
    private val list2Model = DefaultListModel<String>()

    /* ------------------------------------------------------ */
    /* UI-Komponenten                                         */
    /* ------------------------------------------------------ */

    private val titleLabel = JLabel().apply {
        font = Font("Arial", Font.BOLD, 22)
    }

    private val descArea = JTextArea().apply {
        lineWrap = true;  wrapStyleWord = true;  isEditable = false
    }
    private val descScroll = JScrollPane(descArea).apply {
        preferredSize = Dimension(10, 90)
        maximumSize   = Dimension(Int.MAX_VALUE, 110)
        minimumSize   = Dimension(10, 70)
    }

    private val list1 = JList(list1Model)
    private val list2 = JList(list2Model)

    init {
        border = EmptyBorder(12, 12, 12, 12)
        buildLayout()
        installInteractions(list1, list1Model, { currentInfo?.list1 })
        installInteractions(list2, list2Model, { currentInfo?.list2 })
    }

    /* ------------------------------------------------------ */
    /* Layout-Helfer                                          */
    /* ------------------------------------------------------ */

    private fun buildLayout() {
        val gbc = GridBagConstraints().apply {
            gridwidth = GridBagConstraints.REMAINDER
            insets    = Insets(4, 4, 4, 4)
            anchor    = GridBagConstraints.NORTHWEST
            fill      = GridBagConstraints.HORIZONTAL
            weightx   = 1.0
        }

        add(titleLabel, gbc)

        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        add(descScroll, gbc)

        /* ---------- List 1 ---------- */
        add(sectionHeader("List 1") { addItem(list1Model, currentInfo?.list1) }, gbc)

        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 0.5
        add(JScrollPane(list1), gbc)

        /* ---------- List 2 ---------- */
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        add(sectionHeader("List 2") { addItem(list2Model, currentInfo?.list2) }, gbc)

        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 0.5
        add(JScrollPane(list2), gbc)
    }

    private fun sectionHeader(text: String, onAdd: () -> Unit) =
        JPanel(BorderLayout()).apply {
            add(JLabel(text), BorderLayout.WEST)
            add(JButton("+").apply {
                toolTipText = "Eintrag hinzufügen"
                margin = Insets(2, 6, 2, 6)
                addActionListener { onAdd() }
            }, BorderLayout.EAST)
        }

    /* ------------------------------------------------------ */
    /* Interaktionen (Add / Edit / Delete)                    */
    /* ------------------------------------------------------ */

    private fun addItem(model: DefaultListModel<String>, backing: MutableList<String>?) {
        val input = JOptionPane.showInputDialog(this, "Neuer Eintrag:", "")
        val txt   = input?.trim().orEmpty()
        if (txt.isNotEmpty()) {
            model.addElement(txt)
            backing?.add(txt)
        }
    }

    private fun editItem(list: JList<String>, model: DefaultListModel<String>,
                         backing: MutableList<String>?) {
        val idx = list.selectedIndex.takeIf { it >= 0 } ?: return
        val old = model.get(idx)
        val input = JOptionPane.showInputDialog(this, "Eintrag bearbeiten:", old) ?: return
        val txt   = input.trim()
        if (txt.isNotEmpty()) {
            model.set(idx, txt)
            backing?.set(idx, txt)
        }
    }

    private fun deleteItem(list: JList<String>, model: DefaultListModel<String>,
                           backing: MutableList<String>?) {
        val idx = list.selectedIndex.takeIf { it >= 0 } ?: return
        if (JOptionPane.showConfirmDialog(
                this, "Eintrag löschen?", "Löschen",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            model.remove(idx)
            backing?.removeAt(idx)
        }
    }

    /** Kontextmenü & Tastenkürzel für eine JList */
    private fun installInteractions(
        list: JList<String>,
        model: DefaultListModel<String>,
        backingProvider: () -> MutableList<String>?
    ) {
        /* --- Kontextmenü --- */
        val popup = JPopupMenu().apply {
            val edit   = JMenuItem("Bearbeiten").also { add(it) }
            val delete = JMenuItem("Löschen").also  { add(it) }
            edit.addActionListener { editItem(list, model, backingProvider()) }
            delete.addActionListener{ deleteItem(list, model, backingProvider()) }
        }
        list.componentPopupMenu = popup

        /* --- Tastatur --- */
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DELETE -> deleteItem(list, model, backingProvider())
                    KeyEvent.VK_F2     -> editItem  (list, model, backingProvider())
                }
            }
        })

        /* --- Doppelklick -> Edit --- */
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e))
                    editItem(list, model, backingProvider())
            }
        })
    }

    /* ------------------------------------------------------ */
    /* Öffentliche API                                        */
    /* ------------------------------------------------------ */

    fun update(info: NodeInfo) {
        currentInfo = info
        titleLabel.text = info.title
        descArea.text   = info.description

        list1Model.apply { clear(); info.list1.forEach(::addElement) }
        list2Model.apply { clear(); info.list2.forEach(::addElement) }
    }

    fun clear() {
        currentInfo = null
        titleLabel.text = ""; descArea.text = ""
        list1Model.clear();  list2Model.clear()
    }
}

