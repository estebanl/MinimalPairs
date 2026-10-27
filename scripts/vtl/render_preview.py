"""Render the exported meshes as a reviewable SVG contact sheet."""
from pathlib import Path
import math
import struct

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

svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1800" viewBox="0 0 1200 1800">',
       '<rect width="1200" height="1800" fill="#f5f3ed"/>',
       '<text x="30" y="38" font-size="25" font-family="sans-serif" fill="#252b2a">VocalTractLab • exported JD3 anatomy</text>',
       '<text x="30" y="66" font-size="15" font-family="sans-serif" fill="#59615d">Six original JD3 presets + four derived teaching poses • side cutaway • GPL-3.0-or-later</text>']
for index, (shape, label) in enumerate([('fleece','/iː/ — source i'), ('kit','/ɪ/ — source I'), ('dress','/e/ (DRESS) — source E'),
                                        ('foot','/ʊ/ — source U'), ('goose','/uː/ — source u'), ('thought','/ɔː/ — source O'), ('trap','/æ/ — derived a + E'), ('palm','/ɑː/ — derived retracted a'), ('strut','/ʌ/ — derived a + @'), ('lot','/ɒ/ — derived a + O')]):
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
svg += ['<text x="30" y="1780" font-size="14" font-family="sans-serif" fill="#59615d">Derived poses are illustrative and unvalidated; they are not exact English presets or learner anatomy.</text>', '</svg>']
(FOLDER / 'preview.svg').write_text('\n'.join(svg))
