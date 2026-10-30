"""Import openGym TEXT only. Usage: python3 scripts/import_opengym.py SOURCE_CHECKOUT"""
import json, re, subprocess, sys
from pathlib import Path
source = Path(sys.argv[1])
target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/opengym'
target.mkdir(parents=True, exist_ok=True)
translations = json.loads((source/'catalogue/i18n/zh.json').read_text())
exercises = []
for path in sorted((source/'catalogue/exercises').glob('*.json')):
    e = json.loads(path.read_text())
    if 'name' not in e:
        parent = json.loads((source/'catalogue/exercises'/f"{e['variantOf']}.json").read_text())
        e = dict(parent, **e)
    zh = translations.get(e['id'], {})
    exercises.append(dict(id=e['id'], name=zh.get('name') or e['name'], englishName=e['name'],
        bodyPart=e['bodyPart'], equipment=e['equipment'], target=e['target'],
        description=zh.get('description') or e.get('description',''),
        instructions=zh.get('instructions') or e.get('instructions',[]), hold=e.get('hold',False)))
(target/'exercises.json').write_text(json.dumps(exercises,ensure_ascii=False,separators=(',',':')))
text=(source/'frontend/src/lib/starter.js').read_text()
names={'push':'推日','pull':'拉日','legs':'腿日','upper-a':'上肢 A','upper-b':'上肢 B','lower-a':'下肢 A','lower-b':'下肢 B','fb-a':'全身 A','fb-b':'全身 B','fb-c':'全身 C','5x5-a':'5×5 A','5x5-b':'5×5 B','5x5-c':'5×5 C'}
seeds=[]
for key, entries in re.findall(r"\['([^']+)', '[^']+', '[^']+', (\[\[.*?\]\])\]",text):
    if key not in names: continue
    rows=json.loads(entries.replace("'",'"'))
    seeds.append(dict(name=names[key], exercises=[dict(exerciseId=i,slotId=f'{key}-{n}',sets=s,reps=r) for n,(i,s,r) in enumerate(rows)]))
assert len(seeds)==13
(target/'starters.json').write_text(json.dumps(seeds,ensure_ascii=False))
for name in ['LICENSE','NOTICE.md']:(target/name).write_bytes((source/name).read_bytes())
rev=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()
(target/'SOURCE.txt').write_text(f'openGym — Copyright (C) 2026 Duarte Santos\nhttps://github.com/DuarteSantos8/openGym\nCommit: {rev}\nAGPL-3.0: catalogue text/translations and starter routine data.\nAdapted to native Kotlin/Compose; see scripts/import_opengym.py.\nNo Gym visual images, animations or media URLs are included.\nCorresponding source for distributed derivatives must be made available under applicable AGPL terms.\n')
print(f'Imported {len(exercises)} exercises, {len(seeds)} starter routines, commit {rev}')
