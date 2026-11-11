# Genera el logo de Lectura Xoriguer: el falcó del logo original + un llibre obert
# amb un punt de llibre amb la senyera (mateix estil que Paraules i Dòmino).
# Ús: python -I empaquetar\logo.py
#   -> empaquetar/logo/logo-1200.png (amb text), empaquetar/logo/logo-sq.png (sense text, per a icones)
#      i les icones de cada plataforma (Android, Windows, iOS) dins de composeApp/.
import os, collections
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'empaquetar', 'logo', 'xoriguer-original.webp')
ORANGE = (247, 147, 30); CREAM = (250, 244, 232); BLACK = (8, 8, 8)
IVORY = (255, 250, 238); IVORY2 = (226, 210, 178); EDGE = (196, 172, 134); INK = (120, 98, 74)
COVER = (122, 58, 14); COVER2 = (70, 32, 8)
YELLOW = (252, 204, 0); RED = (218, 18, 26)


def remove_sprocket(im):
    # El logo original té un plat de bicicleta damunt del falcó: el pintem de negre (igual que al Dòmino).
    W, H = im.size; px = im.load()
    white = lambda p: min(p) > 150 and max(p) - min(p) < 60
    start = next(((x, y) for y in range(H) for x in range(W) if white(px[x, y])), None)
    if not start: return im
    seen = {start}; q = collections.deque([start])
    while q:
        x, y = q.popleft()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (x + dx, y + dy)
                if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and white(px[n]): seen.add(n); q.append(n)
    if max(p[1] for p in seen) > H * 0.6: return im
    for x, y in seen:
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                n = (x + dx, y + dy)
                if 0 <= n[0] < W and 0 <= n[1] < H and max(px[n]) - min(px[n]) < 70: px[n] = (0, 0, 0)
    x0, x1 = min(p[0] for p in seen) - 40, max(p[0] for p in seen) + 10
    y0, y1 = min(p[1] for p in seen) - 10, max(p[1] for p in seen) + 10
    for x in range(max(0, x0), min(W, x1)):
        for y in range(max(0, y0), min(H, y1)):
            p = px[x, y]
            if max(p) > 25 and max(p) - min(p) < 70: px[x, y] = (0, 0, 0)
    return im


def bird():
    im = remove_sprocket(Image.open(SRC).convert('RGB').crop((130, 200, 1080, 516)))
    a = im.convert('L').point(lambda v: 0 if v < 18 else min(255, (v - 18) * 6))
    im = im.convert('RGBA'); im.putalpha(a)
    return im


def bez(p0, p1, p2, n=40):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / n for i in range(n + 1))]


def page(side, cx, top, bot, half, dip, lift):
    """Polígon d'una pàgina (side=-1 esquerra, +1 dreta). La vora de dalt fa una corba que puja cap a fora."""
    ox = cx + side * half
    up = bez((cx, top + dip), (cx + side * half * .45, top - lift * .2), (ox, top))
    dn = bez((ox, bot), (cx + side * half * .45, bot - lift * .6), (cx, bot + dip))
    return up + dn


