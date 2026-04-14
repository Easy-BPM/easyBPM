# BPM Documentation Site

This documentation site is built with Docusaurus 3, a modern documentation framework.

## Prerequisites

You need to have Node.js (v16+) and npm installed on your system.

### Install Node.js & npm

**Windows**:
1. Download from https://nodejs.org/ (LTS recommended)
2. Run the installer
3. Verify installation:
   ```powershell
   node --version
   npm --version
   ```

**macOS**:
```bash
brew install node
```

**Linux**:
```bash
sudo apt install nodejs npm
```

## Setup

1. Navigate to the docs-site directory:
   ```bash
   cd docs-site
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

## Running the Documentation Site

### Development Mode (Local Preview)
```bash
npm start
```

This starts a local server at `http://localhost:3000` with hot-reload. Changes to markdown files are reflected immediately.

### Build for Production
```bash
npm run build
```

Creates a static site in the `build/` folder ready for deployment.

## Documentation Structure

The documentation is organized as follows:

### Core Docs

1. **overview.md** - High-level introduction to Easy BPM
2. **getting-started.md** - Setup prerequisites, quick start guide
3. **features-architecture.md** - Complete features list, architecture overview, data model
4. **api-controllers.md** - REST API endpoints for Process, Task, Form management
5. **metrics-observability.md** - Phase 3: Health checks, metrics collection, Prometheus integration
6. **message-events.md** - Message-based process triggering
7. **integration-testing.md** - Integration test examples
8. **examples.md** - Real-world process examples

### Navigation

All docs are automatically available in the sidebar. Add new docs by:
1. Creating a markdown file in the `docs/` folder
2. Adding the filename to `sidebars.js`

## Markdown Format

All documentation files use standard Markdown:

```markdown
# Main Heading

## Subheading

### Code Example
\`\`\`bash
command example
\`\`\`

### Quote
> This is important

### Links
[Link Text](../other-doc.md)
[External Link](https://example.com)
```

## Building & Deployment

### Local Build Verification
```bash
npm run build
```

### Preview Production Build
```bash
npx serve build
```

Then visit `http://localhost:3000`

### Deploy to GitHub Pages
```bash
npm run deploy
```

(Requires GitHub Pages configured in `docusaurus.config.js`)

## Troubleshooting

### Port 3000 already in use
```bash
npm start -- --port 3001
```

### Theme issues
Clear cache and rebuild:
```bash
rm -rf build node_modules/.cache
npm start
```

### Missing dependencies
```bash
npm install
npm audit fix
```

## Documentation Content Updated

The following documentation has been created/updated:

✅ **features-architecture.md** (NEW)
- Comprehensive feature list
- Architecture diagrams
- Data model overview
- Process execution flow
- Service descriptions
- Technology stack

✅ **api-controllers.md** (NEW)
- Process management endpoints
- Task management endpoints
- Form management endpoints
- Request/response examples
- Pagination support
- Error codes

✅ **metrics-observability.md** (NEW)
- Health endpoints documentation
- Metrics types and descriptions
- Prometheus integration guide
- Grafana dashboard setup
- Example queries
- Performance implications
- Troubleshooting

✅ **sidebars.js** - Updated navigation order
✅ **docusaurus.config.js** - Updated footer with new docs

## Next Steps

1. Install Node.js if not already installed
2. Run `npm install` in the docs-site directory
3. Run `npm start` to preview the documentation locally
4. Build with `npm run build` when ready

## Questions?

Refer to the Docusaurus documentation: https://docusaurus.io/docs
