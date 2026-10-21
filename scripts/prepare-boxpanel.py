#!/usr/bin/env python3
"""Generate branding assets; input is data, never shell/Gradle source."""
import base64, io, json, pathlib, re, shutil, sys
from PIL import Image, ImageDraw

def prepare(source, root):
    value = json.loads(pathlib.Path(source).read_text())
    app_id = value.get('appId', '')
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', app_id):
        raise ValueError('Invalid app identity')
    if value.get('packageName') != 'com.snaptvnow.box.a' + app_id.replace('-', ''):
        raise ValueError('Invalid package')
    code = value.get('versionCode')
    if type(code) is not int or not 1 <= code <= 2100000000 or value.get('versionName') != f'1.0.{code}':
        raise ValueError('Invalid version')
    if value.get('design') not in ('classic', 'cinema', 'compact', 'aura', 'panorama', 'stadium', 'noir', 'horizon'):
        raise ValueError('Invalid design')
    if not 2 <= len(value.get('name', '').strip()) <= 40 or any(ord(c) < 32 for c in value['name']):
        raise ValueError('Invalid label')
    for key in ('accent', 'background'):
        if not re.fullmatch(r'#[a-fA-F0-9]{6}', value.get(key, '')):
            raise ValueError('Invalid colors')
    style = value.get('style')
    if style is not None and (not isinstance(style, dict) or style.get('wallpaper') not in ('aurora','midnight','sunset','ocean') or style.get('density') not in ('comfortable','compact') or not isinstance(style.get('welcome'), str) or len(style['welcome']) > 80 or any(ord(c) < 32 for c in style['welcome'])):
        raise ValueError('Invalid app style')
    folder = pathlib.Path(root) / 'app/build/generated/boxpanel'
    if folder.exists(): shutil.rmtree(folder)
    (folder / 'assets').mkdir(parents=True)
    (folder / 'res/drawable').mkdir(parents=True)
    (folder / 'assets/boxpanel.json').write_text(json.dumps(value, ensure_ascii=False))
    logo = value.get('logo', '')
    if logo:
        if len(logo) > 350000 or not re.fullmatch(r'data:image/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+', logo):
            raise ValueError('Invalid logo')
        image = Image.open(io.BytesIO(base64.b64decode(logo.split(',', 1)[1], validate=True)))
        if image.width * image.height > 16000000: raise ValueError('Logo dimensions too large')
        image = image.convert('RGBA')
    else:
        image = Image.new('RGBA', (256, 256), value['accent'])
        ImageDraw.Draw(image).polygon([(90, 58), (90, 198), (190, 128)], fill=value['background'])
    for name, size in [('snaptvnow_icon', (512, 512)), ('banner', (320, 180))]:
        canvas = Image.new('RGBA', size, value['background'])
        fitted = image.copy(); fitted.thumbnail((int(size[0]*.8), int(size[1]*.8)))
        canvas.alpha_composite(fitted, ((size[0]-fitted.width)//2, (size[1]-fitted.height)//2))
        canvas.save(folder / f'res/drawable/{name}.png')

if __name__ == '__main__': prepare(sys.argv[1], sys.argv[2] if len(sys.argv)>2 else '.')
