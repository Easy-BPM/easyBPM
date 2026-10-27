package com.easy.bpm.service.process

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiTaskRequestResolverTest {
    private val mapper=ObjectMapper()
    private val vars=mapOf("customer" to mapper.readTree("\"A B&x=/é\""),"zero" to mapper.readTree("0"),"off" to mapper.readTree("false"),"nothing" to mapper.readTree("null"),"items" to mapper.readTree("[0,false,null]"))

    @Test fun `URL substitutions are encoded without altering literal structure`() {
        val config=mapper.readTree("""{"url":"http://localhost/orders/{{customer}}?id=${'$'}{customer}"}""")
        val result=ApiTaskRequestResolver.resolve(config,vars)
        assertEquals("http://localhost/orders/A%20B%26x%3D%2F%C3%A9?id=A%20B%26x%3D%2F%C3%A9",result.path("url").asText())
        assertTrue(config.path("url").asText().contains("{{customer}}"))
    }
    @Test fun `nested body templates preserve number boolean null and array values`() {
        val config=mapper.readTree("""{"url":"http://local","body":{"count":"{{zero}}","enabled":"${'$'}{off}","nested":["{{nothing}}","{{items}}"],"text":"count={{zero}} enabled={{off}}"}}""")
        val result=ApiTaskRequestResolver.resolve(config,vars).path("body")
        assertEquals(0,result.path("count").asInt());assertTrue(result.path("count").isNumber)
        assertTrue(result.path("enabled").isBoolean);assertFalse(result.path("enabled").asBoolean())
        assertTrue(result.path("nested")[0].isNull);assertEquals(vars["items"],result.path("nested")[1])
        assertEquals("count=0 enabled=false",result.path("text").asText())
    }
    @Test fun `headers expand but auth references and output definitions remain configuration`() {
        val config=mapper.readTree("""{"url":"http://local","headers":{"X-Count":"{{zero}}"},"auth":{"ref":"{{secretRef}}"},"outputs":[{"sourceValue":"{{literal}}"}]}""")
        val result=ApiTaskRequestResolver.resolve(config,vars)
        assertEquals("0",result.path("headers").path("X-Count").asText());assertEquals(config.path("auth"),result.path("auth"));assertEquals(config.path("outputs"),result.path("outputs"))
    }
    @Test fun `missing variable fails without disclosing variable values or modifying config`() {
        for (json in listOf("""{"url":"http://local/{{absent}}"}""","""{"url":"http://local","body":{"value":"{{absent}}"}}""")) {
            val config=mapper.readTree(json);val before=config.deepCopy<com.fasterxml.jackson.databind.JsonNode>()
            val ex=assertThrows(IllegalArgumentException::class.java){ApiTaskRequestResolver.resolve(config,vars)}
            assertFalse(ex.message!!.contains("A B"));assertEquals(before,config)
        }
    }
}
