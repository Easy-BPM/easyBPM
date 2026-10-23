package com.easy.bpm.decision

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class DecisionXmlRequest(val dmnXml: String = "")
data class DecisionDraftTestRequest(val dmnXml: String = "", val inputs: Map<String, Any?> = emptyMap())
data class DecisionEvaluateRequest(val version: Int? = null, val inputs: Map<String, Any?> = emptyMap())
data class DecisionTestRequest(val name: String = "", val inputs: Map<String, Any?> = emptyMap(), val expectedOutputs: Any? = null)
data class DecisionVersionRequest(val version: Int? = null)

@RestController
@RequestMapping("/decisions")
class DecisionController(private val service: DecisionService) {
    @PostMapping("/validate") fun validate(@RequestBody request: DecisionXmlRequest) = service.validate(request.dmnXml)
    @PostMapping("/test") fun test(@RequestBody request: DecisionDraftTestRequest) = service.test(request.dmnXml, request.inputs)
    @PostMapping fun deploy(@RequestBody request: DecisionXmlRequest) = ResponseEntity.status(HttpStatus.CREATED).body(service.deploy(request.dmnXml))
    @GetMapping fun latest() = service.latest()
    @GetMapping("/{key}/versions") fun versions(@PathVariable key: String) = service.versions(key)
    @GetMapping("/{key}/versions/{version}") fun version(@PathVariable key: String, @PathVariable version: Int) = service.resolve(key, version)
    @PostMapping("/{key}/evaluate") fun evaluate(@PathVariable key: String, @RequestBody request: DecisionEvaluateRequest) = service.evaluateDefinition(service.resolve(key, request.version), request.inputs)
    @GetMapping("/{key}/tests") fun tests(@PathVariable key: String) = service.savedTests(key)
    @PostMapping("/{key}/tests") fun saveTest(@PathVariable key: String, @RequestBody request: DecisionTestRequest) = service.saveTest(key, request.name, request.inputs, request.expectedOutputs)
    @PostMapping("/{key}/tests/run") fun runTests(@PathVariable key: String, @RequestBody(required = false) request: DecisionVersionRequest?) = service.runTests(key, request?.version)
    @GetMapping("/{key}/diff") fun diff(@PathVariable key: String, @RequestParam fromVersion: Int, @RequestParam toVersion: Int) = service.diff(key, fromVersion, toVersion)
}

@RestController
@RequestMapping("/decision-executions")
class DecisionExecutionController(private val service: DecisionService) {
    @GetMapping fun list(@RequestParam(required = false) key: String?, @RequestParam(required = false) version: Int?, @RequestParam(required = false) processInstanceId: Long?,
        @RequestParam(required = false) nodeId: String?, @RequestParam(required = false) status: String?, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = service.executionPage(key, version, processInstanceId, nodeId, status, page, size)
    @GetMapping("/{id}") fun detail(@PathVariable id: Long) = service.execution(id)
}

@RestControllerAdvice(assignableTypes = [DecisionController::class, DecisionExecutionController::class])
class DecisionExceptionHandler {
    @ExceptionHandler(DecisionException::class) fun domain(e: DecisionException) = ResponseEntity.status(e.status).body(mapOf("code" to e.code, "message" to e.message))
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class) fun conflict() = ResponseEntity.status(409).body(mapOf("code" to "CONFLICT", "message" to "Decision write conflicted; retry the request"))
    @ExceptionHandler(IllegalArgumentException::class, org.springframework.http.converter.HttpMessageNotReadableException::class) fun invalid() = ResponseEntity.badRequest().body(mapOf("code" to "INVALID_REQUEST", "message" to "Invalid decision request"))
}
