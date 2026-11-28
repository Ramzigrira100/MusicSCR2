import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const APP_HTML = path.join(__dirname, 'app', 'src', 'main', 'assets', 'index.html');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.wasm': 'application/wasm'
};

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  if (urlPath === '/' || urlPath === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    fs.createReadStream(APP_HTML).pipe(res);
    return;
  }

  const safePath = path.normalize(urlPath).replace(/^(\.\.(\/|\\|$))+/, '');
  const fullPath = path.join(__dirname, safePath);
  if (fullPath.startsWith(__dirname) && fs.existsSync(fullPath) && fs.statSync(fullPath).isFile()) {
    const ext = path.extname(fullPath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    fs.createReadStream(fullPath).pipe(res);
    return;
  }

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  fs.createReadStream(APP_HTML).pipe(res);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`X-Ray Mirror running on http://0.0.0.0:${PORT}`);
});
