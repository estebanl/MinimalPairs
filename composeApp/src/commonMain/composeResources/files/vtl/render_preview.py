"""Render the exported meshes as a reviewable SVG contact sheet."""
from pathlib import Path
import math
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / 'design/vocaltractlab-preview'
COLORS = ['#cbb39d', '#d26d85', '#fff1d5', '#ae4b65', '#ae9689']

def read(path):
    data = path.read_bytes()
    assert data[:4] == b'VTLM'
    version, count = struct.unpack_from('<II', data, 4)
    assert version == 1
    offset = 12
    faces = []
    for _ in range(count):
        part, vertices, triangles = struct.unpack_from('<III', data, offset)
        offset += 12
        points = list(struct.iter_unpack('<fff', data[offset:offset + vertices * 12]))
        offset += vertices * 12
        for _ in range(triangles):
            indices = struct.unpack_from('<III', data, offset)
            offset += 48  # indices and three corner normals
            # Transform upstream x=anterior, y=superior, z=lateral to viewer axes.
            p = [(points[i][2], points[i][1], points[i][0]) for i in indices]
            clipped = []
            for a, b in zip(p, p[1:] + p[:1]):
                ia, ib = a[0] >= 0, b[0] >= 0
                if ia:
                    clipped.append(a)
                if ia != ib:
                    t = a[0] / (a[0] - b[0])
                    clipped.append(tuple(a[k] + t * (b[k] - a[k]) for k in range(3)))
            for j in range(1, len(clipped) - 1):
                faces.append((part, [clipped[0], clipped[j], clipped[j + 1]]))
    assert offset == len(data)
    return faces

consonants = '--consonants' in sys.argv
rhotic = '--r-only' in sys.argv
width, height = (800, 540) if rhotic else (1200, 1800)
subtitle = ('Two authored /r/ examples • illustrative / unreviewed • not measured speaker anatomy' if rhotic else
            'Nine consonant gestures + one reused rounded pose • fixed context • unreviewed for English • GPL-3.0-or-later' if consonants else
            'Six original JD3 presets + four derived teaching poses • side cutaway • GPL-3.0-or-later')
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
       f'<rect width="{width}" height="{height}" fill="#f5f3ed"/>',
       '<text x="30" y="38" font-size="25" font-family="sans-serif" fill="#252b2a">VocalTractLab • exported JD3 anatomy</text>',
       f'<text x="30" y="66" font-size="15" font-family="sans-serif" fill="#59615d">{subtitle}</text>']
vowels = [('fleece','/iː/ — source i'), ('kit','/ɪ/ — source I'), ('dress','/e/ (DRESS) — source E'),
          ('foot','/ʊ/ — source U'), ('goose','/uː/ — source u'), ('thought','/ɔː/ — source O'),
          ('trap','/æ/ — derived a + E'), ('palm','/ɑː/ — derived retracted a'),
          ('strut','/ʌ/ — derived a + @'), ('lot','/ɒ/ — derived a + O')]
gestures = [('bilabial-closure','/p b/ — lip closure'), ('alveolar-closure','/t d/ — tongue closure'),
            ('velar-closure','/k ɡ/ — velar closure'), ('labiodental-fricative','/f v/ — lip / teeth'),
            ('dental-fricative','/θ ð/ — tongue / teeth'), ('alveolar-fricative','/s z/ — narrow channel'),
            ('postalveolar-fricative','/ʃ ʒ/ + affricate friction'), ('postalveolar-closure','/tʃ dʒ/ — closure phase'),
            ('alveolar-lateral','/l/ — lateral contact'), ('goose','/w/ — reused u approximation')]
rhoticExamples = [('rhotic-bunched', '/r/ — Bunched example'), ('rhotic-tip-up', '/r/ — Tip-up example')]
for index, (shape, label) in enumerate(rhoticExamples if rhotic else gestures if consonants else vowels):
    x0, y0 = (index % 3) * 400, 95 + (index // 3) * 415
    svg.append(f'<rect x="{x0+12}" y="{y0}" width="376" height="398" rx="16" fill="#e8e6df"/>')
    svg.append(f'<text x="{x0+28}" y="{y0+30}" font-family="sans-serif" font-size="19" fill="#252b2a">{label}</text>')
    # Side view: surviving positive lateral coordinates are farther from viewer.
    faces = sorted(read(FOLDER / 'meshes' / f'{shape}.vtlm'), key=lambda f: -sum(p[0] for p in f[1]))
    for part, points in faces:
        a,b,c = points
        u = [b[k]-a[k] for k in range(3)]; v = [c[k]-a[k] for k in range(3)]
        normal = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
        length = max(1e-9, math.sqrt(sum(n*n for n in normal)))
        light = .55+.45*abs((normal[0]*.8+normal[1]*.6)/length)
        rgb = [min(255,round(int(COLORS[part][k:k+2],16)*light)) for k in (1,3,5)]
        fill = '#%02x%02x%02x' % tuple(rgb)
        coords = ' '.join(f'{x0+200+(p[2]-2)*26:.2f},{y0+200-(p[1]+3.7)*26:.2f}' for p in points)
        svg.append(f'<polygon points="{coords}" fill="{fill}" stroke="{fill}" stroke-width="0.3"/>')
svg += [f'<text x="30" y="{height-20}" font-size="14" font-family="sans-serif" fill="#59615d">{'Neither variant is obligatory. Tongue surface only; GPL-3.0-or-later.' if rhotic else 'Teaching mappings need English phonetic review; snapshots are not motion, audible voicing, or learner anatomy.' if consonants else 'Derived poses are illustrative and unvalidated; they are not exact English presets or learner anatomy.'}</text>', '</svg>']
(FOLDER / ('rhotic-preview.svg' if rhotic else 'consonant-preview.svg' if consonants else 'preview.svg')).write_text('\n'.join(svg))