def book(w):
    """Llibre obert d'amplada w (dibuixat a 4x i reduït)."""
    S = 4; W = w * S; H = int(W * .78)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    cx = W / 2; half = W * .47; top = H * .10; bot = H * .78; dip = H * .07; lift = H * .06
    # punt de llibre amb la senyera: es dibuixa primer perquè quedi darrere del llibre i només en surti la punta
    rw = W * .085; rx = cx + W * .03; ry0 = top + dip; ry1 = H * .95
    rmask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(rmask).polygon([(rx, ry0), (rx + rw, ry0), (rx + rw, ry1), (rx + rw / 2, ry1 - rw * .55), (rx, ry1)], fill=255)
    rib = Image.new('RGBA', (W, H), (0, 0, 0, 0)); rd = ImageDraw.Draw(rib)
    for k in range(9):
        a = rx + rw * k / 9; b = rx + rw * (k + 1) / 9
        rd.rectangle((a, ry0, b, ry1), fill=(YELLOW if k % 2 == 0 else RED) + (255,))
    im.paste(rib, (0, 0), rmask)
    # tapa (una mica més gran que les pàgines)
    for s in (-1, 1):
        d.polygon(page(s, cx, top + H * .07, bot + H * .07, half + W * .025, dip, lift), fill=COVER2 + (255,))
        d.polygon(page(s, cx, top + H * .05, bot + H * .05, half + W * .02, dip, lift), fill=COVER + (255,))
    # gruix de fulls (capes cada cop més clares)
    for k, off in enumerate((.040, .030, .020, .010)):
        col = tuple(int(EDGE[i] + (IVORY2[i] - EDGE[i]) * k / 3) for i in range(3))
        for s in (-1, 1):
            d.polygon(page(s, cx, top + H * off, bot + H * off, half - W * .004 * (4 - k), dip, lift), fill=col + (255,))
    # pàgines de dalt amb un degradat cap al llom
    for s in (-1, 1):
        mask = Image.new('L', (W, H), 0)
        ImageDraw.Draw(mask).polygon(page(s, cx, top, bot, half, dip, lift), fill=255)
        grad = Image.new('RGBA', (W, H)); gd = ImageDraw.Draw(grad)
        for x in range(W):
            k = min(1, abs(x - cx) / half)
            t = (1 - k) ** 3 * .75
            gd.line([(x, 0), (x, H)], fill=tuple(int(IVORY[i] * (1 - t) + IVORY2[i] * t) for i in range(3)) + (255,))
        im.paste(grad, (0, 0), mask)
    # línies de text que segueixen la corba
    for s in (-1, 1):
        for j in range(7):
            f = .16 + j * .095
            y = top + (bot - top) * f
            x0, x1 = (.12, .86) if j != 6 else ((.43, .86) if s < 0 else (.12, .55))
            pts = []
            for i in range(31):
                u = x0 + (x1 - x0) * i / 30
                yy = y + dip * (1 - u) ** 2 - lift * .15 * u
                pts.append((cx + s * half * u, yy))
            d.line(pts, fill=INK + (150,), width=int(S * 2.6))
    # ombra del llom
    sh = Image.new('L', (W, H), 0)
    ImageDraw.Draw(sh).line([(cx, top + dip), (cx, bot + dip)], fill=120, width=int(W * .03))
    im.paste((90, 60, 30, 255), (0, 0), sh.filter(ImageFilter.GaussianBlur(W * .012)))
    return im.resize((w, round(H / S)), Image.LANCZOS)


def place_book(canvas, cx, cy, w):
    b = book(w); pad = int(w * .15)
    big = Image.new('RGBA', (b.size[0] + pad * 2, b.size[1] + pad * 2), (0, 0, 0, 0))
    sh = Image.new('RGBA', big.size, (0, 0, 0, 0)); sh.paste((0, 0, 0, 190), (pad, pad + int(w * .04)), b.split()[3])
    big.alpha_composite(sh.filter(ImageFilter.GaussianBlur(w * .04)))
    big.alpha_composite(b, (pad, pad))
    canvas.alpha_composite(big, (int(cx - big.size[0] / 2), int(cy - big.size[1] / 2)))


def text(d, xy, s, font, fill, spacing=0):
    if not spacing:
        d.text(xy, s, font=font, fill=fill, anchor='mm'); return
    widths = [d.textlength(ch, font=font) for ch in s]
    total = sum(widths) + spacing * (len(s) - 1); x = xy[0] - total / 2
    for ch, w in zip(s, widths):
        d.text((x + w / 2, xy[1]), ch, font=font, fill=fill, anchor='mm'); x += w + spacing


