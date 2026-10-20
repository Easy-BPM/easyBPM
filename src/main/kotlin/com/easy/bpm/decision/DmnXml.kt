package com.easy.bpm.decision

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** The supported DMN subset is deliberately closed: unsupported semantic elements fail import. */
@Component
class DmnXml {
    companion object { const val NS = "https://www.omg.org/spec/DMN/20191111/MODEL/" }
    fun parse(xml: String): DecisionTable {
        if (xml.toByteArray().size > 1_000_000) throw DecisionException("LIMIT", "DMN XML exceeds 1 MB")
        try {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
            }.newDocumentBuilder().apply {
                setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                    override fun error(e: org.xml.sax.SAXParseException) { throw e }
                    override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                })
            }.parse(xml.byteInputStream())
            val root = document.documentElement
            require(root.localName == "definitions" && root.namespaceURI == NS) { "Expected DMN 1.3 definitions namespace" }
            val allowed = setOf("definitions", "decision", "decisionTable", "input", "inputExpression", "output", "rule", "inputEntry", "outputEntry", "text", "description")
            val children = mapOf("definitions" to setOf("decision"), "decision" to setOf("decisionTable", "description"), "decisionTable" to setOf("input", "output", "rule"), "input" to setOf("inputExpression", "description"), "inputExpression" to setOf("text"), "output" to setOf("description"), "rule" to setOf("inputEntry", "outputEntry", "description"), "inputEntry" to setOf("text"), "outputEntry" to setOf("text"), "text" to emptySet(), "description" to emptySet())
            val attributes = mapOf("definitions" to setOf("id", "name", "namespace"), "decision" to setOf("id", "name"), "decisionTable" to setOf("id", "hitPolicy"), "input" to setOf("id", "label"), "inputExpression" to setOf("id", "typeRef"), "output" to setOf("id", "name", "label", "typeRef"), "rule" to setOf("id"), "inputEntry" to setOf("id"), "outputEntry" to setOf("id"), "text" to emptySet(), "description" to emptySet())
            val ids = mutableSetOf<String>()
            val all = document.getElementsByTagName("*")
            for (i in 0 until all.length) {
                val e = all.item(i) as Element
                require(e.namespaceURI == NS && e.localName in allowed) { "Unsupported DMN element" }
                (0 until e.attributes.length).forEach { j ->
                    val attribute = e.attributes.item(j)
                    require(attribute.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI || attribute.nodeName in attributes.getValue(e.localName)) { "Unsupported DMN attribute" }
                }
                if (e.hasAttribute("id")) require(e.getAttribute("id").isNotBlank() && ids.add(e.getAttribute("id"))) { "Duplicate or empty DMN identifier" }
                (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.forEach { child -> require(child.localName in children.getValue(e.localName)) { "Unsupported DMN element structure" } }
                if (e.localName in setOf("inputExpression", "inputEntry", "outputEntry")) require(e.children("text").size == 1) { "Exactly one expression text is required" }
            }
            val decisions = root.children("decision")
            require(decisions.size == 1) { "Exactly one decision is supported" }
            val d = decisions.single()
            val tables = d.children("decisionTable")
            require(tables.size == 1) { "Exactly one decision table is required" }
            val t = tables.single()
            val inputs = t.children("input").map { i ->
                val expression = i.children("inputExpression").singleOrNull() ?: error("Input expression required")
                DecisionInput(i.getAttribute("id"), i.getAttribute("label"), expression.children("text").singleOrNull()?.textContent.orEmpty(), type(expression.getAttribute("typeRef")))
            }
            val outputs = t.children("output").map { DecisionOutput(it.getAttribute("id"), it.getAttribute("name"), type(it.getAttribute("typeRef"))) }
            val rules = t.children("rule").map { r -> DecisionRule(r.getAttribute("id"), r.children("description").firstOrNull()?.textContent.orEmpty(),
                r.children("inputEntry").map { it.children("text").singleOrNull()?.textContent.orEmpty() },
                r.children("outputEntry").map { it.children("text").singleOrNull()?.textContent.orEmpty() }) }
            return DecisionTable(d.getAttribute("id"), d.getAttribute("name").ifBlank { d.getAttribute("id") }, t.getAttribute("hitPolicy").ifBlank { "UNIQUE" }, inputs, outputs, rules)
        } catch (e: DecisionException) { throw e } catch (e: Exception) {
            throw DecisionException("INVALID_DMN", e.message?.takeIf { e is IllegalArgumentException || e is IllegalStateException }?.take(180) ?: "Invalid or unsupported DMN XML")
        }
    }
    private fun type(value: String): String = when (value) {
        "string", "number", "boolean", "date" -> value
        "Any", "json" -> "json"
        else -> throw DecisionException("UNSUPPORTED_TYPE", "Unsupported DMN type")
    }
    private fun Element.children(name: String): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.localName == name && it.namespaceURI == NS }
}
