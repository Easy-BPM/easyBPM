// Requires Playwright (or NODE_PATH) and Chrome. No backend is required.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createServer} from 'vite';
const {chromium} = createRequire(import.meta.url)('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const field = (id, type, extra={}) => ({id,name:id,title:id,type,required:true,readOnly:false,...extra});
const fixture = {id:'preview_test',name:'Preview test',tabs:[
  {id:'general',name:'General',fields:[field('Description','string',{minLength:3,maxLength:12,pattern:'^[A-Z]'}),field('Amount','number',{minimum:0,maximum:10,multipleOf:0.5}),field('Approved','boolean'),field('Choice','select',{options:['One','Two']}),field('Radio','radio',{options:['A','B']})]},
  {id:'details',name:'Details',fields:[field('Notes','text'),field('Date','date',{minDate:'2026-01-01',maxDate:'2026-12-31'}),field('Time','date',{includeTime:true,minDate:'2026-06-15T08:00',maxDate:'2026-06-15T18:00'}),field('Read only','string',{required:false,readOnly:true}),field('Pattern test','string',{required:false,pattern:'['}),field('Attachment','fileUpload')]}
]};
const server = await createServer({configFile:false,root,server:{host:'127.0.0.1',port:0},optimizeDeps:{include:['react','react-dom/client']}});
server.middlewares.use('/__preview.html',async(_req,res)=>{
  res.setHeader('Content-Type','text/html');
  res.end(await server.transformIndexHtml('/__preview.html',`<div id="root"></div><script type="module">import React from 'react';import {createRoot} from 'react-dom/client';import {FormModeler} from '/components/FormModeler.tsx';import {generateJsonSchema} from '/utils/formUtils.ts';const form=${JSON.stringify(fixture)};window.schema=generateJsonSchema(form).schema;createRoot(document.getElementById('root')).render(React.createElement(FormModeler,{formLibrary:new Map([[form.id,form]]),selectedFormKey:form.id}));</script>`));
});
server.middlewares.stack.unshift(server.middlewares.stack.pop());
let browser;
try{
  await server.listen(); browser=await chromium.launch({headless:true,channel:process.env.FORM_BROWSER_CHANNEL||'chrome'});
  const page=await browser.newPage(); const errors=[],writes=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.method()!=='GET')writes.push(r.url());});
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__preview.html`);
  await page.getByRole('button',{name:'Preview Form',exact:true}).click();
  assert.equal(await page.getByPlaceholder('Description',{exact:true}).evaluate(el=>el.required),true);
  const input=name=>page.getByLabel(name,{exact:false});
  const submit=()=>page.getByRole('button',{name:'Submit',exact:true}).click();
  const text=()=>page.locator('body').innerText();
  await submit();assert.match(await page.getByRole('alert').first().innerText(),/Description.*required/);
  await input('Description').fill('   ');await submit();assert.match(await text(),/Description.*required/);
  await input('Description').fill('ab');await input('Amount').fill('-1');await submit();
  assert.match(await text(),/at least 3 characters/);assert.match(await text(),/at least 0/);
  await input('Description').fill('abc');await input('Amount').fill('0.3');await submit();
  assert.match(await text(),/configured pattern/);assert.match(await text(),/multiple of 0.5/);
  await input('Description').fill('VeryLongDescription');await input('Amount').fill('11');await submit();
  assert.match(await text(),/at most 12 characters/);assert.match(await text(),/at most 10/);
  await input('Description').fill('Valid');await input('Amount').fill('0');await input('Choice').selectOption('Two');
  await page.getByRole('radio',{name:'B',exact:true}).check();await submit();
  await input('Notes').waitFor();assert.match(await page.getByRole('alert').first().innerText(),/Notes.*required/);
  await input('Notes').fill('Details entered');await input('Date').fill('2025-12-31');await submit();assert.match(await text(),/on or after/);
  await input('Date').fill('2027-01-01');await submit();assert.match(await text(),/on or before/);
  await input('Date').fill('2026-06-15');
  assert.equal(await input('Read only').isEditable(),false);
  await input('Pattern test').fill('x');await input('Time').fill('2026-06-15T07:00');await submit();
  assert.match(await text(),/invalid pattern/);assert.match(await text(),/Time must be on or after/);
  await input('Pattern test').fill('');await input('Time').fill('2026-06-15T12:00');await submit();assert.match(await page.getByRole('status').innerText(),/valid.*No data was sent/);
  const values=JSON.parse(await page.getByLabel('Preview values').innerText());
  assert.equal(values.Amount,0);assert.equal(values.Approved,false);assert.equal(values.Description,'Valid');assert.equal(values.Notes,'Details entered');assert.equal(values.Radio,'B');
  assert.match(await text(),/File controls are display-only/);
  const schema=await page.evaluate(()=>window.schema);assert.ok(schema.required.includes('Description'));assert.equal(schema.properties.Amount.multipleOf,0.5);assert.equal(schema.properties.Description.pattern,'^[A-Z]');
  assert.deepEqual(writes,[]);assert.deepEqual(errors,[]);
  await page.getByRole('button',{name:'General',exact:true}).last().click();assert.equal(await input('Amount').inputValue(),'0');
  await input('Description').fill('Changed');assert.equal(await page.getByRole('status').count(),0);
  console.log('PASS #66: required/whitespace, constraints, hidden tabs, typed zero/false, feedback and no submissions');
}finally{await browser?.close();await server.close();}
