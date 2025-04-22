package parser


import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.transform.*
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun addDdElement(doc: Document, dlNode: Element, value: String) {
    val newDd = doc.createElement("dd")
    newDd.textContent = value
    dlNode.appendChild(newDd)
}

fun writeXml(doc: Document): String {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

    val writer = java.io.StringWriter()
    transformer.transform(DOMSource(doc), StreamResult(writer))
    return writer.toString()
}
