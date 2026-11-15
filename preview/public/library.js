export function createLibrary({state,main,$,icon,escape,bytes,groupThreads,threadKey,imageSource,clearTileUrls,rememberScroll,restoreScroll,navigate,openSaved,openHistory,confirmBatchDelete,savePosts,updateDownloadBanner,sidebar,relativeTime,toast}) {
  const views=new Map();
  return function renderSaved(){
    clearTileUrls();
    const groups=groupThreads(state.saved),recent=state.libraryMode==='history';
    const folder=recent?null:groups.find(g=>g.key===state.libraryThread);
    if(!recent&&!folder)state.libraryThread='';
    const root=!recent&&!folder,scope=recent?'history':folder?.key||'threads';
    const view=views.get(scope)||{query:'',sort:'recent',board:''};
    const source=recent?state.history:folder?.files||state.saved;
    const watched=new Set(state.history.map(p=>p.key));
    const total=state.saved.reduce((n,p)=>n+p.blob.size,0);
    const selectLabel=root?'Select threads':'Select files';
    main.innerHTML=`<header class="page-top"><div><h1>Library</h1><p>${groups.length} ${groups.length===1?"thread":"threads"} / ${state.saved.length} ${state.saved.length===1?"file":"files"} / ${bytes(total)} on this device</p></div>${folder?`<button class="text-button" id="play-folder">${icon('play')}${folder.files.some(p=>watched.has(p.key))?'Resume thread':'Play thread'}</button>`:''}</header>
      <div class="library-tabs"><button class="library-tab ${!recent?'active':''}" id="saved-tab">Downloads</button><button class="library-tab ${recent?'active':''}" id="history-tab">Recently watched</button></div>
      ${folder?`<div class="folder-navigation"><button class="text-button" id="all-saved-threads">${icon('arrow')}All threads</button><button class="text-button danger-text" id="delete-folder">${icon('trash')}Delete thread</button></div><div class="folder-heading"><h2>${escape(folder.title)}</h2><p>/${folder.board}/ &nbsp; #${folder.thread} / ${folder.files.length} files / ${bytes(folder.bytes)}</p></div>`:''}
      <div class="library-tools"><input id="saved-search" placeholder="${recent?'Search history…':root?'Search saved threads…':'Search files…'}" aria-label="Search library" value="${escape(view.query)}">
      ${root||recent?`<select id="saved-sort" aria-label="Sort library"><option value="recent">${recent?'Last watched':'Newest threads'}</option><option value="oldest">Oldest first</option><option value="board">By board</option></select><select id="library-board" aria-label="Filter saved boards"><option value="">All boards</option>${[...new Set(source.map(p=>p.board))].sort().map(b=>`<option value="${b}">/${b}/</option>`).join('')}</select>`:''}
      <button class="text-button" id="select-files">${state.selecting?'Done':selectLabel}</button>${recent?'<button class="text-button" id="clear-history">Clear history</button>':''}</div>
      <div class="library-actions">${state.selecting?`<button class="text-button" id="select-all">Select visible</button><span id="selection-count"></span><button class="text-button delete-selected" id="delete-selected">${recent?'Remove selected':root?'Delete threads':'Delete selected'}</button>`:!recent?'<span>Available offline</span><div class="library-filter"><button data-filter="all">All</button><button data-filter="unwatched">Unwatched</button><button data-filter="watched">Watched</button></div>':'<span>Resume from where you stopped</span>'}</div>
      ${state.failed.length?`<div class="error-message">${state.failed.length} saves failed.<button class="text-button" id="retry-saves">Retry</button><button class="text-button" id="dismiss-failed">Dismiss</button></div>`:''}<div id="saved-content"></div>`;
    if($('#saved-sort'))$('#saved-sort').value=view.sort;
    if($('#library-board'))$('#library-board').value=view.board;
    let visible=[],visibleGroups=[];
    function selectedCount(){if(!$('#selection-count'))return;const count=root?groups.filter(g=>g.files.every(p=>state.selected.has(p.key))).length:state.selected.size;$('#selection-count').textContent=`${count} ${root?'threads':'files'} selected`;}
    function draw(){
      clearTileUrls();view.query=$('#saved-search').value;view.sort=$('#saved-sort')?.value||'recent';view.board=$('#library-board')?.value||'';views.set(scope,view);
      const q=view.query.toLowerCase();const matches=p=>`${p.filename} ${p.title} ${p.board} ${p.thread}`.toLowerCase().includes(q);
      if(root){
        visibleGroups=groups.filter(g=>(!view.board||g.board===view.board)&&g.files.some(matches)&&(state.watchedFilter==='all'||(state.watchedFilter==='watched'?g.files.every(p=>watched.has(p.key)):g.files.some(p=>!watched.has(p.key)))));
        if(view.sort==='oldest')visibleGroups.reverse();if(view.sort==='board')visibleGroups.sort((a,b)=>a.board.localeCompare(b.board));
        visible=visibleGroups.flatMap(g=>g.files);
        $('#saved-content').innerHTML=visibleGroups.length?`<div class="thread-folders">${visibleGroups.map(g=>{
          const marked=g.files.every(p=>state.selected.has(p.key)),covers=g.files.slice(0,4),seen=g.files.filter(p=>watched.has(p.key)).length;
          return `<article class="thread-folder ${marked&&state.selecting?'selected-tile':''}"><button class="folder-open" data-folder="${g.key}" aria-label="Open saved thread ${g.key}" ${state.selecting?`aria-pressed="${marked}"`:''}><div class="folder-cover cover-${covers.length}">${covers.map(p=>`<img src="${imageSource(p)}" alt="" loading="lazy" decoding="async">`).join('')}${state.selecting?`<span class="selection-mark">${marked?icon('check'):''}</span>`:''}<span class="media-count">${g.files.length} files</span></div><h3>${escape(g.title)}</h3><p>/${g.board}/ &nbsp; #${g.thread} / ${bytes(g.bytes)}</p><span class="folder-watched">${seen} / ${g.files.length} watched</span></button>${state.selecting?'':`<button class="folder-delete icon-button" data-delete-folder="${g.key}" aria-label="Delete saved thread ${g.key}">${icon('trash')}</button>`}</article>`;
        }).join('')}</div>`:emptyMessage(q,recent,root);
        main.querySelectorAll('[data-folder]').forEach(e=>e.onclick=()=>{const g=groups.find(g=>g.key===e.dataset.folder);if(state.selecting){const all=g.files.every(p=>state.selected.has(p.key));g.files.forEach(p=>all?state.selected.delete(p.key):state.selected.add(p.key));draw();}else{rememberScroll();state.libraryThread=g.key;renderSaved();}});
        main.querySelectorAll('[data-delete-folder]').forEach(e=>e.onclick=()=>confirmBatchDelete(groups.find(g=>g.key===e.dataset.deleteFolder).files.map(p=>p.key),false));
      }else{
        visible=source.filter(p=>matches(p)&&(!view.board||p.board===view.board)&&(recent||state.watchedFilter==='all'||watched.has(p.key)===(state.watchedFilter==='watched')));
        if(view.sort==='oldest')visible.reverse();if(view.sort==='board')visible.sort((a,b)=>a.board.localeCompare(b.board));
        $('#saved-content').innerHTML=visible.length?`<div class="saved-grid">${visible.map(p=>{
          const saved=state.saved.find(s=>s.key===p.key),h=state.history.find(h=>h.key===p.key),progress=h?.duration>0?Math.min(100,h.position*100/h.duration):0;
          const ratio=Math.min(2,Math.max(.65,(p.w||p.width||240)/(p.h||p.height||180)));
          return `<button class="saved-tile ${state.selected.has(p.key)?'selected-tile':''}" data-key="${p.key}" ${state.selecting?`aria-pressed="${state.selected.has(p.key)}"`:''}><div class="thread-cover" style="--ratio:${ratio}">${state.selecting?`<span class="selection-mark">${state.selected.has(p.key)?icon('check'):''}</span>`:''}<img src="${imageSource(p)}" alt="" loading="lazy" decoding="async"><span class="media-count">${icon(['.webm','.mp4'].includes(p.ext)?'play':'image')}${saved?bytes(saved.blob.size):'Stream'}</span>${progress?`<div class="progress-bar"><span style="width:${progress}%"></span></div>`:''}</div><h3>${escape(p.filename)}${escape(p.ext)}</h3><p>/${p.board}/ &nbsp; ${recent?relativeTime(p.watchedAt):'Offline'}${watched.has(p.key)&&!recent?' / Watched':''}${recent&&saved?' / Offline':''}</p></button>`;
        }).join('')}</div>`:emptyMessage(q,recent,root);
        main.querySelectorAll('[data-key]').forEach(e=>e.onclick=()=>{const p=visible.find(p=>p.key===e.dataset.key);if(state.selecting){state.selected.has(p.key)?state.selected.delete(p.key):state.selected.add(p.key);draw();}else{rememberScroll();if(recent)openHistory(visible,visible.indexOf(p));else openSaved(folder.files,folder.files.findIndex(f=>f.key===p.key));}});
      }
      $('#browse-empty')?.addEventListener('click',()=>navigate('browse'));selectedCount();
    }
    function up(){rememberScroll();state.libraryThread='';state.selecting=false;state.selected.clear();renderSaved();}
    $('#saved-tab').onclick=()=>{rememberScroll();state.libraryMode='saved';state.libraryThread='';state.selecting=false;state.selected.clear();renderSaved();};
    $('#history-tab').onclick=()=>{rememberScroll();state.libraryMode='history';state.selecting=false;state.selected.clear();renderSaved();};
    $('#select-files').onclick=()=>{rememberScroll();state.selecting=!state.selecting;state.selected.clear();renderSaved();};
    $('#all-saved-threads')?.addEventListener('click',up);
    $('#play-folder')?.addEventListener('click',()=>{rememberScroll();openSaved(folder.files);});
    $('#delete-folder')?.addEventListener('click',()=>confirmBatchDelete(folder.files.map(p=>p.key),false));
    $('#saved-search').oninput=draw;if($('#saved-sort'))$('#saved-sort').onchange=draw;if($('#library-board'))$('#library-board').onchange=draw;
    main.querySelectorAll('[data-filter]').forEach(e=>{e.classList.toggle('active',e.dataset.filter===state.watchedFilter);e.onclick=()=>{state.watchedFilter=e.dataset.filter;main.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b===e));draw();};});
    $('#select-all')?.addEventListener('click',()=>{const all=visible.length&&visible.every(p=>state.selected.has(p.key));visible.forEach(p=>all?state.selected.delete(p.key):state.selected.add(p.key));draw();});
    $('#delete-selected')?.addEventListener('click',()=>{if(!state.selected.size){toast('Select at least one item.');return;}confirmBatchDelete([...state.selected],recent);});
    $('#clear-history')?.addEventListener('click',()=>confirmBatchDelete(state.history.map(p=>p.key),true));
    $('#retry-saves')?.addEventListener('click',()=>{const failed=state.failed.map(f=>f.post);state.failed=[];savePosts(failed);renderSaved();});$('#dismiss-failed')?.addEventListener('click',()=>{state.failed=[];renderSaved();});
    draw();updateDownloadBanner();sidebar();restoreScroll();
  };

  function emptyMessage(q,recent,root){return `<div class="empty-state">${icon('bookmark')}<h2>${q?'No matching results':recent?'Nothing watched yet':root&&!state.saved.length?'Your library starts here':'No files in this filter'}</h2><p>${q?'Try a different search.':recent?'Threads you watch appear here with their playback position.':root?'Downloaded media is organized by its original thread.':'Choose another filter to see more files.'}</p>${root&&!state.saved.length&&!q?'<button class="primary" id="browse-empty">Explore threads</button>':''}</div>`;}
}
