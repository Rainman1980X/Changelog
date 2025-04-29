package dispatcher

interface ComponentModel {
    fun process(p1: String)
    fun process(p1: String, p2: Int)
    fun process(p1: String, p2: Int, p3: Boolean)
}