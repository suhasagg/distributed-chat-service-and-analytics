import http from 'k6/http';
export const options={vus:5,duration:'20s'};
export default function(){http.post('http://localhost:8080/messages',JSON.stringify({roomId:'room-1',senderId:'load-user',messageType:'TEXT',content:'hello from k6'}),{headers:{'Content-Type':'application/json','X-Tenant-ID':'tenant-a'}});}
