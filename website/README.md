# SparkPlusPlus Docs Site

This directory contains the Docusaurus source for the public SparkPlusPlus documentation site.

## Local Development

```bash
npm install
npm run dev
```

`npm run dev` runs the Docusaurus development server with hot reload enabled.

If file changes are not being detected on your machine, use polling mode:

```bash
npm run dev:poll
```

## Production Build

```bash
npm run build
```

The GitHub Actions workflows in the root repository build and deploy this site to GitHub Pages.
