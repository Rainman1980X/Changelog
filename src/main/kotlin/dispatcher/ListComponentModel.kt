package dispatcher

class ListComponentModel : ComponentModel {
    override fun process(p1: String) {
        println("ListModel: added entry '$p1'")
    }

    override fun process(p1: String, p2: Int) {
        println("ListModel: added entry '$p1' at position $p2")
    }

    override fun process(p1: String, p2: Int, p3: Boolean) {
        println("ListModel: added entry '$p1' at position $p2, selected=$p3")
    }
}
