export const threadKey = post => `${post.board}_${post.thread}`;

export function groupThreads(posts) {
  const groups=new Map();
  for(const post of posts){
    const id=threadKey(post);
    if(!groups.has(id))groups.set(id,{key:id,board:post.board,thread:post.thread,title:'',files:[],bytes:0,latest:0});
    const group=groups.get(id);
    if(!group.title&&post.title?.trim())group.title=post.title;
    group.files.push(post);group.bytes+=post.blob?.size||post.fsize||0;
    group.latest=Math.max(group.latest,post.savedAt||post.watchedAt||0);
  }
  return [...groups.values()].map(group=>({...group,title:group.title||`Thread #${group.thread}`,files:group.files.sort((a,b)=>a.no-b.no)})).sort((a,b)=>b.latest-a.latest);
}
