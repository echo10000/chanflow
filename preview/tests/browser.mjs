import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {mkdir,readFile} from 'node:fs/promises';

const browser=await chromium.launch(process.platform==='win32'?{executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true}:{headless:true});
const context=await browser.newContext({viewport:{width:1365,height:900},serviceWorkers:'block',hasTouch:true});
const page=await context.newPage();
await page.addInitScript(()=>localStorage.setItem('board','w'));
const errors=[];page.on('pageerror',error=>errors.push(error.message));
const video=await readFile('preview/tests/fixtures/playback.webm');
const png=Buffer.from(await page.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=180;const g=c.getContext('2d');g.fillStyle='#71896d';g.fillRect(0,0,320,180);return c.toDataURL('image/png').split(',')[1];}),'base64');
const counts=new Map();
function postsFor(thread){return Array.from({length:thread===70?40:4},(_,i)=>({no:thread*1000+i+1,tim:100000+(thread-42)*100+i+1,ext:i<2?'.webm':'.png',filename:`fixture-${thread}-${i+1}`,w:320,h:180,fsize:i<2?video.length:png.length,com:`Post ${i+1}`,sub:i===0?`Thread ${thread}`:''}));}
await page.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;let body;if(path.endsWith('boards.json'))body={boards:[{board:'w',title:'Playback fixtures',ws_board:1},{board:'g',title:'Technology',ws_board:1}]};else if(path.endsWith('catalog.json'))body=[{threads:Array.from({length:40},(_,i)=>({...postsFor(42+i)[0],no:42+i,images:42+i===70?39:3,replies:4}))}];else body={posts:postsFor(Number(path.match(/thread\/(\d+)/)[1]))};return route.fulfill({contentType:'application/json',body:JSON.stringify(body)});});
await page.route('**/media/**',route=>{const path=new URL(route.request().url()).pathname;counts.set(path,(counts.get(path)||0)+1);const isVideo=path.endsWith('.webm');return route.fulfill({status:200,headers:{'content-type':isVideo?'video/webm':'image/png','content-length':String(isVideo?video.length:png.length)},body:isVideo?video:png});});
const waitFeed=()=>page.locator('#feed-stage').waitFor();
const waitSaved=number=>page.waitForFunction(n=>document.querySelector('.saved-count')?.textContent===String(n),number);
const sleep=ms=>page.waitForTimeout(ms);
async function openThread(id){await page.locator(`.thread-card[data-thread="${id}"]`).click();await waitFeed();}
async function saveThread(number){await page.getByRole('button',{name:'Save whole thread',exact:true}).click();await page.getByRole('button',{name:`Save all ${number} files`,exact:true}).click();}
async function findBoard(id){const box=await page.locator('#all-boards').boundingBox();await page.mouse.click(box.x+box.width/2,box.y+box.height/2);await page.locator(`.board-result[data-id="${id}"]`).click();}
async function swipe(dx,dy){const box=await page.locator('#feed-stage').boundingBox(),x=box.x+box.width/2,y=box.y+box.height/2;const cdp=await context.newCDPSession(page);try{await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});for(let i=1;i<=6;i++)await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x+dx*i/6,y:y+dy*i/6}]});await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});}finally{await cdp.detach();}}
async function checkCatalogReturn(phone){
  if(phone)await page.getByRole('button',{name:'Phone',exact:true}).click();
  await page.mouse.move(phone?680:700,700);await page.mouse.wheel(0,550);
  await page.waitForFunction(isPhone=>(isPhone?document.querySelector('#main').scrollTop:scrollY)>300,phone);
  const index=await page.locator('.thread-card').evaluateAll(cards=>cards.findIndex(card=>{const r=card.getBoundingClientRect();return r.top>140&&r.bottom<innerHeight-45;}));assert.ok(index>=0);
  const position=await page.evaluate(isPhone=>isPhone?document.querySelector('#main').scrollTop:scrollY,phone);
  await page.locator('.thread-card').nth(index).click();await waitFeed();await page.getByRole('button',{name:'Back',exact:true}).click();
  await page.waitForFunction(({phone,position})=>Math.abs((phone?document.querySelector('#main').scrollTop:scrollY)-position)<3,{phone,position});
  await findBoard('g');await page.locator('.thread-card').first().waitFor();
  await findBoard('w');
  await page.waitForFunction(({phone,position})=>Math.abs((phone?document.querySelector('#main').scrollTop:scrollY)-position)<3,{phone,position});
  await page.mouse.move(phone?680:700,500);await page.mouse.wheel(0,-10000);await sleep(150);
  if(phone)await page.getByRole('button',{name:'Desktop',exact:true}).click();
}
try{
  await page.goto('http://127.0.0.1:4173');await page.locator('.thread-card').first().waitFor();
  await checkCatalogReturn(false);await checkCatalogReturn(true);
  await page.mouse.move(700,450);await page.mouse.wheel(0,-10000);await sleep(150);
  await openThread(42);await page.waitForFunction(()=>document.querySelector('#next-status')?.textContent==='Next ready');
  await page.getByRole('button',{name:'Next media',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#feed-video')?.src.startsWith('blob:'));
  await page.waitForFunction(()=>document.querySelector('#feed-video')?.currentTime>.1);
  assert.equal(counts.get('/media/w/100002.webm'),1,'the preloaded next video is reused');
  await page.keyboard.press('ArrowRight');await page.waitForFunction(()=>document.querySelector('.feed-header h2')?.textContent==='Thread 43');
  await sleep(450);await page.keyboard.press('ArrowLeft');await page.waitForFunction(()=>document.querySelector('.feed-header h2')?.textContent==='Thread 42');
  assert.equal(await page.locator('.position-label').innerText(),'2 / 4','returning to a thread restores its file');
  await sleep(450);await swipe(-180,0);await page.waitForFunction(()=>document.querySelector('.feed-header h2')?.textContent==='Thread 43');
  await sleep(450);await swipe(180,0);await page.waitForFunction(()=>document.querySelector('.feed-header h2')?.textContent==='Thread 42');
  await swipe(0,-180);await page.waitForFunction(()=>document.querySelector('.position-label')?.textContent==='3 / 4');await sleep(300);await swipe(0,180);await page.waitForFunction(()=>document.querySelector('.position-label')?.textContent==='2 / 4');
  await page.getByRole('button',{name:'Unmute',exact:true}).click();await page.getByRole('button',{name:'Mute',exact:true}).click();
  await page.getByRole('button',{name:'Fill',exact:true}).click();assert.equal(await page.locator('.feed-stage.fill').count(),1);await page.getByRole('button',{name:'Fit',exact:true}).click();
  await page.getByRole('button',{name:'Post',exact:true}).click();await page.getByRole('button',{name:'Close dialog'}).click();
  await saveThread(4);await page.getByRole('button',{name:'Library',exact:false}).first().click();await waitSaved(4);
  await mkdir('preview/test-results',{recursive:true});await page.screenshot({path:'preview/test-results/thread-folders-desktop.png',scale:'css'});
  assert.equal(await page.locator('.thread-folder').count(),1,'Downloads opens to thread folders');assert.equal(await page.locator('.saved-tile').count(),0);
  await page.getByRole('button',{name:'Open saved thread w_42',exact:true}).click();assert.equal(await page.locator('.saved-tile').count(),4);
  await page.getByRole('button',{name:'Select files',exact:true}).click();await page.locator('.saved-tile').first().click();await page.getByRole('button',{name:'Delete selected',exact:true}).click();
  await page.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(await page.locator('.saved-tile').count(),4);
  await page.getByRole('button',{name:'Delete selected',exact:true}).click();await page.locator('#confirm-batch').click();await waitSaved(3);assert.equal(await page.locator('.saved-tile').count(),3);
  await page.getByRole('button',{name:'All threads',exact:true}).click();assert.equal(await page.locator('.thread-folder').count(),1);
  await page.getByRole('button',{name:'Explore',exact:true}).click();await openThread(43);await saveThread(4);
  await page.getByRole('button',{name:'Library',exact:false}).first().click();await waitSaved(7);assert.equal(await page.locator('.thread-folder').count(),2);
  await page.getByRole('button',{name:'Select threads',exact:true}).click();await page.getByRole('button',{name:'Select visible',exact:true}).click();await page.getByRole('button',{name:'Delete threads',exact:true}).click();
  await page.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(await page.locator('.thread-folder').count(),2);
  await page.getByRole('button',{name:'Delete threads',exact:true}).click();await page.locator('#confirm-batch').click();await waitSaved(0);assert.equal(await page.locator('.thread-folder').count(),0);
  await page.getByRole('button',{name:'Recently watched',exact:true}).click();await page.getByRole('button',{name:'Downloads',exact:true}).click();
  await page.getByRole('button',{name:'Explore',exact:true}).click();await page.getByRole('button',{name:'Show more threads',exact:true}).click();await openThread(70);await saveThread(40);
  await page.getByRole('button',{name:'Library',exact:false}).first().click();await waitSaved(40);await page.getByRole('button',{name:'Open saved thread w_70',exact:true}).click();
  await page.getByRole('button',{name:'Phone',exact:true}).click();await page.mouse.move(680,700);await page.mouse.wheel(0,650);
  await page.waitForFunction(()=>document.querySelector('#main').scrollTop>300);
  const filePosition=await page.locator('#main').evaluate(e=>e.scrollTop);
  const fileIndex=await page.locator('.saved-tile').evaluateAll(cards=>cards.findIndex(card=>{const r=card.getBoundingClientRect();return r.top>140&&r.bottom<innerHeight-60;}));assert.ok(fileIndex>=0);
  await page.locator('.saved-tile').nth(fileIndex).click();await waitFeed();await page.getByRole('button',{name:'Back',exact:true}).click();
  await page.waitForFunction(position=>Math.abs(document.querySelector('#main').scrollTop-position)<3,filePosition);
  await page.getByRole('button',{name:'All threads',exact:true}).click();await page.getByRole('button',{name:'Open saved thread w_70',exact:true}).click();
  await page.waitForFunction(position=>Math.abs(document.querySelector('#main').scrollTop-position)<3,filePosition);
  await page.screenshot({path:'preview/test-results/folder-scrolled-phone.png',scale:'css'});
  await page.getByRole('button',{name:'Desktop',exact:true}).click();await page.locator('#all-boards').click();await page.getByRole('textbox',{name:'Search boards'}).fill('Technology');await page.locator('.board-result').first().click();await page.locator('.thread-card').first().waitFor();
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await mkdir('preview/test-results',{recursive:true});await page.screenshot({path:'preview/test-results/mobile-fixtures.png',scale:'css'});assert.deepEqual(errors,[]);
  console.log('Passed: catalog scroll per board in desktop and phone modes; thread folders; per-file and whole-thread deletion; original-order playback; left/right thread navigation with position restore; preload reuse; folder scroll restore; narrow layout.');
}catch(error){await mkdir('preview/test-results',{recursive:true});await page.screenshot({path:'preview/test-results/failure.png',scale:'css'}).catch(()=>{});console.error((await page.locator('body').innerText().catch(()=>'')).slice(-2500));throw error;}
finally{await browser.close();}
