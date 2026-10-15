// Scan a Maven local repository for corrupted (entirely NUL) .pom / .jar files.
// A zero-filled file means an interrupted/failed download; Maven cannot parse it.
const fs = require('fs');
const path = require('path');

const repo = process.argv[2];
const outFile = process.argv[3];

const candidates = [];
const walk = (dir) => {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { walk(p); continue; }
    if (!e.name.endsWith('.pom') && !e.name.endsWith('.jar')) continue;
    candidates.push(p);
  }
};
walk(repo);

const isAllNul = (file) => {
  let fd;
  try {
    fd = fs.openSync(file, 'r');
    const size = fs.fstatSync(fd).size;
    if (size === 0) return false; // empty file is a different problem; do not touch it
    const buf = Buffer.alloc(64 * 1024);
    let pos = 0;
    while (pos < size) {
      const n = fs.readSync(fd, buf, 0, Math.min(buf.length, size - pos), pos);
      if (n <= 0) break;
      for (let i = 0; i < n; i++) if (buf[i] !== 0) return false;
      pos += n;
    }
    return true;
  } catch { return false; } finally { if (fd !== undefined) fs.closeSync(fd); }
};

const corrupt = [];
for (const f of candidates) if (isAllNul(f)) corrupt.push(f);

console.log('SCANNED=' + candidates.length);
console.log('ALL_NUL=' + corrupt.length);
fs.writeFileSync(outFile, JSON.stringify(corrupt, null, 2), 'utf8');
console.log('LIST_WRITTEN=' + outFile);

// Group by the leaf artifact version directory so the report is readable
const byArtifact = new Map();
for (const f of corrupt) {
  const parts = f.split(path.sep);
  const key = parts.slice(-3, -1).join('/'); // artifactId/version
  byArtifact.set(key, (byArtifact.get(key) || 0) + 1);
}
console.log('--- distinct artifact/version pairs: ' + byArtifact.size + ' ---');
for (const [k, v] of [...byArtifact.entries()].sort()) console.log(k + '  (' + v + ')');
