package com.easy.bpm.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApiClientCredentialTest : FunSpec({
    test("parses only exact EasyBPM credential format") {
        val selector = "abcdefghijklmnop"
        val secret = "a".repeat(43)
        ApiClientCredential.parse("Bearer ebpm_$selector.$secret") shouldBe (selector to secret)
        ApiClientCredential.parse("Bearer ebpm_${selector}.${secret}x") shouldBe null
        ApiClientCredential.parse("Bearer eyJhbGciOiJIUzI1NiJ9.token.signature") shouldBe null
    }

    test("claims malformed native credentials while leaving other bearer tokens alone") {
        ApiClientCredential.isNativeBearer("Bearer ebpm_broken") shouldBe true
        ApiClientCredential.isNativeBearer("Bearer ordinary-jwt") shouldBe false
    }
})
