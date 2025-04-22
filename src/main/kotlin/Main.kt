package org.example

import parser.addDdElement
import parser.parseAndModifyDetails
import parser.writeXml

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val name = "Kotlin"
    //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
    // to see how IntelliJ IDEA suggests fixing it.
    println("Hello, " + name + "!")

    for (i in 1..5) {
        //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
        // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
        println("i = $i")
    }
}

fun main1() {
    val inputXml = """
        <Details id="1">
            <summary><b>Optionaler Titel</b></summary>
            <dl>
                <dt>Begriff 1</dt>
                <dd>Definition 1a</dd>
                <dd>Definition 1b</dd>
                <dt>Begriff 2</dt>
                <dd>Definition 2a</dd>
            </dl>
        </Details>
    """.trimIndent()

    val parsed = parseAndModifyDetails(inputXml)

    println("ID: ${parsed.id}")
    println("Summary: '${parsed.summary}'")
    parsed.dlElements.forEach { println(it) }

    addDdElement(parsed.dom, parsed.dlNode, "neu hinzugefügt")

    val outputXml = writeXml(parsed.dom)
    println("\n--- Geändertes XML ---\n$outputXml")
}