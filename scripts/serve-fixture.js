// scripts/serve-fixture.js
const http = require('http')
const fs = require('fs')
const path = require('path')
const dir = path.resolve(__dirname, '..', 'e2e', 'fixtures')
http.createServer((req, res) => {
  let u = decodeURIComponent((req.url || '/').split('?')[0])
  if (u === '/') u = '/sample-list.html'
  const p = path.join(dir, u)
  fs.readFile(p, (e, d) => {
    if (e) { res.statusCode = 404; res.end('not found'); return }
    res.setHeader('Content-Type', 'text/html; charset=utf-8')
    res.end(d)
  })
}).listen(7777, '127.0.0.1', () => {
  console.log('fixture listening on 7777, dir=' + dir)
})
