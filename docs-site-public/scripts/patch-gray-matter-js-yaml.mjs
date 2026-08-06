import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const enginePath = join(root, 'node_modules', 'gray-matter', 'lib', 'engines.js');

const before = readFileSync(enginePath, 'utf8');
const after = before
  .replace('parse: yaml.safeLoad.bind(yaml),', 'parse: yaml.load.bind(yaml),')
  .replace('stringify: yaml.safeDump.bind(yaml)', 'stringify: yaml.dump.bind(yaml)');

if (before === after) {
  if (before.includes('yaml.load.bind(yaml)') && before.includes('yaml.dump.bind(yaml)')) {
    process.exit(0);
  }

  throw new Error('gray-matter YAML engine did not match the expected js-yaml v3 API calls.');
}

writeFileSync(enginePath, after);
