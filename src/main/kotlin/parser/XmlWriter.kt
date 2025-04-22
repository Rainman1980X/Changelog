package parser

import org.w3c.dom.Element
import javax.xml.transform.*
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


fun addDdToDetails(parsed: ParsedDetails, value: String) {
    val newDd = parsed.dom.createElement("dd")
    newDd.textContent = value
    parsed.dlNode.appendChild(newDd)
}

fun writeDetailsElement(detailsElement: Element): String {
    val transformer = TransformerFactory.newInstance().newTransformer()
//    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
//    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") // kein <?xml ...?>

    val writer = java.io.StringWriter()
    transformer.transform(DOMSource(detailsElement), StreamResult(writer))
    return writer.toString()
}

