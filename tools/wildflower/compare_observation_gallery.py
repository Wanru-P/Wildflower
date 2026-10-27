"""Labelled target/real evidence boards; originals are never edited."""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[2]
evidence = root / 'docs/wildflower/evidence/observation-gallery'
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 16)
for viewport in ['393', '320']:
    items = [
        ('目标图 · 设计参考', root / 'docs/wildflower/observation-gallery-kit/references/target_observation_gallery.png'),
        ('第一轮 · 真实 API35', evidence / f'round1/gallery-{viewport}.png'),
        ('第二轮 · 真实 API35', evidence / f'round2/gallery-{viewport}.png'),
        ('最终 · 真实 API35', evidence / f'final/gallery-{viewport}.png'),
    ]
    board = Image.new('RGB', (1480, 940), '#FBF9F2')
    draw = ImageDraw.Draw(board)
    draw.text((18, 12), f'Observation Gallery · {viewport}dp 对照 · 仅等比缩放，不裁切原截图', fill='#214D33', font=font)
    for index, (label, path) in enumerate(items):
        x = 18 + index * 366
        draw.text((x, 54), label, fill='#214D33', font=font)
        with Image.open(path) as image:
            thumb = ImageOps.contain(image.convert('RGB'), (346, 800), Image.Resampling.LANCZOS)
            board.paste(thumb, (x + (346 - thumb.width) // 2, 90))
    draw.text((18, 910), '摄影为已授权参考测试档案，非用户野外实拍；目标照片不用于填充正式观察。', fill='#8D7B6B', font=small)
    board.save(evidence / f'comparison-{viewport}.png')
