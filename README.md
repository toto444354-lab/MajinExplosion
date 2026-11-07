# 魔人自爆（Majin Explosion）— 第十三階段 v0.13.0

Minecraft **Java 1.20.1 / Fabric / Java 17** 原始碼專案。**不是可直接放進 `mods` 的 JAR**；請使用 GitHub Actions 編譯後再安裝。

## 第十三階段新增（重點）

- **營火煙霧：** 本人／假人蓄力時，使用原版 `CAMPFIRE_COSY_SMOKE`，位置低且分散。預設每 **8 tick 4 顆**，不再用 CLOUD / LARGE_SMOKE 拼煙；沒有任何吸力。
- **風向向外：** 客戶端重新繪製 56 道短風痕，從能量球外側向 **360° 上下左右** 散出去，保留小幅順時針旋動。風只有視覺效果，不會吹走或拉動生物。
- **爆炸後殘煙：** 爆炸結束後在範圍內分散留下少量營火煙霧，預設持續約 14 秒逐漸稀疏；每四個 tick 最多生成六顆，不會強制載入區塊。過去存檔的煙霧設定繼續保留，但仍有硬性安全上限。
- **效能：** 減少風痕網格數量，降低長壽命煙霧粒子生成頻率。原本的地形分批挖除、安全半徑和假人自爆控制不變。**這不會消除 200 格地形爆炸的主要伺服器負載。**

## 本版改動

- **移除蓄力吸力：** 蓄力不再把生物或地形方塊吸向玩家。向內風帶已移除，改為向外擴散的視覺風痕。球體仍是順時針旋轉的黃色立體網格。
- **蓄力時有煙塵：** 光球附近、地面四周持續出現分散的營火煙霧粒子，緩緩**向外**擴散、上升；預設每 8 tick 4 顆，不生成方塊實體。
- **蓄力球接觸傷害：** 生物沒有受到吸力；若自己碰到球面，保留原設定：每秒最多造成 1 HP。可用 `mob_damage_enabled=false` 關閉。
- **新增可控的自爆假人：** 每位有管理員權限的玩家可生成一個穿金色裝備的盔甲座假人，透過指令讓它蓄力、取消、釋放自爆；玩家可離開假人旁邊，以第三人稱或創造模式飛到遠處觀察。
- **假人爆炸沿用現有邏輯：** 逆時針橢圓形光球、範圍內每秒 9 HP、挖坑、水消除、爆炸邊緣砂石噴射和殘留煙霧。假人爆炸完消失，**操作者不會因「施放者自爆死亡」規則死亡**；但生存模式站在爆炸範圍內仍會受到正常爆炸傷害。
- **本人技能不變：** 按住 G 蓄力、本人不能移動；放開 G 取消蓄力；G 按住時按 H 自爆。

## 假人操作方式（第一次使用請開啟作弊）

在**新測試世界**開啟作弊，使用有 OP 權限的角色輸入：

| 指令 | 功能 |
| --- | --- |
| `/majin dummy spawn` | 在面前 4 格生成一個穿金色裝備的測試假人 |
| `/majin dummy charge` | 假人開始蓄力；到 10 秒後維持滿蓄力，直到下新指令 |
| `/majin dummy status` | 查詢目前蓄力秒數 |
| `/majin dummy cancel` | 取消蓄力並歸零，假人保留 |
| `/majin dummy release` | 以假人當前蓄力值啟動自爆；假人在爆炸完成時消失 |
| `/majin dummy remove` | 移除尚未啟動爆炸的假人 |

**觀察流程：** `/majin dummy spawn` → `/majin dummy charge` → 飛到旁邊安全位置，等 10 秒（不用繼續按鍵）→ `/majin dummy release`。由於指令是從玩家身上發出，不能在輸入指令時自行切到別人的視角；建議創造模式飛起觀察，或使用第二個玩家視角。

每位操作者同時只能保有一個待命假人。假人於伺服器重啟或操作者登出時會清除；進行中的假人自爆由爆炸管理器負責，爆炸動畫完成後刪除假人。假人不是使用玩家皮膚的真 NPC，**目前使用原版盔甲座作為第一版測試替身**。

## 大型爆炸地形設定（請先備份世界）

關閉遊戲後，編輯 `%appdata%\.minecraft\config\majin_explosion.properties`：

```properties
explosion_terrain_enabled=true
max_explosion_terrain_radius=48
max_explosion_block_removals_per_tick=4096
explosion_carve_micros_per_tick=6000
creative_caster_death_enabled=false
residual_smoke_enabled=true
residual_smoke_seconds=14
smoke_particles_per_tick=6
charging_smoke_interval_ticks=8
charging_smoke_particles_per_burst=4
```

上面的 **48** 格只是第一次測試建議；新檔案的 `max_explosion_terrain_radius` 預設仍是 **96**，爆炸球和傷害的最大半徑仍為 **200**。既有設定檔不會被新版本覆蓋，必須自己修改。地形破壞量太大時，光球仍會等待挖洞進度，無法保證完整 200 格爆炸能在短時間完成。

**注意：** 爆炸可能清空箱子、熔爐等容器的物品；這些物品不會掉落而會永久消失。請勿在主要存檔測試。

## 如何更新 GitHub 和安裝

1. 解壓縮 ZIP，在你現有的 GitHub `MajinExplosion` 專案 `Code → Add file → Upload files` 上傳新版 `src`、`tests`、`README.md` 和 `gradle.properties` 等原始碼。**你原本的 `.github/workflows/main.yml` 已能自動建置**；不要再新增第二個 workflow。若上傳此 ZIP 隨附的 `.github`，應讓 `main.yml` 在原位置覆蓋，而不是建立嵌套資料夾。
2. Commit 後進入 **Actions**，等待最新的 `Fabric 1.20.1 build` 成功；從 Artifacts 下載 JAR 的 ZIP。
3. 解壓下載的 ZIP，找到 `majin-explosion-0.13.0.jar`（不要用 `-sources.jar`），取代 `mods` 裡的舊版，同時保留 Fabric API。
4. 啟動 Fabric 1.20.1，在新的創造模式世界開啟作弊測試上述指令。

## 煙霧細節與設定說明

- `charging_smoke_particles_per_burst=4` 控制每次蓄力煙霧數量（0～10），`charging_smoke_interval_ticks=8` 控制生成間隔（4～20 tick）。本人和測試假人共用設定。
- `residual_smoke_enabled=true` 開啟爆炸後的煙霧，`residual_smoke_seconds=14` 控制維持時間。`smoke_particles_per_tick=6` 保留舊設定名稱，但第十三階段實際上**每 4 tick 才生成一次**、每批最多 6 顆，逐漸變少，以避免營火煙霧累積過多。
- 此效果是原版營火煙霧粒子，不是會影響視線、碰撞或實際氣流的體積煙霧。原版煙霧會自然消散，不會留下永久方塊。

## 已完成的驗證

```bash
python3 tests/check_stage13.py
```

此離線測試會用 Java 17 編譯測試四面八方向外吹散的風及蓄力煙塵數學，並靜態檢查營火煙霧、殘煙節流、指令、吸力停用與假人釋放路徑。**尚未執行完整 Fabric/Gradle 編譯，亦未在 Minecraft 內實測**，首次 GitHub Actions 可能仍需 API 相容性修正。
