"""Rebuild the offline food subset from USDA's SR Legacy April 2018 CSV archive.

Usage: python3 scripts/import_food_composition.py /path/to/archive.zip
Source: https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
USDA FoodData Central data are public domain (CC0). All values are per 100 g edible portion.
"""
import csv
import hashlib
import io
import json
from pathlib import Path
import sys
from zipfile import ZipFile

# FDC ID, display name, preparation state, category. Raw/cooked records are distinct foods.
SELECTION = [
    (169757, "米饭", "熟重 · 长粒白米饭，无盐", "主食"),
    (169756, "大米", "生重 · 长粒白米", "主食"),
    (173904, "燕麦片", "干重 · 原味，未强化", "主食"),
    (168482, "红薯", "生重 · 未烹调", "主食"),
    (168484, "红薯", "熟重 · 去皮水煮", "主食"),
    (170026, "土豆", "生重 · 含皮", "主食"),
    (170440, "土豆", "熟重 · 去皮水煮，无盐", "主食"),
    (169999, "甜玉米", "熟重 · 水煮沥干，无盐", "主食"),
    (172688, "全麦面包", "成品重 · 通用配方", "主食"),
    (171077, "鸡胸肉", "生重 · 去皮去骨", "蛋白来源"),
    (171477, "鸡胸肉", "熟重 · 仅肉，烤制", "蛋白来源"),
    (171287, "鸡蛋", "生重 · 去壳全蛋", "蛋白来源"),
    (173424, "鸡蛋", "熟重 · 水煮全蛋，去壳", "蛋白来源"),
    (172476, "普通豆腐", "未烹调 · 硫酸钙凝固", "蛋白来源"),
    (172475, "硬豆腐", "未烹调 · 硫酸钙凝固", "蛋白来源"),
    (175167, "三文鱼", "生重 · 养殖大西洋鲑", "蛋白来源"),
    (175168, "三文鱼", "熟重 · 养殖大西洋鲑，干热烹调", "蛋白来源"),
    (172217, "全脂牛奶", "液体称重 · 乳脂 3.25%", "乳品"),
    (171284, "原味全脂酸奶", "成品重 · 通用配方", "乳品"),
    (170886, "原味低脂酸奶", "成品重 · 通用配方", "乳品"),
    (173944, "香蕉", "生重 · 去皮可食部", "蔬果"),
    (171688, "苹果", "生重 · 含皮可食部", "蔬果"),
    (170379, "西兰花", "生重 · 可食部", "蔬果"),
    (169967, "西兰花", "熟重 · 水煮沥干，无盐", "蔬果"),
    (168462, "菠菜", "生重 · 可食部", "蔬果"),
    (170567, "扁桃仁（巴旦木）", "可食部重", "油脂坚果"),
    (171413, "橄榄油", "称重 · 烹调用油", "油脂坚果"),
    (171411, "大豆油", "称重 · 烹调用油", "油脂坚果"),
]

def main():
    archive = Path(sys.argv[1])
    with ZipFile(archive) as z:
        def rows(name):
            path = next(p for p in z.namelist() if p.endswith('/' + name))
            return csv.DictReader(io.TextIOWrapper(z.open(path), encoding='utf-8-sig'))
        foods = {int(r['fdc_id']): r for r in rows('food.csv')}
        nutrients = {int(r['id']): r for r in rows('nutrient.csv')}
        ids = {r[0] for r in SELECTION}
        nutrient_ids = {1008: 'calories', 1003: 'protein', 1005: 'carbs', 1004: 'fat'}
        for ident in nutrient_ids:
            assert nutrients[ident]['unit_name'].upper() == ('KCAL' if ident == 1008 else 'G')
        amounts = {ident: {} for ident in ids}
        for r in rows('food_nutrient.csv'):
            food_id, nutrient_id = int(r['fdc_id']), int(r['nutrient_id'])
            if food_id in ids and nutrient_id in nutrient_ids:
                amounts[food_id][nutrient_ids[nutrient_id]] = float(r['amount'])
        output = []
        for ident, name, state, group in SELECTION:
            assert set(amounts[ident]) == set(nutrient_ids.values()), ident
            output.append(dict(id=str(ident), name=name, state=state, group=group,
                               sourceName=foods[ident]['description'], per100g=amounts[ident]))
    root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/nutrition'
    root.mkdir(parents=True, exist_ok=True)
    (root / 'foods.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (root / 'SOURCE.txt').write_text(
        'USDA Agricultural Research Service. FoodData Central, SR Legacy April 2018.\n'
        'https://fdc.nal.usda.gov/download-datasets/\n'
        'https://fdc.nal.usda.gov/api-guide/#bkmk-9 (Public domain / CC0)\n'
        'All values are per 100 g edible portion, including liquids weighed in grams.\n'
        'Energy uses nutrient 1008 (kcal); protein 1003, carbohydrate 1005 and fat 1004 (g).\n'
        'Prepared and raw entries are independent reference samples, not cooking yield conversions.\n'
        'Archive SHA256: ' + hashlib.sha256(archive.read_bytes()).hexdigest() + '\n', encoding='utf-8')
    print(f'Wrote {len(output)} foods with complete energy/protein/carbohydrate/fat records.')

if __name__ == '__main__':
    main()