def compose(with_text=True, size=1200, margin=0.0):
    """margin: espai buit als costats (per a icones adaptatives d'Android)."""
    cv = Image.new('RGBA', (size, size), BLACK + (255,))
    inner = int(size * (1 - 2 * margin)); off = (size - inner) // 2
    lay = Image.new('RGBA', (inner, inner), (0, 0, 0, 0))
    g = Image.new('RGBA', (inner, inner), (0, 0, 0, 0)); gd = ImageDraw.Draw(g)
    gd.ellipse((inner * .18, inner * .32, inner * .82, inner * .82), fill=ORANGE + (70,)); g = g.filter(ImageFilter.GaussianBlur(inner * .075))
    lay.alpha_composite(g)
    if with_text:
        place_book(lay, inner * .5, inner * .545, int(inner * .52))
    else:
        place_book(lay, inner * .5, inner * .64, int(inner * .80))
    b = bird(); bw = int(inner * (0.86 if with_text else 0.98)); b = b.resize((bw, round(b.size[1] * bw / b.size[0])), Image.LANCZOS)
    lay.alpha_composite(b, (int(inner / 2 - bw / 2 - inner * 0.02), int(inner * (0.04 if with_text else 0.05))))
    if with_text:
        d = ImageDraw.Draw(lay)
        f1 = ImageFont.truetype('C:/Windows/Fonts/seguibli.ttf', int(inner * 0.135))
        f2 = ImageFont.truetype('C:/Windows/Fonts/bahnschrift.ttf', int(inner * 0.048))
        f2.set_variation_by_name('Bold')
        text(d, (inner / 2, inner * 0.815), 'LECTURA', f1, CREAM)
        text(d, (inner / 2, inner * 0.925), 'XORIGUER', f2, ORANGE, spacing=int(inner * 0.028))
    cv.alpha_composite(lay, (off, off))
    return cv


def save(img, *parts, rgb=False):
    p = os.path.join(ROOT, *parts); os.makedirs(os.path.dirname(p), exist_ok=True)
    (img.convert('RGB') if rgb else img).save(p, optimize=True)


if __name__ == '__main__':
    big = compose(True)
    save(big, 'empaquetar', 'logo', 'logo-1200.png', rgb=True)
    sq = compose(False, 1024)
    save(sq, 'empaquetar', 'logo', 'logo-sq.png', rgb=True)
    app = os.path.join(ROOT, 'composeApp')
    if os.path.isdir(app):
        # logo per a la pantalla d'inici / "Quant a" (recurs comú)
        save(big.resize((600, 600), Image.LANCZOS), 'composeApp', 'src', 'commonMain', 'composeResources', 'drawable', 'logo.png', rgb=True)
        # Android: icones clàssiques + primer pla de la icona adaptativa (amb marge de seguretat)
        dens = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
        fg = compose(False, 1024, margin=0.17)
        for k, n in dens.items():
            save(sq.resize((n, n), Image.LANCZOS), 'composeApp', 'src', 'androidMain', 'res', f'mipmap-{k}', 'ic_launcher.png', rgb=True)
            m = int(n * 108 / 48)
            save(fg.resize((m, m), Image.LANCZOS), 'composeApp', 'src', 'androidMain', 'res', f'mipmap-{k}', 'ic_launcher_foreground.png', rgb=True)
        # Windows: .ico i .png per a la finestra
        icop = os.path.join(app, 'icons', 'lectura.ico'); os.makedirs(os.path.dirname(icop), exist_ok=True)
        sq.convert('RGB').save(icop, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
        save(sq.resize((512, 512), Image.LANCZOS), 'composeApp', 'icons', 'lectura.png', rgb=True)
        save(sq.resize((256, 256), Image.LANCZOS), 'composeApp', 'src', 'commonMain', 'composeResources', 'drawable', 'icona.png', rgb=True)
        # iOS: icona única de 1024 (Xcode genera la resta)
        save(sq, 'iosApp', 'iosApp', 'Assets.xcassets', 'AppIcon.appiconset', 'app-icon-1024.png', rgb=True)
    print('ok')
