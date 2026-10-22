'use strict';

/**
 * Minimal static file server for the web dashboard (local development / simple hosting).
 *   node server.js            -> http://127.0.0.1:3000
 *   PORT=8080 HOST=0.0.0.0 node server.js
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT) || 3000;
const HOST = process.env.HOST || '127.0.0.1'; // loopback by default; opt in to exposing it
const ROOT = path.resolve(__dirname);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

// Only these files are ever served; the server file itself and package metadata stay private.
const PRIVATE = new Set(['server.js', 'package.json', 'package-lock.json']);

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
};

function resolveFile(urlPath) {
  let pathname;
  try {
    pathname = decodeURIComponent(new URL(urlPath, 'http://localhost').pathname);
  } catch {
    return null; // malformed URL encoding
  }
  if (pathname.includes('\0')) return null;

  const candidate = path.resolve(ROOT, '.' + (pathname === '/' ? '/index.html' : pathname));
  // Path traversal guard: the resolved path must stay inside ROOT (the URL parser already
  // collapses `..` segments, this is defence in depth).
  if (candidate !== ROOT && !candidate.startsWith(ROOT + path.sep)) return null;
  const rel = path.relative(ROOT, candidate);
  if (PRIVATE.has(rel) || rel.split(path.sep)[0] === 'test' || rel.split(path.sep)[0] === 'node_modules') return null;
  return candidate;
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD', ...SECURITY_HEADERS });
    return res.end('Method Not Allowed');
  }

  let file = resolveFile(req.url || '/');
  if (!file) {
    res.writeHead(404, SECURITY_HEADERS);
    return res.end('Not Found');
  }

  fs.stat(file, (err, stat) => {
    // Single-page app fallback for unknown routes
    if (err || !stat.isFile()) file = path.join(ROOT, 'index.html');

    fs.readFile(file, (readErr, content) => {
      if (readErr) {
        res.writeHead(500, SECURITY_HEADERS);
        return res.end('Internal Server Error');
      }
      res.writeHead(200, {
        'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
        'Content-Length': content.length,
        ...SECURITY_HEADERS,
      });
      res.end(req.method === 'HEAD' ? undefined : content);
    });
  });
});

if (require.main === module) {
  server.listen(PORT, HOST, () => {
    console.log(`FinTrack Khata web dashboard: http://${HOST}:${PORT}`);
  });
}

module.exports = { server, resolveFile };
