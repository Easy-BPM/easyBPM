// Run with Playwright (local/NODE_PATH) and Chrome. API responses are isolated fixtures.
import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {fileURLToPath} from 'node:url';import path from 'node:path';import {createServer} from 'vite';
const {chromium}=createRequire(import.meta.url)('playwright');const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const schema={type:'object',properties:{code:{title:'Code',type:'string',minLength:3,maxLength:5,pattern:'^[A-Z]+$'},quantity:{title:'Quantity',type:'integer',minimum:0,maximum:100,multipleOf:2},approved:{title:'Approved',type:'boolean'},choice:{title:'Choice',type:'number',enum:[0,2]},locked:{title:'Locked',type:'string',readOnly:true}},required:['code','quantity']};
const task={id:1,name:'Review',nodeId:'review',processInstanceId:1,assignee:'alice',status:'PENDING',formDbId:1,variables:{code:'ABC',quantity:0,approved:false,choice:0,locked:'KEEP'}};
const server=await createServer({configFile:false,root,server:{host:'127.0.0.1',port:0},optimizeDeps:{include:['react','react-dom/client']}});
server.middlewares.use('/__constraints.html',async(_req,res)=>{res.setHeader('Content-Type','text/html');res.end(await server.transformIndexHtml('/__constraints.html',`<div id="root"></div><script type="module">import React from 'react';import {createRoot} from 'react-dom/client';import {TaskDetailView} from '/App.tsx';createRoot(document.getElementById('root')).render(React.createElement(TaskDetailView,{taskId:1,currentUser:'alice',onBack:()=>window.finished=true}));</script>`));});server.middlewares.stack.unshift(server.middlewares.stack.pop());let browser;
try{await server.listen();browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage();const completed=[],drafts=[],errors=[];
page.on('pageerror',error=>errors.push(error.message));await page.route('http://localhost:8080/**',async route=>{const endpoint=new URL(route.request().url()).pathname;
if(endpoint==='/tasks/1')return route.fulfill({json:{...task,variables:drafts.at(-1)?.variables||task.variables}});
if(endpoint==='/forms/1')return route.fulfill({json:{id:1,formId:'strict',schema}});
if(endpoint==='/tasks/1/complete'){completed.push(route.request().postDataJSON());return route.fulfill({body:'completed'});}
if(endpoint==='/tasks/1/draft'){const body=route.request().postDataJSON();drafts.push(body);return route.fulfill({json:{...task,variables:body.variables}});}
return route.fulfill({status:404});});
const load=async()=>{await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__constraints.html`);await page.getByPlaceholder('Enter Code...').waitFor();};await load();
const code=page.getByPlaceholder('Enter Code...'),quantity=page.getByPlaceholder('Enter Quantity...');
for(const [text,number,message] of [['ABC','-2','at least 0'],['abc','2','pattern'],['A','2','at least 3'],['ABC','102','at most 100'],['ABC','1.5','integer'],['ABC','3','multiple of 2']]){
await code.fill(text);await quantity.fill(number);await page.getByRole('button',{name:'Complete Task',exact:true}).click();
assert.equal(completed.length,0,'Invalid input must not produce a completion request');assert.match(await page.locator('body').innerText(),new RegExp(message));assert.equal(await code.inputValue(),text);assert.equal(await quantity.inputValue(),number);}
await page.getByRole('button',{name:'Save Draft',exact:true}).click();await page.waitForFunction(()=>document.body.innerText.includes('Draft saved'));assert.equal(drafts.length,1);assert.equal(drafts[0].variables.quantity,3);
await load();assert.equal(await quantity.inputValue(),'3');assert.equal(await page.getByPlaceholder('Enter Locked...').isDisabled(),true);
await quantity.fill('0');await page.locator('select').selectOption('0');await code.fill('ABC');await page.getByRole('button',{name:'Complete Task',exact:true}).click();await page.waitForFunction(()=>window.finished===true);
assert.equal(completed.length,1);assert.equal(completed[0].variables.quantity,0);assert.equal(completed[0].variables.approved,false);assert.equal(completed[0].variables.choice,0);assert.equal(completed[0].variables.locked,'KEEP');assert.deepEqual(errors,[]);console.log('PASS #71: schema constraints block requests, preserve input/drafts and retain typed zero/false');
}finally{await browser?.close();await server.close();}
