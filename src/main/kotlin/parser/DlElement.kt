package parser

sealed class DlElement {
    data class Dt(val value: String) : DlElement()
    data class Dd(val value: String) : DlElement()
}
