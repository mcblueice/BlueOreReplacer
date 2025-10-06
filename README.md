# BlueOreReplacer

一個用於 Paper 伺服器的礦物代換插件

## 功能特色

- 準確的礦物生成機率: 依世界、生態、高度計算合適的礦物與礦脈尺寸
- 自動人工標記: 追蹤玩家放置方塊，避免在人工建築內生成礦物，以及避免替換人工放置的礦物
- 支援 PlaceholderAPI，提供礦物生成機率與礦脈尺寸

## 指令列表

| 指令 | 用途 | 權限節點 |
|------|------|----------|
| `/blueoreplacer reload` | 重新載入 `config.yml` 與 `lang.yml` | `blueoreplacer.reload` |
| `/blueoreplacer debug` | 切換偵錯訊息顯示（玩家或主控台） | `blueoreplacer.debug` |
| `/blueoreplacer check` | 切換檢查模式，右鍵判斷方塊是否為人工 | `blueoreplacer.check` |
| `/blueoreplacer check <x> <y> <z> [world]` | 查詢指定座標是否為人工方塊 | `blueoreplacer.check` |
| `/blueoreplacer clear [x] [y] [z] [world]` | 清除指定區塊的人工標記 | `blueoreplacer.debug` |
| `/blueoreplacer simulate <OreType> [Y] [BiomeMode]` | 模擬礦物生成機率  | `blueoreplacer.debug` |

> `OreType`、`BiomeMode` 皆支援 Tab 補全，座標可使用 `~` 相對值

## PlaceholderAPI 支援

| 變數名稱 | 說明 |
|----------|------|
| `%blueorereplacer_simulate_chance_<ore>%` | 取得玩家所在地對應礦物的生成機率(百分比) |
| `%blueorereplacer_simulate_maxvein_<ore>%` | 取得玩家所在地對應礦物的最大礦脈尺寸 |

## 授權 License

本專案採用 MIT License
