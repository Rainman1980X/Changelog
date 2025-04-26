package gui2

// MainFrame.kt
//
// Hauptfenster der modularen Swing-GUI
// – oben:   unabhängiges Info-Panel
// – links:  JTree-Navigation
// – rechts: DetailView
// – JSplitPane bleibt stabil; rechter Teil wächst beim Resizing
//
import java.awt.*
import javax.swing.*

class MainFrame : JFrame("Trading-Assistant") {

    /* ------------------------------------------------------------------ */
    /* Panels                                                             */
    /* ------------------------------------------------------------------ */

    private val topInfoPanel = TopInfoPanel()
    private val detailView   = DetailView()

    /* ------------------------------------------------------------------ */
    /* Konstruktor                                                        */
    /* ------------------------------------------------------------------ */

    init {
        /* Basis-Fenster */
        defaultCloseOperation = EXIT_ON_CLOSE
        layout                = BorderLayout(0, 0)
        size                  = Dimension(900, 600)
        minimumSize           = Dimension(650, 450)
        setLocationRelativeTo(null)          // zentrieren

        /* Demo-Daten & Tree-Panel */
        val treePanel = TreePanel(createSampleData()) { sel ->
            if (sel != null) {
                detailView.update(sel)
                topInfoPanel.updateInfo("Ausgewählt: ${sel.title}")
            } else {
                detailView.clear()
                topInfoPanel.updateInfo("Bitte wähle ein Element.")
            }
        }

        treePanel.minimumSize  = Dimension(230, 100)
        detailView.minimumSize = Dimension(350, 100)

        /* SplitPane */
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, detailView).apply {
            dividerLocation      = 260           // Startbreite links
            isContinuousLayout   = true          // flüssiges Ziehen
            resizeWeight         = 0.0           // nur rechter Teil wächst
            border               = BorderFactory.createEmptyBorder()
        }

        /* Einbau in Frame */
        add(topInfoPanel, BorderLayout.NORTH)
        add(split,        BorderLayout.CENTER)

        /* FlatLaf liefert den subtilen Resize-Grip unten rechts           */
    }

    /* ------------------------------------------------------------------ */
    /* Demo-Daten                                                         */
    /* ------------------------------------------------------------------ */

    private fun createSampleData(): Map<String, Map<String, NodeInfo>> = mapOf(
        "Fruits" to mapOf(
            "Apple"  to NodeInfo(
                "Apple", "A juicy red fruit.",
                mutableListOf("Sweet", "Crunchy", "Healthy"),
                mutableListOf("Grows on trees", "Popular worldwide")
            ),
            "Banana" to NodeInfo(
                "Banana", "A long yellow fruit.",
                mutableListOf("Soft", "Sweet"),
                mutableListOf("Rich in potassium")
            )
        ),
        "Vegetables" to mapOf(
            "Carrot" to NodeInfo(
                "Carrot", "An orange root vegetable.",
                mutableListOf("Crunchy", "Sweet"),
                mutableListOf("Good for eyes", "Rich in Vitamin A")
            )
        )
    )
}
