package gui2

import javax.swing.tree.DefaultMutableTreeNode

object TreeBuilder {
    fun buildTree(data: Map<String, Map<String, NodeInfo>>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Root")
        data.forEach { (category, items) ->
            val categoryNode = DefaultMutableTreeNode(category)
            items.forEach { (itemName, nodeInfo) ->
                val itemNode = DefaultMutableTreeNode(nodeInfo)
                categoryNode.add(itemNode)
            }
            root.add(categoryNode)
        }
        return root
    }
}

