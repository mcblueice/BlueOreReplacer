# BlueOreReplacer

一個用於 Paper 伺服器的礦物代換插件

## 功能特色

- 對齊原版設定的礦物生成機率: 依世界、生態域、高度與特徵計算機率與礦脈尺寸
- 自動人工標記: 追蹤玩家放置方塊，避免在人工建築內生成礦物，以及避免替換人工放置的礦物
- 支援 PlaceholderAPI，提供查詢所在位置礦物特徵的生成機率

## 指令列表

| 指令 | 用途 | 權限節點 |
|------|------|----------|
| `/blueoreplacer reload` | 重新載入 `config.yml` 與 `lang.yml` | `blueoreplacer.reload` |
| `/blueoreplacer debug` | 切換偵錯訊息顯示（玩家或主控台） | `blueoreplacer.debug` |
| `/blueoreplacer check` | 切換檢查模式，右鍵判斷方塊是否為人工 | `blueoreplacer.check` |
| `/blueoreplacer check <x> <y> <z> [world]` | 查詢指定座標是否為人工方塊 | `blueoreplacer.check` |
| `/blueoreplacer clear [x] [y] [z] [world]` | 清除指定區塊的人工標記 | `blueoreplacer.debug` |
| `/blueoreplacer simulate <OreFeature> [Y] [BiomeMode]` | 模擬指定礦物特徵的生成機率  | `blueoreplacer.debug` |

> `OreFeature`、`BiomeMode` 皆支援 Tab 補全，座標可使用 `~` 相對值

## PlaceholderAPI 支援

| 變數名稱 | 說明 |
|----------|------|
| `%blueorereplacer_simulate_chance_<OreFeature>%` | 取得玩家所在地對應礦物特徵的生成機率(百分比) |

## 礦物特徵列表

| 礦物特徵 | 描述 |
|----------|-----------|
| `coal_main` | 煤礦主分佈，覆蓋地表至山脈高度的三角型分布 |
| `coal_alt` | 煤礦高海拔分佈，僅在高山地區平均分布 |
| `iron_main` | 鐵礦主分佈，地底至地表的主要來源 |
| `iron_high` | 鐵礦高海拔分佈，高山／洞穴上層的大量生成 |
| `iron_alt` | 鐵礦平均分佈，填補主分佈以外的高度空缺 |
| `copper_main` | 銅礦主分佈，覆蓋大部分地下高度 |
| `gold_main` | 金礦主分佈，地底的標準生成 |
| `gold_alt` | 金礦深層分佈，附加於高度 -48 以上 |
| `gold_badlands_extra` | 金礦惡地增量，只在惡地生物群系額外生成 |
| `lapis_main` | 青金石主分佈，Y -32 至 32 的三角型產量 |
| `lapis_alt` | 青金石平均分佈，覆蓋 -64 至 64 高度 |
| `redstone_main` | 紅石主分佈，集中於 -64 至 -32 的三角型產量 |
| `redstone_alt` | 紅石平均分佈，延伸至 16 高度 |
| `emerald_mountain` | 綠寶石山地分佈，只在山地/滴石山地生成 |
| `diamond_cluster_common` | 鑽石普通團簇，含露天懲罰的標準鑽石 vein |
| `diamond_cluster_buried` | 鑽石掩埋團簇，只在深層覆蓋石內生成 |
| `diamond_cluster_large` | 鑽石大型團簇，稀有但礦脈巨大 |
| `diamond_cluster_medium` | 鑽石中型團簇，固定高度區間的平均分布 |
| `nether_quartz_main` | 地獄石英礦主分佈，覆蓋地獄大部分高度 |
| `nether_gold_main` | 地獄金礦主分佈，與石英礦高度範圍相同 |
| `debris_main` | 遠古遺骸主分佈，集中於 Y 8–24 的三角型分布 |
| `debris_alt` | 遠古遺骸平均分佈，覆蓋地獄更大高度範圍 |

## 授權 License

本專案採用 MIT License
