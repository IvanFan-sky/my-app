#!/usr/bin/env node
/**
 * Remove corrupted (entirely NUL) .pom files from a Maven local repository.
 *
 * Why this exists: on 2026-10-12 an interrupted download left 85 Spring Boot
 * 3.5.16 POM files zero-filled in F:\JavaTool\Maven\maven_repository. Maven
 * cannot parse them and fails every build with:
 *   Non-parseable POM ... only whitespace content allowed before start tag
 * Deleting them makes Maven re-download the correct content.
 *
 * Safety rules:
 *   1. Only paths listed in the manifest are considered, and each must resolve
 *      to a location strictly inside --repo.
 *   2. The whole file is re-read before deletion; it is deleted only if every
 *      byte is still NUL. Anything else is skipped and reported.
 *   3. Only the .pom itself plus its same-named .sha1 / .lastUpdated markers are
 *      removed. Sibling .jar files and every other file are left untouched.
 *
 * Usage:
 *   node repair-corrupt-maven.js --repo F:\JavaTool\Maven\maven_repository \
 *        --list <manifest.json> [--dry-run]
 *
 * The manifest is a JSON array of absolute paths, as produced by
 * scan-corrupt-maven.js.
 */
const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const out = { dryRun: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry-run') out.dryRun = true;
    else if (a === '--repo') out.repo = argv[++i];
    else if (a === '--list') out.list = argv[++i];
    else if (a === '--help' || a === '-h') out.help = true;
    else throw new Error('unknown argument: ' + a);
  }
  return out;
}

function isAllNul(file) {
  let fd;
  try {
    fd = fs.openSync(file, 'r');
    const size = fs.fstatSync(fd).size;
    if (size === 0) return false; // empty is a different problem; do not touch
    const buf = Buffer.alloc(64 * 1024);
    let pos = 0;
    while (pos < size) {
      const n = fs.readSync(fd, buf, 0, Math.min(buf.length, size - pos), pos);
      if (n <= 0) break;
      for (let i = 0; i < n; i++) if (buf[i] !== 0) return false;
      pos += n;
    }
    return true;
  } catch {
    return false;
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log('usage: node repair-corrupt-maven.js --repo <repo> --list <manifest.json> [--dry-run]');
    return 0;
  }
  if (!args.repo || !args.list) throw new Error('--repo and --list are required');

  const repoRoot = path.resolve(args.repo);
  const manifest = JSON.parse(fs.readFileSync(args.list, 'utf8'));
  if (!Array.isArray(manifest)) throw new Error('manifest must be a JSON array');

  let deleted = 0;
  let reclaimed = 0;
  const skipped = [];

  for (const entry of manifest) {
    const full = path.resolve(entry);

    // Rule 1: must be strictly inside the repository
    const rel = path.relative(repoRoot, full);
    if (rel.startsWith('..') || path.isAbsolute(rel)) {
      skipped.push('outside repo: ' + full);
      continue;
    }
    if (!fs.existsSync(full)) {
      skipped.push('already gone: ' + full);
      continue;
    }
    if (!full.toLowerCase().endsWith('.pom')) {
      skipped.push('not a .pom: ' + full);
      continue;
    }

    // Rule 2: re-verify the whole file is still all NUL
    if (!isAllNul(full)) {
      skipped.push('no longer all-NUL, kept: ' + full);
      continue;
    }

    // Rule 3: only this .pom and its same-named markers
    for (const target of [full, full + '.sha1', full + '.lastUpdated']) {
      if (!fs.existsSync(target)) continue;
      const size = fs.statSync(target).size;
      if (args.dryRun) {
        console.log('[dry-run] would delete ' + target);
      } else {
        fs.unlinkSync(target);
      }
      deleted++;
      reclaimed += size;
    }
  }

  console.log('REPO=' + repoRoot);
  console.log('LISTED=' + manifest.length);
  console.log('DELETED_FILES=' + deleted);
  console.log('RECLAIMED_BYTES=' + reclaimed);
  console.log('SKIPPED=' + skipped.length);
  for (const s of skipped.slice(0, 20)) console.log('  ' + s);

  if (!args.dryRun) {
    const still = manifest.filter((p) => fs.existsSync(p));
    console.log('STILL_PRESENT=' + still.length);
    for (const s of still.slice(0, 10)) console.log('  ' + s);
  }
  return 0;
}

try {
  process.exit(main());
} catch (err) {
  console.error('ERROR: ' + err.message);
  process.exit(1);
}
