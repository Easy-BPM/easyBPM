// Synthetic credentials only. Never print captured arguments, request bodies or sessions.
// Requires Playwright (or NODE_PATH) and Chrome; API responses are isolated fixtures.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createServer} from 'vite';
const {chromium} = createRequire(import.meta.url)('playwright');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const markers={username:'QA_USER_MARKER_67',password:'QA_PASSWORD_MARKER_67',token:'QA_TOKEN_MARKER_67'};
const server=await createServer({configFile:false,root,server:{host:'127.0.0.1',port:0},optimizeDeps:{include:['react','react-dom/client']}});
server.middlewares.use('/__auth-test.html',async(_req,res)=>{
  res.setHeader('Content-Type','text/html');
  res.end(await server.transformIndexHtml('/__auth-test.html',`<div id="root"></div><script type="module">import React from 'react';import {createRoot} from 'react-dom/client';import App from '/App.tsx';createRoot(document.getElementById('root')).render(React.createElement(App));</script>`));
});
server.middlewares.stack.unshift(server.middlewares.stack.pop());
let browser;
try{
  await server.listen();browser=await chromium.launch({headless:true,channel:process.env.AUTH_BROWSER_CHANNEL||'chrome'});
  for(const mode of ['success','unauthorized','server-error','network-error','config-error','oidc-error']){
    const page=await browser.newPage();let submitted=false,configRequests=0;const pageErrors=[];
    page.on('pageerror',()=>pageErrors.push(true));
    await page.addInitScript(markers=>{
      const state={leaked:false,methods:[],calls:[]};window.authLogCheck=state;
      const sensitive=value=>{
        let text='';try{text=typeof value==='string'?value:JSON.stringify(value);}catch{text=String(value);}
        if(value instanceof Error)text=String(value);
        return Object.values(markers).some(marker=>text?.includes(marker)) || /authorization|bearer|"password"/i.test(text||'');
      };
      for(const name of Object.getOwnPropertyNames(console)){
        if(typeof console[name]!=='function')continue;
        state.methods.push(name);
        console[name]=(...args)=>{state.calls.push(name);state.leaked ||= args.some(sensitive);};
      }
    },markers);
    await page.route('http://localhost:8080/**',async route=>{
      const request=route.request(),endpoint=new URL(request.url()).pathname;
      if(endpoint==='/auth/config'){
        configRequests++;
        if(mode==='config-error'||(mode==='oidc-error'&&configRequests>1))return route.fulfill({status:500,json:{message:`Configuration failed ${markers.password} ${markers.token}`}});
        if(mode==='oidc-error')return route.fulfill({json:{provider:'oidc',oidc:{issuerUri:'https://example.invalid',clientId:'qa',authorizationEndpoint:'https://example.invalid/authorize',tokenEndpoint:'https://example.invalid/token',logoutEndpoint:'https://example.invalid/logout'}}});
        return route.fulfill({json:{provider:'local',oidc:null}});
      }
      if(endpoint==='/auth/login'){
        const body=request.postDataJSON();
        submitted=request.method()==='POST' && body.username===markers.username && body.password===markers.password && request.headers()['content-type']==='application/json';
        if(mode==='network-error')return route.abort('failed');
        if(mode!=='success')return route.fulfill({status:mode==='unauthorized'?401:500,json:{message:`Denied ${markers.password} ${markers.token} Authorization: Bearer ${markers.token}`}});
        return route.fulfill({json:{username:markers.username,token:markers.token,groups:['QA'],permissions:['ACCESS_PROCESS_PORTAL']}});
      }
      if(endpoint==='/auth/me')return route.fulfill({json:{username:markers.username,groups:['QA'],permissions:['ACCESS_PROCESS_PORTAL']}});
      if(endpoint==='/tasks/search')return route.fulfill({json:{content:[],totalPages:1,totalElements:0,size:100,number:0}});
      if(endpoint==='/tasks/sla/summary')return route.fulfill({json:{onTrack:0,atRisk:0,overdue:0,completed:0,withoutSla:0}});
      return route.fulfill({json:[]});
    });
    if(mode==='oidc-error')await page.addInitScript(()=>sessionStorage.setItem('easybpm_portal_oidc_auto_login_suppressed','true'));
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__auth-test.html`);
    if(mode==='oidc-error'){
      await page.getByRole('button',{name:'Sign in with Keycloak',exact:true}).click();
      await page.waitForFunction(()=>window.authLogCheck.calls.includes('error'));
    }else{
      await page.getByPlaceholder('Enter username').fill(markers.username);await page.locator('input[type=password]').fill(markers.password);
      await page.getByRole('button',{name:'Sign In',exact:true}).click();
    }
    if(mode==='success'){
      await page.getByPlaceholder('Enter username').waitFor({state:'detached'});
      assert.equal(await page.evaluate(markers=>{const session=JSON.parse(localStorage.getItem('easybpm_portal_auth'));return session?.token===markers.token&&session.username===markers.username&&session.groups[0]==='QA';},markers),true,'Successful login must preserve the session contract');
    }else{
      await page.getByRole('button',{name:'Sign In',exact:true}).waitFor();
      if(mode!=='oidc-error')await page.getByRole('alert').filter({hasText:'Unable to sign in.'}).waitFor();
      await page.waitForFunction(()=>!document.querySelector('button[type=submit]').disabled);
      assert.equal(await page.evaluate(()=>localStorage.getItem('easybpm_portal_auth')===null),true,'Failed login must not create a session');
    }
    const result=await page.evaluate(()=>({leaked:window.authLogCheck.leaked,methods:window.authLogCheck.methods,calls:window.authLogCheck.calls}));
    assert.ok(result.methods.includes('log')&&result.methods.includes('error')&&result.methods.includes('warn')&&result.methods.includes('debug')&&result.methods.includes('info'));
    assert.equal(result.leaked,false,`Sensitive console output detected for ${mode}`);
    assert.equal(submitted,mode!=='oidc-error','Login request contract must remain unchanged');assert.deepEqual(pageErrors,[]);
    console.log(`PASS #67 ${mode}: no credential/token/header output; authentication behavior preserved`);
    await page.close();
  }
}finally{await browser?.close();await server.close();}
