// One look-ahead transfer. Completed files share a bounded in-memory LRU.
// The browser's own HTTP cache can reuse media older than this small window.
export class MediaPreloader {
  constructor(limit=64*1048576, perFile=32*1048576){this.limit=limit;this.perFile=perFile;this.files=new Map();this.bytes=0;this.active=null;}
  get(url){const hit=this.files.get(url);if(!hit)return null;this.files.delete(url);this.files.set(url,hit);return hit;}
  async next(url,expected,onReady=()=>{}){
    if(this.active?.url===url)return;
    this.cancel();
    if(!url||expected>this.perFile)return;
    if(this.files.has(url)){onReady();return;}
    const controller=new AbortController();const task={url,controller};this.active=task;
    try{
      const response=await fetch(url,{signal:controller.signal});if(!response.ok)return;
      const size=Number(response.headers.get('content-length')||0);if(size>this.perFile){await response.body.cancel();return;}
      const chunks=[];let total=0;const reader=response.body.getReader();
      while(true){const {done,value}=await reader.read();if(done)break;total+=value.byteLength;if(total>this.perFile){await reader.cancel();return;}chunks.push(value);}
      if(!total||size&&size!==total)return;
      const blob=new Blob(chunks,{type:response.headers.get('content-type')||'application/octet-stream'});
      while(this.bytes+total>this.limit&&this.files.size){const oldest=this.files.keys().next().value;this.bytes-=this.files.get(oldest).size;this.files.delete(oldest);}
      this.files.set(url,blob);this.bytes+=total;onReady();
    }catch{/* Preloading is optional; playback presents its own retry action. */}
    finally{if(this.active===task)this.active=null;}
  }
  cancel(){this.active?.controller.abort();this.active=null;}
  clear(){this.cancel();this.files.clear();this.bytes=0;}
}
