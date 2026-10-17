(()=>{ if(!window.VGA) return; let n=0; const cb={};
window.__vgr=(id,ok,t)=>{const c=cb[id];delete cb[id];c&&c(ok,t)};
const of=window.fetch;
window.fetch=(u,o)=>{ if(typeof u==='string'&&u.startsWith('/api/')){ return new Promise(res=>{ const id=++n; cb[id]=(ok,t)=>res(new Response(t,{status:ok?200:500,headers:{'content-type':'application/json'}})); VGA.api(id,u.slice(5),(o&&o.body)||'{}'); }); } return of(u,o); };
})();
