import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validApiPath, validMediaPath } from '../validation.mjs';

test('only board catalog, thread, and board-list API paths are proxied',()=>{
  for(const path of ['boards.json','w/catalog.json','g/thread/123456.json','3/catalog.json'])assert.ok(validApiPath(path),path);
  for(const path of ['../secret','w/thread/0.json','https://evil.test/catalog.json','w/catalog.json?url=x','w//catalog.json','w/thread/-1.json','w/catalog.json/extra'])assert.equal(validApiPath(path),false,path);
});
test('media proxy rejects traversal, query injection, host override, and unsupported types',()=>{
  for(const path of ['w/123456789.jpg','gif/123456789.webm','g/123456789s.jpg','w/123456.gif'])assert.ok(validMediaPath(path),path);
  for(const path of ['w/../../file','w/1234.exe','//evil.test/123.jpg','w/123.jpg?url=https://evil.test','w/0.jpg','w/1234.jpg/extra','w%2f1234.jpg'])assert.equal(validMediaPath(path),false,path);
});
