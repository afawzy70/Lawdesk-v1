#!/usr/bin/env python3
"""Make a deterministic one-page PDF for isolated upload tests; no client data."""
from pathlib import Path

target = Path(__file__).with_name("synthetic.pdf")
parts = [b"%PDF-1.4\n"]
offsets = [0]
objects = [
    b"<< /Type /Catalog /Pages 2 0 R >>",
    b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    b"<< /Length 48 >>\nstream\nBT /F1 12 Tf 72 720 Td (SYN LawDesk Phase 8) Tj ET\nendstream",
]
for number, obj in enumerate(objects, 1):
    offsets.append(sum(map(len, parts)))
    parts.append(f"{number} 0 obj\n".encode() + obj + b"\nendobj\n")
xref = sum(map(len, parts))
parts.append(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
parts.extend(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
parts.append(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
target.write_bytes(b"".join(parts))
print(target)
