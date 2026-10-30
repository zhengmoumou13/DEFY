# 数据来源、授权与更新

## openGym 动作文字与模板

- 上游：[DuarteSantos8/openGym](https://github.com/DuarteSantos8/openGym)
- 固定提交：`b6cfe2bbd3f099523ffbf2677438864e9a09a06f`
- 当前导入：6,554 个文字动作、13 个入门模板。
- 内容：名称、英文名、部位、器械、目标肌群、描述、步骤、静态保持标记，以及模板引用和组次。
- 中文翻译存在时优先使用，否则保留英文；并非每个条目均为完整人工校对的中文。
- 上游主体许可为 AGPL-3.0，部分原始文字另有 MIT 来源。完整通知和许可保存在 `app/src/main/assets/opengym/`。

导入脚本只选择文字字段，不复制 `catalogue/media/`，不保存 Gym visual 媒体 URL、图片、GIF 或动画。上游通知包含未被 DEFY 导入的模块说明，保留原文并不意味着 DEFY 集成了这些模块。

### 再生成

```bash
git clone https://github.com/DuarteSantos8/openGym.git /path/to/openGym
git -C /path/to/openGym checkout b6cfe2bbd3f099523ffbf2677438864e9a09a06f
python3 scripts/import_opengym.py /path/to/openGym
```

脚本会覆盖本项目 `assets/opengym` 下的 JSON、许可、通知和 `SOURCE.txt`，不会修改上游。升级版本前先核对上游数据结构、变式继承、许可和实际 diff，再运行 `GymDataTest` / `OriginalDemoTest`。不能仅替换文件而遗漏署名。

## 原创动作示意与个人视频

`gym/` 与 `ui/gym/` 中的 Canvas 代码绘制 10 类运动示意。只按已支持的明确动作匹配，未匹配动作显示文字步骤或用户视频，不拿相近动画冒充所有变式。

用户可导入自有或另获许可的视频，当前单文件上限 100 MB。这些视频属于用户资料，不纳入源码或默认动作素材。仓库不转授任何 Gym visual 媒体权利。

## USDA 食品数据

- 数据集：FoodData Central，SR Legacy April 2018。
- 当前子集：28 条食品，包含生 / 熟、可食部、原始英文描述和 FDC ID。
- 营养：每 100 g 可食部的能量（1008，kcal）、蛋白质（1003）、碳水（1005）、脂肪（1004），后三者单位为 g。
- 数据状态：USDA 声明为公共领域，按 CC0 发布；来源见 [官方 API Guide](https://fdc.nal.usda.gov/api-guide/#bkmk-9)。
- 具体数据与归档 SHA-256：`app/src/main/assets/nutrition/foods.json` 和 `SOURCE.txt`。

### 再生成

从 [USDA 官方归档](https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip) 获取 ZIP，然后执行：

```bash
python3 scripts/import_food_composition.py /path/to/FoodData_Central_sr_legacy_food_csv_2018-04.zip
```

脚本根据明确 FDC ID 选择食品，并校验四种营养完整性。原始归档较大，不提交到仓库。增加食品时应核对英文名称、部位、烹调方式、可食部、单位和全部营养字段，不根据名称近似猜测。

这是离线参考子集，不需要 USDA API Key，也不是实时搜索 USDA 的客户端。生熟条目独立，不能据此自动推算同一份食材烹调前后的重量。

## 谭成义饮食参考

[来源与参数核对](tan-diet-source.md) 记录原视频、时间段、参数和应用自行增加的功能。DEFY 是非官方参考工具，未复制视频、截图、逐字转录或统一个案餐单。AI 根据启用的参数生成新计划，参考参数不等于诊断或个人饮食处方。

## 文档截图

`docs/images/` 来自本项目模拟器的空状态或测试样例，不包含真实用户记录、私人手机画面、API Key 或第三方动作视频。公开截图与实际界面之间可能随版本更新存在差异。
