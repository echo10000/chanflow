import {MediaPreloader} from './preload.js';
const preloader=new MediaPreloader();
const $ = selector => document.querySelector(selector);
const icons = {
  grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  bookmark:'<path d="M6 4h12v17l-6-4-6 4z"/>',plus:'<path d="M12 5v14M5 12h14"/>',
  arrow:'<path d="m14 5-7 7 7 7"/>',down:'<path d="m5 9 7 7 7-7"/>',up:'<path d="m5 15 7-7 7 7"/>',
  download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
  volume:'<path d="m11 4-6 5H2v6h3l6 5zM15 8c3 2 3 6 0 8m3-11c5 4 5 10 0 14"/>',
  mute:'<path d="m11 4-6 5H2v6h3l6 5zM16 9l6 6m0-6-6 6"/>',
  expand:'<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>',
  chat:'<path d="M21 11a8 8 0 0 1-8 8H8l-5 3v-7a8 8 0 0 1 8-12h2a8 8 0 0 1 8 8Z"/>',
  play:'<path d="m8 4 13 8-13 8z"/>',pause:'<path d="M8 4v16M16 4v16"/>',
  refresh:'<path d="M20 8a9 9 0 1 0 0 8M20 3v5h-5"/>',image:'<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8" cy="8" r="2"/><path d="m21 15-6-6-9 12"/>',
  trash:'<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>',more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  check:'<path d="m4 12 5 5L20 6"/>',search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name]||icons.grid}</svg>`;
const escape = value => String(value??'').replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const plain = html => { const d = new DOMParser().parseFromString(html||'','text/html'); return d.body.textContent.trim(); };
const mediaUrl = post => `/media/${post.board}/${post.tim}${post.ext}`;
const thumbnail = post => `/media/${post.board}/${post.tim}s.jpg`;
const key = post => `${post.board}_${post.no}`;
const bytes = n => n>=1048576?`${(n/1048576).toFixed(1)} MB`:`${Math.round(n/1024)} KB`;
const main = $('#main'), app = $('#app');
const state = {tab:'browse',board:localStorage.getItem('board')||'wsg',boards:[],threads:[],limit:24,feed:null,index:0,saved:[],history:[],libraryMode:'saved',watchedFilter:'all',selecting:false,selected:new Set(),muted:true,fill:false,loading:false,queue:[],job:null,failed:[],allowAdult:localStorage.getItem('adult')==='true'};
let requestGeneration=0, mediaGeneration=0, blobUrl=null, toastTimer, lastMove=0, wheelDelta=0, wheelTimer,viewTimer,activePost=null;
const database = new Promise((resolve,reject)=>{
  const req=indexedDB.open('chanflow-vault',2);
  req.onupgradeneeded=()=>{if(!req.result.objectStoreNames.contains('media'))req.result.createObjectStore('media',{keyPath:'key'});if(!req.result.objectStoreNames.contains('history'))req.result.createObjectStore('history',{keyPath:'key'});};
  req.onsuccess=()=>resolve(req.result); req.onerror=()=>reject(req.error);
});
async function stored(operation, value, table='media') {
  const db=await database;
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(table,operation==='getAll'?'readonly':'readwrite');
    const store=tx.objectStore(table); const req=operation==='getAll'||operation==='clear'?store[operation]():store[operation](value);
    tx.oncomplete=()=>resolve(req.result); tx.onerror=()=>reject(tx.error); tx.onabort=()=>reject(tx.error||new Error('Storage unavailable'));
  });
}
async function refreshSaved(){state.saved=(await stored('getAll')).sort((a,b)=>b.savedAt-a.savedAt);$('.saved-count').textContent=state.saved.length;}
async function refreshHistory(){state.history=(await stored('getAll',null,'history')).sort((a,b)=>b.watchedAt-a.watchedAt);}
async function recordWatch(post,position=0,duration=0){
  const {blob,thumb,...metadata}=post;
  const row={...metadata,key:key(post),watchedAt:Date.now(),position,duration};
  const old=state.history.findIndex(p=>p.key===row.key);if(old>=0)state.history.splice(old,1);state.history.unshift(row);
  const removed=state.history.splice(200);try{await stored('put',row,'history');for(const p of removed)await stored('delete',p.key,'history');}catch{}
}
async function deleteSavedKeys(keys){const db=await database;return new Promise((resolve,reject)=>{const tx=db.transaction('media','readwrite');const s=tx.objectStore('media');keys.forEach(k=>s.delete(k));tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});}
function toast(text){$('#toast').textContent=text;$('#toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),3500);}
function svgAll(){document.querySelectorAll('[data-icon]').forEach(e=>e.innerHTML=icon(e.dataset.icon));}
function dialog(html){$('#dialog-content').innerHTML=html;$('#dialog').showModal();}
$('#dialog-close').onclick=()=>$('#dialog').close();
$('#dialog').addEventListener('click',event=>{if(event.target===$('#dialog')){const r=$('#dialog').getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)$('#dialog').close();}});
$('#phone-size').onclick=()=>{app.classList.add('phone');$('#phone-size').classList.add('selected');$('#desktop-size').classList.remove('selected');};
$('#desktop-size').onclick=()=>{app.classList.remove('phone');$('#desktop-size').classList.add('selected');$('#phone-size').classList.remove('selected');};
document.querySelectorAll('[data-tab]').forEach(e=>e.onclick=()=>navigate(e.dataset.tab));
$('#all-boards').onclick=showBoards;
document.querySelector('.brand').onclick=e=>{e.preventDefault();navigate('browse');};

async function api(path,signal){
  const response=await fetch(`/api/${path}`,{signal});
  if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.error||`Connection failed (${response.status}).`);}
  return response.json();
}
function shortBoards(){
  const ids=[state.board,'wsg','w','g','a','v','co','lit'].filter((id,i,a)=>a.indexOf(id)===i);
  return ids.map(id=>state.boards.find(b=>b.board===id)).filter(b=>b&&(b.ws_board||state.allowAdult)).slice(0,6);
}
function sidebar(){
  $('#board-shortcuts').innerHTML=shortBoards().map(b=>`<button class="board-shortcut ${state.board===b.board?'selected':''}" data-board="${b.board}"><b>/${b.board}/</b><span>${escape(b.title)}</span></button>`).join('');
  $('#board-shortcuts').querySelectorAll('[data-board]').forEach(e=>e.onclick=()=>chooseBoard(e.dataset.board));
  document.querySelectorAll('.nav-item').forEach(e=>e.classList.toggle('active',e.dataset.tab===state.tab));
}
function clearMedia(){clearTimeout(viewTimer);mediaGeneration++;main.querySelectorAll('video').forEach(v=>{if(v.currentTime>=2&&activePost)recordWatch(activePost,v.currentTime,Number.isFinite(v.duration)?v.duration:0);v.pause();v.removeAttribute('src');v.load();});activePost=null;if(blobUrl){URL.revokeObjectURL(blobUrl);blobUrl=null;}}
function navigate(tab){requestGeneration++;clearMedia();clearTileUrls();preloader.cancel();state.feed=null;state.tab=tab;app.classList.remove('feed-mode');sidebar();if(tab==='saved')renderSaved();else renderBrowse();}
async function chooseBoard(id){state.board=id;localStorage.setItem('board',id);state.threads=[];state.limit=24;state.loading=true;navigate('browse');const generation=++requestGeneration;
  try{const pages=await api(`${id}/catalog.json`);if(generation!==requestGeneration)return;state.threads=pages.flatMap(p=>p.threads);state.loading=false;renderBrowse();}
  catch(e){if(generation!==requestGeneration)return;state.loading=false;renderBrowse(e.message);}
}
function renderBrowse(error=''){
  const b=state.boards.find(b=>b.board===state.board);
  main.innerHTML=`<header class="page-top"><div><h1>${escape(b?.title||`/${state.board}/`)}</h1><p>/${escape(state.board)}/ &nbsp; ${state.threads.length?`${state.threads.length} active threads`:''}</p></div><button class="text-button" id="refresh-board">${icon('refresh')}Refresh</button></header>
    <div class="board-row">${shortBoards().map(b=>`<button class="board-chip ${b.board===state.board?'selected':''}" data-board="${b.board}"><strong>/${b.board}/</strong></button>`).join('')}<button class="board-chip" id="more-boards">${icon('plus')}</button></div>
    <div class="section-heading"><h2>Latest threads</h2>${b?.ws_board?'<span class="safe">SFW</span>':''}<input class="thread-search" id="thread-search" placeholder="Filter threads…" aria-label="Filter threads"></div>
    ${error?`<div class="error-message">${escape(error)} <button class="text-button" id="retry-board">Try again</button></div>`:state.loading?'<div class="loading-state"><span class="spinner"></span>Loading threads…</div>':!state.threads.length?'<div class="empty-state"><h2>No threads to show</h2><p>Choose another board or refresh to try again.</p></div>':`<div class="thread-grid">${state.threads.slice(0,state.limit).map(t=>{
      const title=plain(t.sub)||plain(t.com).split('\n')[0].slice(0,100)||`Thread #${t.no}`;
      const ratio=Math.min(2.1,Math.max(.65,(t.w||t.tn_w||240)/(t.h||t.tn_h||180)));
      return `<button class="thread-card" data-thread="${t.no}"><div class="thread-cover" style="--ratio:${ratio}">${t.tim?`<img src="/media/${state.board}/${t.tim}s.jpg" alt="" loading="lazy" decoding="async">`:''}${t.ext==='.webm'?'<span class="file-type">VIDEO</span>':''}<span class="media-count">${icon('image')}${(t.images||0)+(t.tim?1:0)}</span></div><h3>${escape(title)}</h3><div class="thread-meta">${icon('chat')} ${t.replies||0}<span class="post-number">#${t.no}</span></div></button>`;
    }).join('')}</div>${state.limit<state.threads.length?'<button id="load-more" class="load-more">Show more threads</button>':''}`}`;
  main.querySelectorAll('[data-board]').forEach(e=>e.onclick=()=>chooseBoard(e.dataset.board));
  $('#more-boards').setAttribute('aria-label','Find a board');$('#more-boards').onclick=showBoards;
  $('#refresh-board').onclick=()=>chooseBoard(state.board);$('#retry-board')?.addEventListener('click',()=>chooseBoard(state.board));
  $('#load-more')?.addEventListener('click',()=>{const y=window.scrollY;state.limit+=24;renderBrowse();window.scrollTo(0,y);});
  main.querySelectorAll('[data-thread]').forEach(e=>e.onclick=()=>openThread(Number(e.dataset.thread)));
  $('#thread-search').oninput=e=>{const q=e.target.value.toLowerCase();main.querySelectorAll('.thread-card').forEach(card=>card.hidden=!card.textContent.toLowerCase().includes(q));};
  sidebar();
}
function showBoards(){
  dialog(`<h2>Find a board</h2><p>Choose where your next feed starts.</p><input class="board-search" id="board-search" placeholder="Search boards…" aria-label="Search boards"><label class="adult-option"><input type="checkbox" id="adult-boards" ${state.allowAdult?'checked':''}>Show adult boards (18+)</label><div class="board-results" id="board-results"></div>`);
  const draw=()=>{const q=$('#board-search').value.toLowerCase();$('#board-results').innerHTML=state.boards.filter(b=>(b.ws_board||state.allowAdult)&&`${b.board} ${b.title}`.toLowerCase().includes(q)).map(b=>`<button class="board-result" data-id="${b.board}"><b>/${b.board}/</b><span>${escape(b.title)}${b.ws_board?'':' (18+)'}</span></button>`).join('');$('#board-results').querySelectorAll('button').forEach(e=>e.onclick=()=>{$('#dialog').close();chooseBoard(e.dataset.id);});};
  $('#board-search').oninput=draw;$('#adult-boards').onchange=e=>{state.allowAdult=e.target.checked;localStorage.setItem('adult',String(state.allowAdult));draw();};draw();$('#board-search').focus();
}
async function openThread(id){
  clearMedia();const generation=++requestGeneration;
  main.innerHTML='<div class="loading-state"><span class="spinner"></span>Opening the media feed…</div>';
  try{
    const {posts}=await api(`${state.board}/thread/${id}.json`);if(generation!==requestGeneration)return;
    const title=plain(posts[0].sub)||plain(posts[0].com).slice(0,100)||`Thread #${id}`;
    const media=posts.filter(p=>p.tim&&!p.filedeleted&&['.jpg','.jpeg','.png','.gif','.webm','.mp4'].includes(p.ext)).map(p=>({...p,board:state.board,thread:id,title,caption:plain(p.com),filename:plain(p.filename)||String(p.no)}));
    if(!media.length){toast('This thread has no supported media.');renderBrowse();return;}
    state.feed={media,title,offline:false};state.index=0;renderFeed();
  }catch(e){if(generation!==requestGeneration)return;renderBrowse(e.message);}
}
function rail(action,name,label,extra=''){return `<button class="rail-action ${extra}" data-action="${action}" aria-label="${label}"><span class="circle">${icon(name)}</span><span>${label}</span></button>`;}
function renderFeed(){
  if(!state.feed)return;
  clearMedia();const serial=mediaGeneration;const post=state.feed.media[state.index];activePost=post;const video=['.webm','.mp4'].includes(post.ext);const saved=state.saved.some(s=>s.key===key(post));
  app.classList.add('feed-mode');main.innerHTML=`<section class="feed-shell"><header class="feed-header"><button class="icon-button" id="back-feed" aria-label="Back">${icon('arrow')}</button><div><h2>${escape(state.feed.title)}</h2><p>${post.blob?'Saved on this device':`/${post.board}/ · Thread #${post.thread}`}</p></div><button class="text-button thread-menu" id="thread-actions" aria-label="More options">${icon('more')}</button></header><div class="feed-stage ${state.fill?'fill':''}" id="feed-stage" tabindex="0" aria-label="Media feed. Swipe or use arrow keys to navigate.">
    ${video?`<video id="feed-video" playsinline loop ${state.muted?'muted':''} preload="auto" controlslist="nodownload" aria-label="${escape(post.filename)}"></video>`:'<img id="feed-image" alt="'+escape(post.filename)+'">'}<div class="feed-loading" id="media-loading"><span class="spinner"></span>Loading ${video?'video':'image'}…</div>
    <div class="feed-rail">${video?rail('volume',state.muted?'mute':'volume',state.muted?'Unmute':'Mute'):''}${rail('fit','expand',state.fill?'Fit':'Fill')}${rail('save',saved?'check':'download',saved?'Saved':'Save',saved?'saved':'')}${rail('post','chat','Post')}${post.blob?rail('delete','trash','Delete'):''}</div></div>
    <footer class="feed-caption"><div class="caption-text"><h3>${escape(post.filename)}${escape(post.ext)}</h3><p>${escape(post.caption||`${post.width||post.w||'?'} × ${post.height||post.h||'?'} · ${bytes(post.fsize||post.blob?.size||0)}`)}</p><span class="next-status" id="next-status"></span></div><div class="feed-position"><button id="prev-media" class="icon-button" aria-label="Previous media">${icon('up')}</button><span class="position-label">${state.index+1} / ${state.feed.media.length}</span><button id="next-media" class="icon-button" aria-label="Next media">${icon('down')}</button></div></footer><div class="feed-hint">Scroll or press ↑ ↓ to browse${video?' · Click video to pause':''}</div></section>`;
  $('#back-feed').onclick=()=>navigate(state.tab);$('#prev-media').onclick=()=>move(-1);$('#next-media').onclick=()=>move(1);$('#thread-actions').onclick=threadActions;
  main.querySelectorAll('[data-action]').forEach(e=>e.onclick=event=>{event.stopPropagation();const action=e.dataset.action;if(action==='save')savePosts([post]);if(action==='volume'){state.muted=!state.muted;const v=$('#feed-video');if(v)v.muted=state.muted;e.innerHTML=`<span class="circle">${icon(state.muted?'mute':'volume')}</span><span>${state.muted?'Unmute':'Mute'}</span>`;e.setAttribute('aria-label',state.muted?'Unmute':'Mute');}if(action==='fit'){state.fill=!state.fill;$('#feed-stage').classList.toggle('fill',state.fill);e.querySelector('span:last-child').textContent=state.fill?'Fit':'Fill';e.setAttribute('aria-label',state.fill?'Fit':'Fill');}if(action==='post')showPost(post);if(action==='delete')askDelete(post);});
  const stage=$('#feed-stage');stage.onwheel=e=>{if(e.target.closest('.feed-rail'))return;e.preventDefault();wheelDelta+=e.deltaY;clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>wheelDelta=0,120);if(Math.abs(wheelDelta)>45){move(wheelDelta>0?1:-1);wheelDelta=0;}};
  let start=null;stage.ontouchstart=e=>{if(!e.target.closest('button'))start={x:e.touches[0].clientX,y:e.touches[0].clientY};};stage.ontouchend=e=>{if(!start)return;const dy=e.changedTouches[0].clientY-start.y,dx=e.changedTouches[0].clientX-start.x;start=null;if(Math.abs(dy)>55&&Math.abs(dy)>Math.abs(dx)*1.3)move(dy<0?1:-1);};
  const buffered=post.blob||preloader.get(mediaUrl(post));
  const source=buffered?URL.createObjectURL(buffered):mediaUrl(post);if(buffered)blobUrl=source;
  const ready=()=>{if(serial!==mediaGeneration)return;$('#media-loading')?.remove();};
  const fail=()=>{if(serial!==mediaGeneration)return;ready();if(!$('#media-error'))stage.insertAdjacentHTML('beforeend','<div class="media-error" id="media-error">This file couldn’t be loaded. It may have expired, or this browser may not support its codec.<br><button class="secondary" id="retry-media">Retry</button></div>');$('#retry-media').onclick=renderFeed;};
  if(video){const v=$('#feed-video');let lastRecord=0;v.oncanplay=ready;v.onerror=fail;v.onloadedmetadata=()=>{const h=state.history.find(p=>p.key===key(post));if(h?.position>2&&h.position<v.duration-2)v.currentTime=h.position;};v.ontimeupdate=()=>{if(v.currentTime>=2&&performance.now()-lastRecord>5000){lastRecord=performance.now();recordWatch(post,v.currentTime,Number.isFinite(v.duration)?v.duration:0);}};v.onclick=()=>{if(v.paused)v.play().catch(()=>toast('Press play to start this video.'));else v.pause();};v.src=source;v.muted=state.muted;v.play().catch(()=>{ready();toast('Click the video to start playback.');});}
  else{const img=$('#feed-image');img.onload=()=>{ready();viewTimer=setTimeout(()=>{if(serial===mediaGeneration&&!document.hidden)recordWatch(post);},2000);};img.onerror=fail;img.src=source;}
  if(!post.blob){const next=state.feed.media[state.index+1];preloader.next(next&&!next.blob?mediaUrl(next):null,next?.fsize||0,()=>{if(serial===mediaGeneration&&$('#next-status'))$('#next-status').textContent='Next ready';});}
}
function move(delta){if(!state.feed||$('#dialog').open)return;const now=performance.now();if(now-lastMove<240)return;const index=state.index+delta;if(index<0||index>=state.feed.media.length){toast(index<0?'First item in this feed.':'You’ve reached the end of this thread.');return;}lastMove=now;state.index=index;renderFeed();}
document.addEventListener('keydown',e=>{if($('#dialog').open||['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName))return;if(e.key==='/'){e.preventDefault();showBoards();return;}if(!state.feed)return;if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();move(e.key==='ArrowDown'?1:-1);}if(e.key===' '&&$('#feed-video')){e.preventDefault();$('#feed-video').click();}if(e.key==='Escape')navigate(state.tab);});
document.addEventListener('visibilitychange',()=>{const video=$('#feed-video');if(!video)return;if(document.hidden)video.pause();else video.play().catch(()=>{});});
function showPost(post){dialog(`<h2>${escape(post.title)}</h2><p>/${escape(post.board)}/ · Post #${post.no}</p><p>${escape(post.caption||'This post has no text.')}</p><p>${escape(post.filename)}${escape(post.ext)} · ${bytes(post.fsize||post.blob?.size||0)}</p><div class="dialog-actions"><button class="primary" id="post-save">Save to library</button></div>`);$('#post-save').onclick=()=>{savePosts([post]);$('#dialog').close();};}
function threadActions(){const offline=state.feed.offline,history=state.feed.history;dialog(`<h2>${offline?'Offline library':history?'Recently watched':'Save this thread'}</h2><p>${offline?'These files play directly from this browser’s storage.':history?'History stores the media you opened and your playback positions. Files marked Offline are saved on this device.':`Save all ${state.feed.media.length} attachments (${bytes(state.feed.media.reduce((n,p)=>n+(p.fsize||0),0))}) inside ChanFlow. Already saved files are skipped. Cancel the queue in Library.`}</p><div class="dialog-actions">${offline||history?'':`<button class="primary" id="save-thread">Save all ${state.feed.media.length} files</button>`}<button class="secondary" id="close-options">Close</button></div>`);$('#close-options').onclick=()=>$('#dialog').close();$('#save-thread')?.addEventListener('click',()=>{savePosts(state.feed.media);$('#dialog').close();});}
function savePosts(posts){const existing=new Set([...state.saved.map(p=>p.key),...state.queue.map(key),...(state.job?[key(state.job.post)]:[])]);const pending=posts.filter(p=>!existing.has(key(p)));state.queue.push(...pending);if(!pending.length){toast('Already saved or queued.');return;}toast(`${pending.length===1?'File':pending.length+' files'} added to your save queue.`);navigator.storage?.persist?.().catch(()=>{});runQueue();}
async function runQueue(){if(state.job||!state.queue.length)return;const post=state.queue.shift();const controller=new AbortController();state.job={post,controller,received:0,total:post.fsize||0};updateDownloadBanner();
  try{
    if((post.fsize||0)>250*1048576)throw new Error('This file exceeds the 250 MB limit.');
    const response=await fetch(mediaUrl(post),{signal:controller.signal});if(!response.ok)throw new Error(response.status===404?'The file expired before it could be saved.':`Download failed (${response.status}).`);
    const total=Number(response.headers.get('content-length')||0);if(total>250*1048576)throw new Error('This file exceeds the 250 MB limit.');state.job.total=total;
    const reader=response.body.getReader(),chunks=[];let received=0,last=0;
    while(true){const {done,value}=await reader.read();if(done)break;received+=value.byteLength;if(received>250*1048576){await reader.cancel();throw new Error('This file exceeds the 250 MB limit.');}chunks.push(value);state.job.received=received;if(performance.now()-last>400){updateDownloadBanner();last=performance.now();}}
    if(!received||(total&&received!==total))throw new Error('The download was incomplete. Try saving it again.');
    const blob=new Blob(chunks,{type:response.headers.get('content-type')||'application/octet-stream'});
    let thumb=null;try{const r=await fetch(thumbnail(post),{signal:controller.signal});if(r.ok)thumb=await r.blob();}catch(e){if(e.name==='AbortError')throw e;}
    await stored('put',{...post,key:key(post),blob,thumb,savedAt:Date.now()});await refreshSaved();toast('Saved inside ChanFlow. Available offline.');
    if(state.feed&&key(state.feed.media[state.index])===key(post)){const e=main.querySelector('[data-action="save"]');if(e){e.classList.add('saved');e.setAttribute('aria-label','Saved');e.innerHTML=`<span class="circle">${icon('check')}</span><span>Saved</span>`;}}
  }catch(e){if(e.name!=='AbortError'){state.failed.push({post,error:e.message});toast(e.name==='QuotaExceededError'?'Storage is full. Delete some saved files and try again.':e.message);}}
  finally{state.job=null;if(state.tab==='saved'&&!state.feed)renderSaved();runQueue();updateDownloadBanner();}
}
function updateDownloadBanner(){let banner=$('#download-banner');if(state.tab!=='saved'||state.feed)return;if(!state.job&&!state.queue.length){banner?.remove();return;}if(!banner){main.querySelector('.library-tools')?.insertAdjacentHTML('beforebegin','<div class="download-banner" id="download-banner"></div>');banner=$('#download-banner');}if(!banner)return;const j=state.job;banner.innerHTML=`<span class="spinner"></span><span>${j?`Saving ${escape(j.post.filename).slice(0,35)}… ${j.total?Math.round(j.received*100/j.total)+'%':bytes(j.received)}`:'Queued'}${state.queue.length?` · ${state.queue.length} next`:''}</span><button class="text-button" id="cancel-downloads">Cancel</button>`;$('#cancel-downloads').onclick=()=>{state.queue=[];state.job?.controller.abort();toast('Save queue cancelled. Completed files are kept.');};}
let tileUrls=[];
function clearTileUrls(){tileUrls.forEach(URL.revokeObjectURL);tileUrls=[];}
function renderSaved(){
  clearTileUrls();const size=state.saved.reduce((n,p)=>n+p.blob.size,0),recent=state.libraryMode==='history';
  main.innerHTML=`<header class="page-top"><div><h1>Library</h1><p>${state.saved.length} saved files / ${bytes(size)} on this device</p></div>${state.saved.length?'<button class="text-button" id="play-all">'+icon('play')+'Play saved</button>':''}</header>
  <div class="library-tabs"><button class="library-tab ${!recent?'active':''}" id="saved-tab">Downloads</button><button class="library-tab ${recent?'active':''}" id="history-tab">Recently watched</button></div>
  <div class="library-tools"><input id="saved-search" placeholder="${recent?'Search history…':'Search saved media…'}" aria-label="Search library"><select id="saved-sort" aria-label="Sort library"><option value="recent">${recent?'Last watched':'Newest first'}</option><option value="oldest">Oldest first</option><option value="board">By board</option><option value="thread">By thread</option></select><button class="text-button" id="select-files">${state.selecting?'Done':'Select files'}</button>${recent?'<button class="text-button" id="clear-history">Clear history</button>':''}</div>
  <div class="library-actions" id="library-actions">${state.selecting?'<button class="text-button" id="select-all">Select visible</button><span id="selection-count">'+state.selected.size+' selected</span><button class="text-button delete-selected" id="delete-selected">'+(recent?'Remove selected':'Delete selected')+'</button>':!recent?'<span>Available offline</span><div class="library-filter"><button data-filter="all">All</button><button data-filter="unwatched">Unwatched</button><button data-filter="watched">Watched</button></div>':'<span>Resume from where you stopped</span>'}</div>
  ${state.failed.length?`<div class="error-message" id="failed-saves">${state.failed.length} save${state.failed.length===1?'':'s'} failed. <button class="text-button" id="retry-saves">Retry</button><button class="text-button" id="dismiss-failed">Dismiss</button></div>`:''}<div id="saved-content"></div>`;
  let visible=[];
  const threadOptions=[...new Map((recent?state.history:state.saved).map(p=>[`${p.board}_${p.thread}`,p])).values()];
  $('#saved-sort').insertAdjacentHTML('afterend',`<select id="library-thread" aria-label="Filter library by thread"><option value="">All threads</option>${threadOptions.map(p=>`<option value="${p.board}_${p.thread}">/${p.board}/ ${escape(p.title).slice(0,100)}</option>`).join('')}</select>`);
  function draw(){clearTileUrls();const q=$('#saved-search').value.toLowerCase(),sort=$('#saved-sort').value;let files=(recent?state.history:state.saved).filter(p=>`${p.filename} ${p.title} ${p.board}`.toLowerCase().includes(q)&&(!$('#library-thread').value||`${p.board}_${p.thread}`===$('#library-thread').value));if(!recent&&state.watchedFilter!=='all')files=files.filter(p=>state.history.some(h=>h.key===p.key)===(state.watchedFilter==='watched'));if(sort==='oldest')files.reverse();if(sort==='board')files.sort((a,b)=>a.board.localeCompare(b.board));if(sort==='thread')files.sort((a,b)=>a.board.localeCompare(b.board)||b.thread-a.thread);visible=files;
    $('#saved-content').innerHTML=!files.length?`<div class="empty-state">${icon('bookmark')}<h2>${q?'No matching files':recent?'Nothing watched yet':state.watchedFilter==='all'?'Your library starts here':'No files in this filter'}</h2><p>${q?'Try a different search.':recent?'Media you view appears here with its playback position.':state.watchedFilter==='all'?'Save a file or a whole thread. Play it here, even offline.':'Choose All to see your downloaded files.'}</p>${!recent&&!q&&state.watchedFilter==='all'?'<button class="primary" id="browse-empty">Explore threads</button>':''}</div>`:`<div class="saved-grid">${files.map(p=>{
      const saved=state.saved.find(s=>s.key===p.key),history=state.history.find(h=>h.key===p.key);
      const preview=saved?.thumb||(!['.webm','.mp4'].includes(p.ext)?saved?.blob:null);let src=preview?URL.createObjectURL(preview):thumbnail(p);if(preview)tileUrls.push(src);
      const watched=!!history,progress=history?.duration>0?Math.min(100,history.position*100/history.duration):0;
      const ratio=Math.min(2,Math.max(.65,(p.w||p.width||240)/(p.h||p.height||180)));
      return `<button class="saved-tile ${state.selected.has(p.key)?'selected-tile':''}" data-key="${p.key}" ${state.selecting?`aria-pressed="${state.selected.has(p.key)}"`:''}><div class="thread-cover" style="--ratio:${ratio}">${state.selecting?`<span class="selection-mark">${state.selected.has(p.key)?icon('check'):''}</span>`:''}${src?`<img src="${src}" alt="" loading="lazy" decoding="async">`:''}<span class="media-count">${icon(['.webm','.mp4'].includes(p.ext)?'play':'image')}${saved?bytes(saved.blob.size):'Stream'}</span>${progress?`<div class="progress-bar"><span style="width:${progress}%"></span></div>`:''}</div><h3>${escape(p.filename)}${escape(p.ext)}</h3><p>/${p.board}/ &nbsp; ${recent?relativeTime(p.watchedAt):new Date(p.savedAt).toLocaleDateString()}${watched&&!recent?' <span class="watched-mark">Watched</span>':''}${recent&&saved?' / Offline':''}</p></button>`;
    }).join('')}</div>`;
    $('#browse-empty')?.addEventListener('click',()=>navigate('browse'));$('#saved-content').querySelectorAll('[data-key]').forEach(e=>e.onclick=()=>{if(state.selecting){if(state.selected.has(e.dataset.key))state.selected.delete(e.dataset.key);else state.selected.add(e.dataset.key);$('#selection-count').textContent=state.selected.size+' selected';draw();return;}const index=files.findIndex(p=>p.key===e.dataset.key);if(recent)openHistory(files,index);else openSaved(files,index);});
  }
  $('#saved-tab').onclick=()=>{state.libraryMode='saved';state.selecting=false;state.selected.clear();renderSaved();};$('#history-tab').onclick=()=>{state.libraryMode='history';state.selecting=false;state.selected.clear();renderSaved();};$('#select-files').onclick=()=>{state.selecting=!state.selecting;state.selected.clear();renderSaved();};
  $('#saved-search').oninput=draw;$('#saved-sort').onchange=draw;$('#library-thread').onchange=draw;
  main.querySelectorAll('[data-filter]').forEach(e=>{e.classList.toggle('active',e.dataset.filter===state.watchedFilter);e.onclick=()=>{state.watchedFilter=e.dataset.filter;main.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b===e));draw();};});
  $('#select-all')?.addEventListener('click',()=>{const all=visible.length&&visible.every(p=>state.selected.has(p.key));visible.forEach(p=>all?state.selected.delete(p.key):state.selected.add(p.key));$('#selection-count').textContent=state.selected.size+' selected';draw();});
  $('#delete-selected')?.addEventListener('click',()=>{const keys=[...state.selected];if(!keys.length){toast('Select at least one file.');return;}confirmBatchDelete(keys,recent);});
  $('#clear-history')?.addEventListener('click',()=>confirmBatchDelete(state.history.map(p=>p.key),true));
  $('#play-all')?.addEventListener('click',()=>openSaved(state.saved,0));$('#retry-saves')?.addEventListener('click',()=>{const failed=state.failed.map(f=>f.post);state.failed=[];savePosts(failed);renderSaved();});$('#dismiss-failed')?.addEventListener('click',()=>{state.failed=[];renderSaved();});draw();updateDownloadBanner();sidebar();
}
function relativeTime(at){const minutes=Math.floor((Date.now()-at)/60000);if(minutes<1)return'Just now';if(minutes<60)return`${minutes}m ago`;if(minutes<1440)return`${Math.floor(minutes/60)}h ago`;return new Date(at).toLocaleDateString();}
function openHistory(files,index){clearMedia();state.feed={media:files.map(p=>state.saved.find(s=>s.key===p.key)||p),title:'Recently watched',offline:false,history:true};state.index=index;renderFeed();}
function confirmBatchDelete(keys,historyOnly=false){if(!keys.length){toast('There is nothing to remove.');return;}const total=state.saved.filter(p=>keys.includes(p.key)).reduce((n,p)=>n+p.blob.size,0);dialog(`<h2>${historyOnly?'Remove from history?':`Delete ${keys.length} saved file${keys.length===1?'':'s'}?`}</h2><p>${historyOnly?'Playback history and resume positions will be removed. Downloaded files stay in your library.':`${bytes(total)} will be removed from this device’s library. This cannot be undone.`}</p><div class="dialog-actions"><button class="secondary" id="cancel-batch">Cancel</button><button class="secondary danger" id="confirm-batch">${historyOnly?'Remove history':`Delete ${keys.length} files`}</button></div>`);$('#cancel-batch').onclick=()=>$('#dialog').close();$('#confirm-batch').onclick=async()=>{try{if(historyOnly){for(const k of keys)await stored('delete',k,'history');await refreshHistory();}else{await deleteSavedKeys(keys);await refreshSaved();}state.selected.clear();state.selecting=false;$('#dialog').close();renderSaved();toast(historyOnly?'History removed.':'Selected files deleted.');}catch{toast('Could not remove these items. Try again.');}};}
function openSaved(files,index){requestGeneration++;state.feed={media:files,title:'Your saved media',offline:true};state.index=index;renderFeed();}
function askDelete(post){dialog(`<h2>Delete this saved file?</h2><p>${escape(post.filename)}${escape(post.ext)} will be removed from this device’s library.</p><div class="dialog-actions"><button class="secondary" id="keep-file">Keep file</button><button class="secondary danger" id="delete-file">Delete file</button></div>`);$('#keep-file').onclick=()=>$('#dialog').close();$('#delete-file').onclick=async()=>{try{await stored('delete',key(post));await refreshSaved();state.feed.media=state.feed.media.filter(p=>key(p)!==key(post));$('#dialog').close();if(!state.feed.media.length)navigate('saved');else{state.index=Math.min(state.index,state.feed.media.length-1);renderFeed();}toast('File deleted.');}catch(e){toast('Could not delete this file. Try again.');}};}

svgAll();
refreshSaved().catch(()=>toast('Browser storage is unavailable. Saving may not work in private browsing.'));
refreshHistory().catch(()=>{});
if('serviceWorker'in navigator){let refreshing=false;navigator.serviceWorker.addEventListener('controllerchange',()=>{if(!refreshing){refreshing=true;location.reload();}});navigator.serviceWorker.register('/sw.js').catch(()=>{});}
try{state.boards=(await api('boards.json')).boards;await chooseBoard(state.board);}
catch(e){state.boards=[{board:'w',title:'Anime/Wallpapers',ws_board:1},{board:'wg',title:'Wallpapers/General',ws_board:1}];state.loading=false;renderBrowse(e.message);}
