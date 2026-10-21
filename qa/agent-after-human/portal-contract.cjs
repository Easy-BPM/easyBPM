// Run: node qa/agent-after-human/portal-contract.cjs
// Service-level contract probes; the browser scenario in README uses the real Docker API.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),ts=require(path.join(root,'easy-bpm-task-portal/node_modules/typescript'));
function load(relative,dependencies={}){const file=path.join(root,relative),m=new Module(file,module);m.paths=module.paths;m.require=(id)=>dependencies[id]??require(id);const source=fs.readFileSync(file,'utf8').replaceAll('import.meta.env','({})');m._compile(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,file);return m.exports;}
const types=load('easy-bpm-task-portal/types.ts');const {bpmService}=load('easy-bpm-task-portal/services/bpmService.ts',{'../types':types});
global.localStorage={getItem:()=>null};const legacy='Task was completed, but process execution failed and an incident was recorded: Agent runtime failure [NETWORK_ERROR].';
(async()=>{let count=0;async function probe(name,replies,expected){let calls=0;global.fetch=async()=>{assert.ok(calls<replies.length,'Unexpected request');const [status,body]=replies[calls++];return new Response(typeof body==='string'?body:JSON.stringify(body),{status});};if(expected==='reject')await assert.rejects(()=>bpmService.completeTask(7,{variables:{comment:'saved'}}));else assert.deepEqual(await bpmService.completeTask(7,{variables:{comment:'saved'}}),{incident:expected});assert.equal(calls,replies.length);count++;console.log('PASS '+name);}
await probe('normal completion',[[200,'Task completed successfully']],false);
await probe('structured committed incident',[[409,{code:'TASK_COMPLETED_WITH_INCIDENT',taskStatus:'COMPLETED',taskId:7}]],true);
await probe('wrong task is not accepted',[[409,{code:'TASK_COMPLETED_WITH_INCIDENT',taskStatus:'COMPLETED',taskId:8}]],'reject');
await probe('ordinary conflict',[[409,'Task already completed']],'reject');
await probe('validation rejection',[[400,'Invalid data']],'reject');
await probe('forbidden rejection',[[403,'Forbidden']],'reject');
await probe('legacy verified completed task',[[409,legacy],[200,{status:'COMPLETED'}]],true);
await probe('legacy pending task is not accepted',[[409,legacy],[200,{status:'PENDING'}]],'reject');
await probe('legacy task lookup failure is not accepted',[[409,legacy],[403,'Forbidden']],'reject');
console.log(`${count} contract probes passed`);})().catch(e=>{console.error(e);process.exitCode=1});
