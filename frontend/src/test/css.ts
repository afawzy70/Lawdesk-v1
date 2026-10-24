/**
 * Test-only reader for the plain CSS files. jsdom has no layout engine, so layout regressions are pinned by
 * asserting the rules that produce the layout: `declarations(css, '.table-scroll')` returns the declarations of
 * every rule outside a media query whose selector list names exactly that selector.
 */
export function declarations(css: string, selector: string): Record<string, string> {
  const found: Record<string, string> = {};
  const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '');
  let depth = 0;
  let start = 0;
  let prelude = '';
  for (let i = 0; i < withoutComments.length; i += 1) {
    const char = withoutComments[i];
    if (char === '{') {
      if (depth === 0) prelude = withoutComments.slice(start, i).trim();
      depth += 1;
      start = i + 1;
    } else if (char === '}') {
      depth -= 1;
      if (depth === 0 && !prelude.startsWith('@') && prelude.split(',').some((s) => s.trim() === selector)) {
        for (const declaration of withoutComments.slice(start, i).split(';')) {
          const colon = declaration.indexOf(':');
          if (colon > 0) found[declaration.slice(0, colon).trim()] = declaration.slice(colon + 1).trim();
        }
      }
      start = i + 1;
    }
  }
  return found;
}
