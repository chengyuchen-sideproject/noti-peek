"""Generate Google Play graphics for NotiPeek, matching the in-app vector icon
(chat bubble + eye, #1E88E5 on #E3F2FD). Uses Pillow only. Supersampled for
smooth edges."""
import math
from PIL import Image, ImageDraw, ImageFont

BG = (227, 242, 253)      # #E3F2FD soft blue
BLUE = (30, 136, 229)     # #1E88E5
WHITE = (255, 255, 255)
BLUE_DARK = (13, 71, 161)  # #0D47A1

def circle_through(p1, p2, p3):
    ax, ay = p1; bx, by = p2; cx, cy = p3
    d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
    ux = ((ax**2 + ay**2) * (by - cy) + (bx**2 + by**2) * (cy - ay) + (cx**2 + cy**2) * (ay - by)) / d
    uy = ((ax**2 + ay**2) * (cx - bx) + (bx**2 + by**2) * (ax - cx) + (cx**2 + cy**2) * (bx - ax)) / d
    r = math.hypot(ux - ax, uy - ay)
    return ux, uy, r

def almond_polygon(s):
    """White eye almond in 108-unit space, scaled by s. Two circular arcs
    through (34,48)-(54,36)-(74,48) [top] and (34,48)-(54,60)-(74,48) [bottom]."""
    uxt, uyt, rt = circle_through((34, 48), (54, 36), (74, 48))
    uxb, uyb, rb = circle_through((34, 48), (54, 60), (74, 48))
    pts = []
    N = 60
    for i in range(N + 1):  # top arc, x 34 -> 74, upper branch (smaller y)
        x = 34 + (74 - 34) * i / N
        y = uyt - math.sqrt(max(0.0, rt * rt - (x - uxt) ** 2))
        pts.append((x * s, y * s))
    for i in range(N + 1):  # bottom arc, x 74 -> 34, lower branch
        x = 74 - (74 - 34) * i / N
        y = uyb + math.sqrt(max(0.0, rb * rb - (x - uxb) ** 2))
        pts.append((x * s, y * s))
    return pts

def draw_mark(draw, s, ox=0.0, oy=0.0):
    """Draw the bubble+eye mark in 108-unit space scaled by s, offset (ox,oy)."""
    def S(x, y):
        return (x * s + ox, y * s + oy)
    # Bubble body: rounded rect x:18..90 y:26..72, radius 14.
    draw.rounded_rectangle([S(18, 26), S(90, 72)], radius=14 * s, fill=BLUE)
    # Tail.
    draw.polygon([S(34, 70), S(30, 86), S(52, 70)], fill=BLUE)
    # Eye white almond.
    draw.polygon([(x + ox, y + oy) for (x, y) in almond_polygon(s)], fill=WHITE)
    # Pupil: circle centre (54,48) radius 8.
    cx, cy = S(54, 48)
    draw.ellipse([cx - 8 * s, cy - 8 * s, cx + 8 * s, cy + 8 * s], fill=BLUE)

def load_font(paths, size):
    for p in paths:
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()

# ---------- 512x512 app icon ----------
SS = 4
size = 512
img = Image.new("RGB", (size * SS, size * SS), BG)
d = ImageDraw.Draw(img)
draw_mark(d, s=(size * SS) / 108.0)
img = img.resize((size, size), Image.LANCZOS)
img.save("ic_play_512.png")
print("wrote ic_play_512.png", img.size)

# ---------- 1024x500 feature graphic ----------
W, H = 1024, 500
fimg = Image.new("RGB", (W * SS, H * SS), BLUE)
fd = ImageDraw.Draw(fimg)
# Diagonal gradient BLUE -> BLUE_DARK.
for y in range(H * SS):
    t = y / (H * SS)
    for band in [1]:  # simple vertical gradient (fast enough per-line)
        pass
    col = tuple(int(BLUE[i] + (BLUE_DARK[i] - BLUE[i]) * t) for i in range(3))
    fd.line([(0, y), (W * SS, y)], fill=col)
# White rounded-square icon tile on the left, with the standard mark inside.
tile = 300 * SS
tx, ty = 80 * SS, (H * SS - tile) // 2
fd.rounded_rectangle([tx, ty, tx + tile, ty + tile], radius=64 * SS, fill=WHITE)
# Mark inside tile: 108-unit space scaled to fit tile with padding.
pad = 34 * SS
inner = tile - 2 * pad
# Re-tint: inside the white tile use the normal blue bubble + white eye, but the
# tile is already white so the eye-white blends; give the bubble a blue body and
# keep eye white with blue pupil (readable on the blue bubble).
draw_mark(fd, s=inner / 108.0, ox=tx + pad, oy=ty + pad)
# Text on the right.
title_font = load_font([
    "C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/Arialbd.ttf",
], 104 * SS)
sub_font = load_font([
    "C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/Arial.ttf",
], 33 * SS)
text_x = 452 * SS
title = "NotiPeek"
sub = "On-device notification history"
# Vertically centre the title+tagline block.
tb = fd.textbbox((0, 0), title, font=title_font)
sb = fd.textbbox((0, 0), sub, font=sub_font)
title_h = tb[3] - tb[1]
gap = 20 * SS
sub_h = sb[3] - sb[1]
block_h = title_h + gap + sub_h
top = (H * SS - block_h) / 2
fd.text((text_x, top - tb[1]), title, font=title_font, fill=WHITE)
fd.text((text_x, top + title_h + gap - sb[1]), sub, font=sub_font, fill=(223, 237, 252))
print("title width px:", (fd.textlength(title, font=title_font)) / SS,
      "sub width px:", (fd.textlength(sub, font=sub_font)) / SS,
      "-> right edge:", (text_x + max(fd.textlength(title, font=title_font), fd.textlength(sub, font=sub_font))) / SS)
fimg = fimg.resize((W, H), Image.LANCZOS)
fimg.save("feature_1024x500.png")
print("wrote feature_1024x500.png", fimg.size)
