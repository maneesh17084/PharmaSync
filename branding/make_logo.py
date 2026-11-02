"""Builds PharmaSync brand images from the supplied 1254x1254 logo.

app-<n>.png       : emblem only on a rounded navy tile with a cyan edge, for window icons and small sizes.
logo-mark-<n>.png : emblem + wordmark with the tile removed (transparent), used on the sign-in panel.
Usage: python branding/make_logo.py branding/pharmasync-logo.png src/main/resources/com/pharmasync/brand  (needs Pillow and numpy)
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

src = Image.open(sys.argv[1]).convert('RGB')
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
S = 4  # supersampling for smooth mask edges


def rounded_mask(size, inset, radius):
    big = Image.new('L', (size * S, size * S), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (inset * S, inset * S, (size - inset) * S - 1, (size - inset) * S - 1), radius * S, fill=255)
    return big.resize((size, size), Image.LANCZOS)


N = src.size[0]

# ---- app icon: emblem on a fresh tile ----
emblem = src.crop((315, 150, 935, 770))  # cross, capsule, box and swoosh; no wordmark
T = 1024
tile = Image.new('RGBA', (T, T), (0, 0, 0, 0))
# Background: vertical navy gradient like the original tile.
bg = Image.new('RGB', (T, T))
top, bottom = (6, 26, 52), (2, 12, 30)
for y in range(T):
    t = y / (T - 1)
    ImageDraw.Draw(bg).line([(0, y), (T, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
radius = int(T * 0.225)
tile.paste(bg, (0, 0), rounded_mask(T, 0, radius))
# Emblem with its dark backdrop blended in through a soft round mask.
E = int(T * 0.80)
em = emblem.resize((E, E), Image.LANCZOS)
soft = Image.new('L', (E, E), 0)
ImageDraw.Draw(soft).rounded_rectangle((E * 0.04, E * 0.04, E * 0.96, E * 0.96), int(E * 0.18), fill=255)
soft = soft.filter(ImageFilter.GaussianBlur(E * 0.03))
tile.paste(em, ((T - E) // 2, (T - E) // 2 - int(T * 0.01)), soft)
# Cyan edge with a faint glow, echoing the original tile.
edge = Image.new('RGBA', (T * S, T * S), (0, 0, 0, 0))
w = int(T * 0.018) * S
ImageDraw.Draw(edge).rounded_rectangle((w // 2, w // 2, T * S - w // 2 - 1, T * S - w // 2 - 1), radius * S,
                                       outline=(34, 211, 238, 255), width=w)
edge = edge.resize((T, T), Image.LANCZOS)
tile = Image.alpha_composite(tile, edge)
for n in (16, 20, 24, 32, 40, 48, 64, 72, 96, 128, 256, 512):
    tile.resize((n, n), Image.LANCZOS).save(os.path.join(out, f'app-{n}.png'))

# ---- logo mark: emblem + wordmark with the tile removed, so it sits on any dark background ----
# The tile is dark navy (strongest channel <= ~75), the artwork is bright. Alpha follows the strongest channel,
# and colours are "un-mixed" from the navy so soft glows keep their hue instead of turning muddy.
px = np.asarray(src, dtype=np.float32)
strongest = px.max(axis=2)
M0, M1 = 72.0, 150.0
alpha = np.clip((strongest - M0) / (M1 - M0), 0.0, 1.0)
# Keep only the artwork's own area (emblem top to just below the wordmark ring's glow), feathered, so the
# tile's glowing border and its stray highlights are dropped.
inner = Image.new('L', (N, N), 0)
ImageDraw.Draw(inner).rounded_rectangle((88, 150, N - 88, 1012), 140, fill=255)
inner = inner.filter(ImageFilter.GaussianBlur(14))
alpha *= np.asarray(inner, dtype=np.float32) / 255.0
navy = np.array([1.0, 24.0, 52.0], dtype=np.float32)
safe = np.maximum(alpha, 1e-3)[..., None]
colour = np.clip((px - (1.0 - safe) * navy) / safe, 0, 255)
mark = Image.fromarray(np.dstack([colour, alpha * 255.0]).astype(np.uint8), 'RGBA')
mark = mark.crop(mark.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
pad = int(mark.width * 0.02)
framed = Image.new('RGBA', (mark.width + 2 * pad, mark.height + 2 * pad), (0, 0, 0, 0))
framed.paste(mark, (pad, pad))
for n in (512, 384, 256):
    h = round(framed.height * n / framed.width)
    framed.resize((n, h), Image.LANCZOS).save(os.path.join(out, f'logo-mark-{n}.png'))

print('done')
