"""Reads the flat message catalogues of the frontend (ar and en) into {key: text} dictionaries."""
import glob
import re

ENTRY = re.compile(r"^\s*'([^']+)':\s*(?:'((?:[^'\\]|\\.)*)'|\"((?:[^\"\\]|\\.)*)\"|`([^`]*)`)\s*,?\s*$")


def load(lang: str, root: str = 'frontend/src/i18n/messages') -> dict[str, str]:
    files = sorted(glob.glob(f'{root}/*.{lang}.ts')) + [f'{root}/{lang}.ts']
    out: dict[str, str] = {}
    for path in files:
        for line in open(path, encoding='utf-8'):
            m = ENTRY.match(line)
            if m:
                text = next(g for g in m.groups()[1:] if g is not None)
                out[m.group(1)] = text.replace("\\'", "'")
    return out


def codes(messages: dict[str, str], prefix: str) -> list[str]:
    """Upper-case codes directly under the prefix, in file order."""
    found = []
    for key in messages:
        if key.startswith(prefix):
            rest = key[len(prefix):]
            if re.fullmatch(r'[A-Z][A-Z0-9_]*', rest):
                found.append(rest)
    return found
