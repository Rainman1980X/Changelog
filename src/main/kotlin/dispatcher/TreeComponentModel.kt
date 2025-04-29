package dispatcher

class TreeComponentModel : ComponentModel {
    override fun process(p1: String) {
        println("TreeModel: added node with label '$p1'")
    }

    override fun process(p1: String, p2: Int) {
        println("TreeModel: added node '$p1' with $p2 children")
    }

    override fun process(p1: String, p2: Int, p3: Boolean) {
        println("TreeModel: added node '$p1' with $p2 children, expanded=$p3")
    }
}
