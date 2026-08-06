# Easy BPM Public Docs

This site is built with [Docusaurus](https://docusaurus.io/).

## Install

```bash
npm ci
```

## Development

```bash
npm run start
```

This starts the Docusaurus development server with live reload.

## Search

The docs use local/offline search. The search index is generated only during production build, so the search box is limited in `npm run start`.

Use this command when you need to test search locally:

```bash
npm run serve:search
```

That script runs `npm run build` first, then serves the generated static site with the search index available.

## Build

```bash
npm run build
```

The generated static site is written to `build`.

## Versioning

Documentation versions are created from the current `docs` snapshot with Docusaurus.

```bash
npm run docusaurus docs:version v0.1.0-beta.1
```

This creates:

- `versions.json`
- `versioned_docs/version-v0.1.0-beta.1`
- `versioned_sidebars/version-v0.1.0-beta.1-sidebars.json`

Keep `docs` for the current/next documentation, and create a new version whenever a release needs a frozen docs snapshot.
