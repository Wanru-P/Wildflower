"""Collect current-build runtime screenshots without changing the app or editing originals."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import html
import json
import shutil
import zipfile
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/wildflower/evidence/all-pages-phase6c'
BASE = ROOT / 'docs/wildflower/evidence/phase6c'
RAW = OUT / 'raw'
S, R, O = BASE / 'device/story', BASE / 'device/real-flow', BASE / 'device/online'
P1, P3, P4, P5 = [RAW / name for name in ['wildflower-phase1', 'wildflower-phase3', 'wildflower-phase4', 'wildflower-phase5b-test']]
G, P, A, C = [RAW / name for name in ['my-garden/test', 'personal-detail/test', 'observation-gallery/test', 'poetry/round1']]

CORE = [
    ('自然探索首页', S, '01-explore'), ('拍照识别 · CameraX', R, 'scan-camera'),
    ('识别结果 · 实际本机模型', R, 'identification-result'), ('识别结果 · 实际 Pl@ntNet', O, 'plantnet-camera-result'),
    ('公共物种详情', S, '03-species'), ('本次识别的物种详情', R, 'species-before-save'),
    ('我的花园', S, '07-garden'), ('我的花园 · 空收藏', S, '06-garden-empty'),
    ('我的植物档案', S, '08-personal-detail'), ('观察时间轴', S, '09-timeline'),
    ('观察照片集', S, '10-gallery'), ('单张照片详情', S, '11-photo-detail'),
    ('观察日期与备注编辑', S, '12-observation-editor'), ('四季手账', S, '13-seasons'),
    ('秋季详情', S, '14-autumn'), ('植物文化', S, '04-culture'),
    ('植物文化 · 阅读与来源', S, '05-culture-reading'), ('植物诗笺编辑器', S, '15-poetry-editor'),
    ('诗笺照片选择', S, '16-select-photo'), ('在线观察地图 · 合成坐标', O, 'google-map-selected-room-pin'),
    ('离线观察地图 · 无坐标', S, '21-map-no-location'), ('设置与帮助', S, '02-settings'),
    ('养护提醒', P1, 'care'), ('叶片健康 · 页面初始状态', P1, 'leaf-health'),
    ('科学资料阅读 · 测试缓存', P1, 'species-cached-science'),
]
STATES = [
    ('首次收藏确认', R, 'collect-confirmation'), ('选择已有同物种植株', R, 'existing-plant-choice'),
    ('拍摄后添加观察', R, 'observation-editor-camera'), ('编辑已有观察', R, 'observation-editor-edit'),
    ('相册导入取消', R, 'picker-cancel-no-observation'), ('更新封面后的照片集', R, 'cover-updated-gallery'),
    ('四季 · 春季', P3, 'seasons-spring'), ('四季 · 跨年冬季', P3, 'seasons-cross-year-winter'),
    ('四季 · 空状态', P5, 'seasons-empty'), ('花园 · 季节筛选', P3, 'garden-season-filter'),
    ('文化 · 无资料', P5, 'culture-empty'), ('照片集 · 空状态', A, 'gallery-empty-393'),
    ('个人档案 · 无照片', P, 'personal-empty-393'), ('图鉴模板预览', S, '17-template-botanical_atlas'),
    ('古典模板预览', S, '17-template-classical_poetry'), ('手账模板预览', S, '17-template-modern_journal'),
    ('诗笺编辑器 · 全部版式区域', C, 'editor-393-templates'),
    ('诗笺 · 无观察照片', P4, 'card-no-photo'), ('PNG 保存完成', P4, 'png-save-success'),
    ('系统保存位置选择器', P4, 'saf-file-picker'), ('系统分享 · 无测试接收器', S, '18-system-share'),
    ('断网 · 实际本机识别回退', O, 'plantnet-network-down-real-offline-fallback'),
    ('在线地图 · 无网络', O, 'google-map-no-network-feedback'), ('在线地图 · 断网重试', O, 'google-map-no-network-retry'),
    ('相机权限 · 首次申请', BASE / 'permissions', '01-first-request'),
    ('相机权限 · 拒绝', BASE / 'permissions', '02-denied'), ('相机权限 · 重新授权', BASE / 'permissions', '03-regranted'),
    ('强制重启 · 档案仍在', BASE, 'cold-start-plant'), ('强制重启 · 三张照片仍在', BASE, 'cold-start-gallery'),
]
RESPONSIVE = [
    ('320dp · 花园', G, 'garden-populated-320'), ('320dp · 花园滚动底部', G, 'garden-bottom-320'),
    ('320dp · 空花园', G, 'garden-empty-320'), ('320dp · 花园大字号', G, 'garden-large-font-320'),
    ('320dp · 个人档案', P, 'personal-detail-320'), ('320dp · 观察时间轴', P, 'personal-timeline-320'),
    ('320dp · 档案大字号', P, 'personal-large-font-320'), ('320dp · 档案照片丢失', P, 'personal-missing-320'),
    ('320dp · 观察照片集', A, 'gallery-320'), ('320dp · 照片丢失', A, 'gallery-missing-320'),
    ('320dp · 观察编辑长备注', P5, 'observation-editor-long-note-320'), ('320dp · 四季', P5, 'seasons-320'),
    ('320dp · 文化', P5, 'culture-320'), ('320dp · 识别相机', P5, 'scan-320'),
    ('320dp · 诗笺编辑器', C, 'editor-320-top'), ('320dp · 三模板选择', C, 'editor-320-templates'),
    ('平板 · 花园', P1, 'garden-populated-tablet'), ('平板 · 四季', P5, 'seasons-tablet'),
    ('平板 · 文化', P5, 'culture-tablet'),
]


def main():
    groups = [('01-pages', '主要页面与视图', CORE), ('02-states', '详情、弹窗及异常状态', STATES), ('03-responsive', '窄屏与平板适配', RESPONSIVE)]
    manifest, counts = [], {}
    cutoff = datetime(2026, 10, 14, 6, 28, tzinfo=timezone.utc).timestamp()
    for folder, title, items in groups:
        target = OUT / folder
        target.mkdir(exist_ok=True)
        counts[folder] = 0
        for index, (label, directory, name) in enumerate(items, 1):
            source = directory / f'{name}.png'
            if not source.exists() or source.stat().st_mtime < cutoff:
                raise ValueError(f'Missing/current-build screenshot required: {source}')
            file = target / f'{index:02d}-{name}.png'
            shutil.copy2(source, file)
            with Image.open(file) as im:
                im.verify()
            with Image.open(file) as im:
                pixels = list(im.size)
            manifest.append(dict(group=folder, group_title=title, title=label, file=file.relative_to(OUT).as_posix(),
                                 source=source.relative_to(ROOT).as_posix(), pixels=pixels,
                                 captured_at=datetime.fromtimestamp(source.stat().st_mtime, timezone.utc).isoformat(),
                                 sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
            counts[folder] += 1
    artwork = OUT / '04-artworks'
    artwork.mkdir(exist_ok=True)
    for name in ['14_botanical_atlas', '15_classical_poetry', '16_modern_journal']:
        source = ROOT / f'final-screenshots/{name}.png'
        target = artwork / source.name
        shutil.copy2(source, target)
        with Image.open(target) as im:
            assert im.size == (1600, 2400)
        manifest.append(dict(group='04-artworks', group_title='实际导出作品 · 非页面截图', title=name,
                             file=target.relative_to(OUT).as_posix(), source=source.relative_to(ROOT).as_posix(),
                             pixels=[1600, 2400], sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    note = ('当前 Phase6C 同一 Debug 构建在 API35 的真实运行截图，原图未经裁切、修饰或重绘；总览只是等比例缩略排版。'
            '植物摄影手记使用许可摄影和演示日期，不能当作校园实拍；模型识别来自模拟器虚拟场景，地图标记为受控合成坐标。'
            '边界测试档案的 Debug 标签保留。文化截图为 Debug 规则，Release 尚无批准文化条目；Release 未签名，不能称为 Release 已安装截图。'
            '首次欢迎引导未在本轮重置数据补拍；隐藏的内部审阅页不属于当前产品入口。没有混入旧版欢迎页或旧版 UI。')
    (OUT / 'README.md').write_text('# 草木知时 · 当前页面截图合集\n\n' + note + '\n\n'
        + '\n'.join(f'- {title}：{counts[folder]} 张' for folder, title, _ in groups)
        + '\n- 实际导出作品：3 张，均为 1600×2400。\n\n打开 index.html 浏览，点击原图放大；manifest.json 包含来源、时间和 SHA256。\n', encoding='utf-8')
    (OUT / 'manifest.json').write_text(json.dumps(dict(apk_sha256=hashlib.sha256((ROOT / 'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest(), note=note, entries=manifest), ensure_ascii=False, indent=2), encoding='utf-8')
    sections = ''
    for group in dict.fromkeys(e['group'] for e in manifest):
        entries = [e for e in manifest if e['group'] == group]
        sections += f'<h2>{entries[0]["group_title"]} · {len(entries)} 张</h2><div class="grid">'
        sections += ''.join(f'<figure><a href="{e["file"]}"><img loading="lazy" src="{e["file"]}" alt="{html.escape(e["title"])}"></a><figcaption>{html.escape(e["title"])}<small>{e["pixels"][0]} × {e["pixels"][1]}</small></figcaption></figure>' for e in entries) + '</div>'
    (OUT / 'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>草木知时 · 当前全部页面</title><style>body{font:16px system-ui;background:#f8f8f1;color:#24452e;margin:28px}p{line-height:1.8;max-width:1100px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:20px}figure{margin:0;background:#fffdf9;padding:12px;border:1px solid #dce6d6;border-radius:12px}img{width:100%;height:475px;object-fit:contain}figcaption{line-height:1.8}small{display:block;color:#777}h2{margin-top:40px}</style><h1>草木知时 · 当前页面截图</h1><p>' + note + '</p><p><a href="../Wildflower_All_Pages_Phase6C.zip">下载完整截图包</a> · <a href="manifest.json">来源清单</a></p>' + sections, encoding='utf-8')
    core = [e for e in manifest if e['group'] == '01-pages']
    width, height = 275, 630
    sheet = Image.new('RGB', (width * 5 + 40, height * 5 + 100), '#f8f8f1')
    draw = ImageDraw.Draw(sheet)
    font = 'C:/Windows/Fonts/msyh.ttc'
    draw.text((20, 15), '草木知时 · 25 个主要页面与视图', font=ImageFont.truetype(font, 28), fill='#24452e')
    draw.text((20, 60), '本轮真实 Debug 运行截屏 · 点击浏览页可查看原图及更多状态', font=ImageFont.truetype(font, 17), fill='#687660')
    for index, entry in enumerate(core):
        x, y = 20 + index % 5 * width, 105 + index // 5 * height
        with Image.open(OUT / entry['file']) as im:
            thumbnail = ImageOps.contain(im.convert('RGB'), (250, 565), Image.Resampling.LANCZOS)
            sheet.paste(thumbnail, (x + (250 - thumbnail.width) // 2, y))
        draw.text((x, y + 575), entry['title'], font=ImageFont.truetype(font, 14), fill='#24452e')
    sheet.save(OUT / 'overview.png')
    archive = OUT.parent / 'Wildflower_All_Pages_Phase6C.zip'
    if archive.exists():
        raise FileExistsError('Preserve existing delivery ZIP: ' + str(archive))
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as zipped:
        for file in [*(OUT / entry['file'] for entry in manifest), *(OUT / name for name in ['README.md', 'manifest.json', 'index.html', 'overview.png'])]:
            zipped.write(file, file.relative_to(OUT).as_posix())
    # Verify every original in the download, not merely the ZIP directory listing.
    with zipfile.ZipFile(archive) as zipped:
        for entry in manifest:
            assert hashlib.sha256(zipped.read(entry['file'])).hexdigest() == entry['sha256']
    print(json.dumps(dict(screenshots=sum(counts.values()), artworks=3, groups=counts, zip_bytes=archive.stat().st_size, index=str(OUT / 'index.html')), ensure_ascii=False))


if __name__ == '__main__':
    main()
