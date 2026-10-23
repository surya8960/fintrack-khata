
'use strict';

/**
 * FinTrack Khata web dashboard server.
 * Works locally and on Render.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT) || 10000;
const HOST = '0.0.0.0';
const ROOT = path.resolve(__dirname);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const PRIVATE = new Set([
  'server.js',
  'package.json',
  'package-lock.json',
]);

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
};

function resolveFile(urlPath) {
  let pathname;

  try {
    pathname = decodeURIComponent(
      new URL(urlPath, 'http://localhost').pathname
    );
  } catch {
    return null;
  }

  if (pathname.includes('\0')) return null;

  const candidate = path.resolve(
    ROOT,
    '.' + (pathname === '/' ? '/index.html' : pathname)
  );

  if (
    candidate !== ROOT &&
    !candidate.startsWith(ROOT + path.sep)
  ) {
    return null;
  }

  const rel = path.relative(ROOT, candidate);

  if (
    PRIVATE.has(rel) ||
    rel.split(path.sep)[0] === 'test' ||
    rel.split(path.sep)[0] === 'node_modules'
  ) {
    return null;
  }

  return candidate;
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, {
      Allow: 'GET, HEAD',
      ...SECURITY_HEADERS,
    });
    return res.end('Method Not Allowed');
  }

  let file = resolveFile(req.url || '/');

  if (!file) {
    res.writeHead(404, SECURITY_HEADERS);
    return res.end('Not Found');
  }

  fs.stat(file, (err, stat) => {
    if (err || !stat.isFile()) {
      // A missing asset (e.g. /config.js, /favicon.ico) must be a real 404; serving index.html
      // there makes browsers try to execute HTML as JavaScript. Only extension-less routes
      // fall back to the single-page app.
      if (path.extname(file)) {
        res.writeHead(404, SECURITY_HEADERS);
        return res.end('Not Found');
      }
      file = path.join(ROOT, 'index.html');
    }

    fs.readFile(file, (readErr, content) => {
      if (readErr) {
        res.writeHead(500, SECURITY_HEADERS);
        return res.end('Internal Server Error');
      }

      res.writeHead(200, {
        'Cache-Control': path.basename(file) === 'index.html' ? 'no-cache' : 'public, max-age=300',
        'Content-Type':
          MIME[path.extname(file).toLowerCase()] ||
          'application/octet-stream',
        'Content-Length': content.length,
        ...SECURITY_HEADERS,
      });

      res.end(req.method === 'HEAD' ? undefined : content);
    });
  });
});

if (require.main === module) {
  server.listen(PORT, HOST, () => {
    console.log(`FinTrack Khata running on port ${PORT}`);
  });
}

module.exports = { server, resolveFile };
