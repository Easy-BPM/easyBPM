import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'..','build','inbox-ui');await mkdir(output,{recursive:true});
const server=await createServer({configFile:false,root,server:{host:'127.0.0.1',port:0},optimizeDeps:{include:['react','react-dom/client']}});
server.middlewares.use('/__inbox.html',async(_req,res)=>{res.setHeader('Content-Type','text/html');res.end(await server.transformIndexHtml('/__inbox.html',`<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{margin:0;font-family:Arial,sans-serif}*{box-sizing:border-box}</style></head><body><div id="root"></div><script type="module">import React from 'react';import {createRoot} from 'react-dom/client';import {InboxView} from '/App.tsx';import '/index.css';createRoot(document.getElementById('root')).render(React.createElement('div',{className:'task-portal-app','data-theme':new URLSearchParams(location.search).get('theme')||'light',style:{padding:'24px',minHeight:'100vh'}},React.createElement(InboxView,{currentUser:'alice',onSelectTask:()=>{}})));</script></body></html>`));});server.middlewares.stack.unshift(server.middlewares.stack.pop());
let browser;
try{
 await server.listen();browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1120,height:650}});const errors=[];page.on('pageerror',e=>errors.push(e.message));let mode='nested';const requests=[];
 await page.route('http://localhost:8080/**',async route=>{const url=new URL(route.request().url());
 if(url.pathname==='/tasks/sla/summary')return route.fulfill({json:{onTrack:12,atRisk:3,overdue:1,completed:8,withoutSla:4}});
 if(url.pathname==='/tasks/search'){
  requests.push({sort:url.searchParams.get('sort'),page:url.searchParams.get('page'),filters:route.request().postDataJSON().filters});
  const number=Number(url.searchParams.get('page')||0);const content=mode==='empty'||mode==='malformed'?[]:Array.from({length:number===0?25:3},(_,i)=>({id:number*25+i+1,name:'Review request',nodeId:'review',processInstanceId:1,assignee:'alice',status:'PENDING',createdAt:'2026-10-23T12:00:00Z'}));const metadata={number,size:25,totalPages:2,totalElements:28};
  return route.fulfill({json:mode==='nested'?{content,page:metadata}:mode==='flat'?{content,...metadata}:mode==='empty'?{content,page:{number:0,size:25,totalPages:0,totalElements:0}}:{content,page:{totalPages:'invalid',size:0}}});
 }return route.fulfill({json:[]});});
 const url=`http://127.0.0.1:${server.httpServer.address().port}/__inbox.html`;
 await page.goto(url);await page.getByRole('navigation',{name:'Task pages'}).getByText('Page 1 of 2').waitFor();
 assert.equal(await page.getByRole('button',{name:'Previous page',exact:true}).isEnabled(),false);
 await page.getByRole('button',{name:'Next page',exact:true}).click();await page.getByText('Page 2 of 2',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Next page',exact:true}).isEnabled(),false);
 await page.getByLabel('Sort',{exact:true}).selectOption('dueDate,asc');await page.getByText('Page 1 of 2',{exact:true}).waitFor();assert.equal(requests.at(-1).sort,'dueDate,asc');assert.equal(requests.at(-1).page,'0');
 assert.equal(await page.locator('.inbox-sla-card').count(),5);
 await page.screenshot({path:path.join(output,'inbox-light.png'),clip:{x:0,y:0,width:1120,height:295}});
 await page.goto(`${url}?theme=dark`);await page.getByText('Page 1 of 2',{exact:true}).waitFor();await page.screenshot({path:path.join(output,'inbox-dark.png'),clip:{x:0,y:0,width:1120,height:295}});
 await page.setViewportSize({width:390,height:844});await page.reload();await page.getByText('Page 1 of 2',{exact:true}).waitFor();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Mobile must not overflow horizontally');await page.screenshot({path:path.join(output,'inbox-mobile.png'),clip:{x:0,y:0,width:390,height:580}});
 for(const scenario of ['flat','empty','malformed']){mode=scenario;await page.goto(url);await page.getByLabel('SLA counters').waitFor();await page.waitForFunction(()=>document.querySelector('.inbox-pagination')?.textContent.includes('Page'));assert.equal((await page.locator('body').innerText()).includes('NaN'),false);if(scenario!=='flat')assert.equal(await page.getByRole('button',{name:'Next page',exact:true}).isEnabled(),false);}
 assert.deepEqual(errors,[]);console.log('PASS inbox: nested/flat/empty/malformed pagination, navigation, sort reset, SLA cards, dark/light and mobile layout');console.log(output);
}finally{await browser?.close();await server.close();}