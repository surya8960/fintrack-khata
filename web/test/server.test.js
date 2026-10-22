'use strict';
const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const { server, resolveFile } = require('../server');

test('resolveFile blocks path traversal and private files', () => {
  const root = require('node:path').resolve(__dirname, '..');
  for (const attack of ['/../etc/passwd', '/%2e%2e/%2e%2e/etc/passwd', '/..%2f..%2fetc/passwd']) {
    const resolved = resolveFile(attack);
    assert.ok(resolved === null || resolved.startsWith(root + require('node:path').sep), attack);
  }
  assert.strictEqual(resolveFile('/server.js'), null);
  assert.strictEqual(resolveFile('/package.json'), null);
  assert.strictEqual(resolveFile('/%'), null);
  assert.strictEqual(resolveFile('/test/server.test.js'), null);
  assert.match(resolveFile('/'), /index\.html$/);
});

test('server serves index and refuses unsafe requests', async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  try {
    const { port } = server.address();
    const get = (p, method = 'GET') => new Promise((resolve, reject) => {
      http.request({ host: '127.0.0.1', port, path: p, method }, res => {
        let body = '';
        res.on('data', d => (body += d));
        res.on('end', () => resolve({ status: res.statusCode, body, headers: res.headers }));
      }).on('error', reject).end();
    });

    const ok = await get('/');
    assert.strictEqual(ok.status, 200);
    assert.match(ok.body, /FinTrack Khata/);
    assert.strictEqual(ok.headers['x-content-type-options'], 'nosniff');

    assert.strictEqual((await get('/server.js')).status, 404);
    assert.strictEqual((await get('/test/server.test.js')).status, 404);
    assert.strictEqual((await get('/some/spa/route')).status, 200); // SPA fallback
    assert.strictEqual((await get('/', 'POST')).status, 405);
  } finally {
    server.close();
    server.closeAllConnections?.();
  }
});
