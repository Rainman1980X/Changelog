package dispatcher

class Dispatcher(private val target: ComponentModel) {

    fun dispatch(p1: String, p2: Int? = null, p3: Boolean? = null) {
        when {
            p2 == null && p3 == null -> target.process(p1)
            p2 != null && p3 == null -> target.process(p1, p2)
            p2 != null && p3 != null -> target.process(p1, p2, p3)
            else -> throw IllegalArgumentException("Invalid parameter combination")
        }
    }
}
