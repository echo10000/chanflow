import {test} from 'node:test';
import assert from 'node:assert/strict';
import {groupThreads} from '../public/groups.js';

test('saved folders keep boards separate and restore the original post order',()=>{
  const posts=[{board:'w',thread:42,no:3,title:'Wallpapers',fsize:30,savedAt:30},{board:'g',thread:42,no:2,title:'Technology',fsize:20,savedAt:20},{board:'w',thread:42,no:1,title:'Wallpapers',fsize:10,savedAt:10}];
  const groups=groupThreads(posts);
  assert.equal(groups.length,2);assert.equal(groups[0].key,'w_42');assert.deepEqual(groups[0].files.map(p=>p.no),[1,3]);assert.equal(groups[0].bytes,40);
  assert.deepEqual(posts.map(p=>p.no),[3,2,1]);
});
test('old downloads without a title receive a readable folder name',()=>{
  assert.equal(groupThreads([{board:'w',thread:42,no:1,fsize:10}])[0].title,'Thread #42');
  assert.deepEqual(groupThreads([]),[]);
});
