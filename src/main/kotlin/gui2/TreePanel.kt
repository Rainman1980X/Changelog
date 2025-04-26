package gui2

import java.awt.BorderLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class TreePanel(
    private val data: Map<String, Map<String, NodeInfo>>,
    private val onNodeSelected: (NodeInfo?) -> Unit
) : JPanel(BorderLayout()) {

    private val tree: JTree

    init {
        val root = TreeBuilder.buildTree(data)
        tree = JTree(root).apply {
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            rowHeight = 24
            font = font.deriveFont(16f)
        }

        tree.addTreeSelectionListener { e ->
            val node = e.path.lastPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is NodeInfo) {
                onNodeSelected(userObject)
            } else {
                onNodeSelected(null)
            }
        }

        add(JScrollPane(tree), BorderLayout.CENTER)
    }
}
