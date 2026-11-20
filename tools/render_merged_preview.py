#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
render_merged_preview.py —— 按合并后真实布局结构（toolbar + 卡片 + 底部导航）渲染预览
颜色直接取自合并后的 colors.xml，不做任何主观取色。
"""
import os
from PIL import Image, ImageDraw, ImageFont

SCALE = 3
W, H = 420, 900

LIGHT = dict(
    window=(243, 244, 246), card=(255, 255, 255), outline=(225, 229, 236),
    primary=(37, 99, 235), text1=(22, 24, 29), text2=(91, 100, 114),
    text3=(139, 148, 163), success=(14, 159, 110), warning=(199, 122, 5),
    danger=(217, 59, 59), white=(255, 255, 255))

DARK = dict(
    window=(15, 17, 21), card=(23, 26, 33), outline=(42, 48, 58),
    primary=(91, 140, 255), text1=(236, 240, 246), text2=(154, 164, 178),
    text3=(107, 116, 130), success=(52, 211, 153), warning=(251, 191, 36),
    danger=(255, 107, 107), white=(255, 255, 255))


def font(size, bold=False):
    paths = [
        '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold
        else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
    ]
    for p in paths:
        if os.path.isfile(p):
            return ImageFont.truetype(p, size * SCALE // 2)
    return ImageFont.load_default()


def rrect(d, box, radius, fill=None, outline=None, width=1):
    box = tuple(v * SCALE for v in box)
    d.rounded_rectangle(box, radius=radius * SCALE, fill=fill, outline=outline,
                        width=width * SCALE)


def render(pal, out_path, dark=False):
    img = Image.new('RGB', (W * SCALE, H * SCALE), pal['window'])
    d = ImageDraw.Draw(img)

    # ---- Toolbar（toolbar_rounded：卡片底 + 3dp 蓝色底边） ----
    d.rectangle([0, 0, W * SCALE, 56 * SCALE], fill=pal['card'])
    d.rectangle([0, 53 * SCALE, W * SCALE, 56 * SCALE], fill=pal['primary'])
    d.text((16 * SCALE, 18 * SCALE), "Limbo ANIPLUS",
           fill=pal['text1'], font=font(16, True))
    d.text(((W - 30) * SCALE, 20 * SCALE), "···", fill=pal['text2'], font=font(14))

    # ---- 分组标题 ----
    d.text((16 * SCALE, 76 * SCALE), "虚拟机 (3)", fill=pal['text2'], font=font(13, True))

    # ---- 三张机器卡片（border：16dp 圆角 + 1.5dp 中性描边） ----
    machines = [
        ("Windows XP", "x86_64 · 512 MB · 2 核", "running", pal['success']),
        ("Debian Linux", "x86_64 · 1024 MB · 4 核", "paused", pal['warning']),
        ("FreeDOS", "x86 · 128 MB · 1 核", "stopped", pal['text3']),
    ]
    y = 104
    for name, sub, state, dot in machines:
        rrect(d, (16, y, W - 16, y + 84), 16, fill=pal['card'],
              outline=pal['outline'], width=2)
        # 启动图标块
        rrect(d, (28, y + 22, 68, y + 62), 10, fill=pal['primary'])
        d.text((38 * SCALE, (y + 33) * SCALE), "VM", fill=pal['white'], font=font(13, True))
        d.text((80 * SCALE, (y + 20) * SCALE), name, fill=pal['text1'], font=font(15, True))
        d.text((80 * SCALE, (y + 46) * SCALE), sub, fill=pal['text2'], font=font(11))
        # 状态点
        d.ellipse([(W - 66) * SCALE, (y + 24) * SCALE,
                   (W - 58) * SCALE, (y + 32) * SCALE], fill=dot)
        d.text(((W - 44) * SCALE, (y + 44) * SCALE), "›", fill=pal['text3'], font=font(18))
        y += 96

    # ---- 新建 FAB（continue_button：12dp 圆角，主色填充） ----
    rrect(d, (W - 116, H - 150, W - 24, H - 104), 12, fill=pal['primary'])
    d.text(((W - 92) * SCALE, (H - 140) * SCALE), "+ 新建",
           fill=pal['white'], font=font(14, True))

    # ---- 底部导航（bottom_nav_shadow：顶部阴影 + 卡片底） ----
    d.rectangle([0, (H - 64) * SCALE, W * SCALE, H * SCALE], fill=pal['card'])
    for i, (label, active) in enumerate([
        ("虚拟机", True), ("线上", False), ("设置", False), ("关于", False)]):
        x = 16 + i * 100
        col = pal['primary'] if active else pal['text2']
        d.text((x * SCALE, (H - 40) * SCALE), "■", fill=col, font=font(12))
        d.text((x * SCALE, (H - 20) * SCALE), label, fill=col, font=font(11, active))

    img = img.resize((W * SCALE // 2, H * SCALE // 2), Image.LANCZOS)
    img.save(out_path)
    print('saved', out_path)


if __name__ == '__main__':
    out = os.path.join(os.path.dirname(__file__), '..', 'docs')
    os.makedirs(out, exist_ok=True)
    render(LIGHT, os.path.join(out, 'merged_preview_light.png'), False)
    render(DARK, os.path.join(out, 'merged_preview_dark.png'), True)
