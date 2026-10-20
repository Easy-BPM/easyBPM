// Playwright (local or NODE_PATH) and Chrome required.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createServer} from 'vite';
const {chromium}=createRequire(import.meta.url)('playwright');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const server=await createServer({configFile:false,root,server:{host:'127.0.0.1',port:0}});
server.middlewares.use('/__mapping.html',async(_req,res)=>{res.setHeader('Content-Type','text/html');res.end(await server.transformIndexHtml('/__mapping.html',`<script type="module">import * as codec from '/utils/bpmnXml.ts';window.codec=codec;</script>`));});server.middlewares.stack.unshift(server.middlewares.stack.pop());
let browser;
try{await server.listen();browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage();await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__mapping.html`);await page.waitForFunction(()=>!!window.codec);
const result=await page.evaluate(()=>{
const payload=[{source:'variable',sourceValue:'payload',sourceName:'payload',target:'variable',targetVariable:'receivedPayload',value:'receivedPayload',type:'json'}];
const definition={processId:'mapping_test',variables:[{name:'receivedPayload',type:'json',initialValue:{}}],nodes:[{id:'start',type:'StartEvent',next:['wait']},{id:'wait',type:'MessageIntermediateCatchEvent',message:{name:'received',correlationKeys:['caseId'],payload},next:['end']},{id:'end',type:'EndEvent'}],flows:[{from:'start',to:'wait'},{from:'wait',to:'end'}]};
let xml=window.codec.processDefinitionToBpmnXml(definition);let parsed=window.codec.bpmnXmlToProcessDefinition(xml);parsed=window.codec.bpmnXmlToProcessDefinition(window.codec.processDefinitionToBpmnXml(parsed));return {message:parsed.nodes.find(n=>n.id==='wait').message,variables:parsed.variables};});
assert.equal(result.message.payload[0].targetVariable,'receivedPayload');assert.equal(result.message.payload[0].sourceValue,'payload');assert.equal(result.message.payload[0].type,'json');assert.deepEqual(result.message.correlationKeys,['caseId']);assert.equal(result.variables[0].name,'receivedPayload');console.log('PASS #70 Modeler BPMN XML double round-trip preserves message payload mapping');
}finally{await browser?.close();await server.close();}
