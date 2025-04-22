package parser

import org.w3c.dom.Document
import org.w3c.dom.Element

data class ParsedDetails(
    val id: Int,
    val summary: String,
    val dlElements: List<DlElement>,
    val dom: Document,
    val dlNode: Element
)