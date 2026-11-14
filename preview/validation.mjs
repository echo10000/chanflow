export const validApiPath = path => path === 'boards.json' || /^[a-z0-9]{1,12}\/(catalog|thread\/[1-9][0-9]*)\.json$/.test(path);
export const validMediaPath = path => /^[a-z0-9]{1,12}\/[1-9][0-9]*(s\.jpg|\.(jpg|jpeg|png|gif|webm|mp4))$/.test(path);
