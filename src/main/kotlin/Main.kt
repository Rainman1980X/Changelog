package org.example

import parser.parseAllDetails
import parser.writeDetailsElement

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main2() {
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

fun main() {
    val inputXml = """
        <ChangeLog>
            <Details id="1">
                <summary><b>Eintrag Eins</b></summary>
                <dl>
                    <dt>Begriff A</dt>
                    <dd>Definition A1</dd>
                </dl>
            </Details>
            <Details id="2">
                <summary><b>Eintrag Zwei</b></summary>
                <dl>
                    <dt>Begriff B</dt>
                    <dd>Definition B1</dd>
                </dl>
            </Details>
        </ChangeLog>
    """.trimIndent()

    val parsedList = parseAllDetails(inputXml)

    println("Gefundene Details:")
    parsedList.forEach {
        println("ID: ${it.id} — Summary: '${it.summary}'")
        it.dlElements.forEach { el -> println("  $el") }
    }

    // Beispiel: Füge einem Eintrag mit ID = 2 ein neues <dd> hinzu
    val detailsToOutput = parsedList.find { it.id == 2 }
    detailsToOutput?.let {
        println("--- Nur das <Details>-Element als String ---")
        println(writeDetailsElement(it.detailsElement))
    }
}