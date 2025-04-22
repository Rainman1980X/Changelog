package parser

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory


fun parseAllDetails(xml: String): List<ParsedDetails> {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(InputSource(StringReader(xml)))

    val detailsNodes = doc.getElementsByTagName("Details")
    val result = mutableListOf<ParsedDetails>()

    for (i in 0 until detailsNodes.length) {
        val detailsElement = detailsNodes.item(i) as Element
        val id = detailsElement.getAttribute("id").toIntOrNull() ?: -1

        val bNodes = detailsElement.getElementsByTagName("b")
        val summaryText = if (bNodes.length > 0) {
            bNodes.item(0).textContent.trim()
        } else {
            ""
        }

        val dlNode = detailsElement.getElementsByTagName("dl").item(0) as Element
        val children = dlNode.childNodes
        val dlElements = mutableListOf<DlElement>()

        for (j in 0 until children.length) {
            val node = children.item(j)
            if (node is Element) {
                when (node.tagName) {
                    "dt" -> dlElements.add(DlElement.Dt(node.textContent.trim()))
                    "dd" -> dlElements.add(DlElement.Dd(node.textContent.trim()))
                }
            }
        }

        result.add(ParsedDetails(id, summaryText, dlElements, doc, detailsElement, dlNode))
    }

    return result
}
