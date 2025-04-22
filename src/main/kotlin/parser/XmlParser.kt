package parser

import org.w3c.dom.Element
import org.w3c.dom.Document
import org.xml.sax.InputSource
import parser.DlElement
import parser.ParsedDetails
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

fun parseAndModifyDetails(xml: String): ParsedDetails {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(InputSource(StringReader(xml)))

    val detailsElement = doc.documentElement
    val id = detailsElement.getAttribute("id").toIntOrNull() ?: -1

    val bNodes = detailsElement.getElementsByTagName("b")
    val summaryText = if (bNodes.length > 0) {
        bNodes.item(0).textContent.trim()
    } else {
        ""
    }

    val dlElements = mutableListOf<DlElement>()
    val dlNode = detailsElement.getElementsByTagName("dl").item(0) as Element
    val children = dlNode.childNodes

    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is Element) {
            when (node.tagName) {
                "dt" -> dlElements.add(DlElement.Dt(node.textContent.trim()))
                "dd" -> dlElements.add(DlElement.Dd(node.textContent.trim()))
            }
        }
    }

    return ParsedDetails(id, summaryText, dlElements, doc, dlNode)
}
