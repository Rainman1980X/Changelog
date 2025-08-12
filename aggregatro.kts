import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import org.w3c.dom.Document
import org.w3c.dom.Element

val spotbugsAggregateDir = layout.buildDirectory.dir("reports/spotbugs/aggregate")

// Merge all XML reports into one XML
val spotbugsAggregateXml = tasks.register("spotbugsAggregateXml") {
    group = "verification"
    description = "Merge all SpotBugs XML reports into a single XML."

    subprojects.forEach { sp ->
        dependsOn("${sp.path}:spotbugsMain")
        dependsOn("${sp.path}:spotbugsTest")
    }

    doLast {
        val xmlFiles = subprojects.flatMap { sp ->
            fileTree("${sp.buildDir}/reports/spotbugs") {
                include("*.xml")
            }.files
        }.filter { it.exists() }

        if (xmlFiles.isEmpty()) {
            logger.lifecycle("No SpotBugs XML files found.")
            return@doLast
        }

        val outDir = spotbugsAggregateDir.get().asFile.apply { mkdirs() }
        val mergedXml = File(outDir, "spotbugs-aggregate.xml")

        val dbf = DocumentBuilderFactory.newInstance()
        val doc: Document = dbf.newDocumentBuilder().newDocument()
        val root: Element = doc.createElement("BugCollection")
        doc.appendChild(root)

        val seen = mutableSetOf<String>()

        xmlFiles.forEach { f ->
            val parsed = dbf.newDocumentBuilder().parse(f)
            val instances = parsed.getElementsByTagName("BugInstance")
            for (i in 0 until instances.length) {
                val node = instances.item(i)
                val type = node.attributes?.getNamedItem("type")?.nodeValue ?: ""
                var clazz = ""
                var method = ""
                var line = ""
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val ch = children.item(j)
                    when (ch.nodeName) {
                        "Class" -> clazz = ch.attributes?.getNamedItem("classname")?.nodeValue ?: clazz
                        "Method" -> method = ch.attributes?.getNamedItem("name")?.nodeValue ?: method
                        "SourceLine" -> line = ch.attributes?.getNamedItem("start")?.nodeValue ?: line
                    }
                }
                val key = "$type@$clazz@$method@$line"
                if (seen.add(key)) {
                    val imported = doc.importNode(node, true)
                    root.appendChild(imported)
                }
            }
        }

        TransformerFactory.newInstance().newTransformer()
            .transform(DOMSource(doc), StreamResult(mergedXml))

        logger.lifecycle("Merged SpotBugs XML written to: ${mergedXml.absolutePath}")
    }
}

// Convert the merged XML into HTML
val spotbugsAggregateHtml = tasks.register("spotbugsAggregateHtml") {
    group = "verification"
    description = "Convert aggregated SpotBugs XML into HTML."
    dependsOn(spotbugsAggregateXml)

    doLast {
        val outDir = spotbugsAggregateDir.get().asFile
        val xmlFile = File(outDir, "spotbugs-aggregate.xml")
        if (!xmlFile.exists()) {
            logger.lifecycle("No aggregated XML found.")
            return@doLast
        }
        val htmlFile = File(outDir, "spotbugs-aggregate.html")
        val xslFile = File(outDir, "spotbugs-simple.xsl")

        // Simple XSLT template
        val stylesheet = """
            <xsl:stylesheet version="1.0"
              xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="html" indent="yes"/>
              <xsl:template match="/">
                <html>
                  <head><meta charset="UTF-8"/><title>SpotBugs Aggregate</title></head>
                  <body>
                    <h1>SpotBugs Aggregated Report</h1>
                    <table border="1" cellpadding="4" cellspacing="0">
                      <tr>
                        <th>Type</th><th>Class</th><th>Method</th><th>Line</th><th>Priority</th>
                      </tr>
                      <xsl:for-each select="//BugInstance">
                        <tr>
                          <td><xsl:value-of select="@type"/></td>
                          <td><xsl:value-of select="Class/@classname"/></td>
                          <td><xsl:value-of select="Method/@name"/></td>
                          <td><xsl:value-of select="SourceLine/@start"/></td>
                          <td><xsl:value-of select="@priority"/></td>
                        </tr>
                      </xsl:for-each>
                    </table>
                    <p>Total findings: <xsl:value-of select="count(//BugInstance)"/></p>
                  </body>
                </html>
              </xsl:template>
            </xsl:stylesheet>
        """.trimIndent()

        xslFile.writeText(stylesheet)

        val tf = TransformerFactory.newInstance()
        tf.newTransformer(StreamSource(xslFile))
            .transform(StreamSource(xmlFile), StreamResult(htmlFile))

        logger.lifecycle("Aggregated SpotBugs HTML written to: ${htmlFile.absolutePath}")
    }
}

// One task to run everything
tasks.register("spotbugsAggregate") {
    group = "verification"
    description = "Run SpotBugs on all subprojects and produce a single aggregated XML & HTML."
    dependsOn(spotbugsAggregateHtml)
}
