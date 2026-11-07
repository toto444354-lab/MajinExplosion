# 魔人自爆（Majin Explosion）— 第十一階段 v0.11.0

Minecraft Java **1.20.1** / Fabric / Java 17 原始碼。**這不是可直接安裝的 JAR**；上傳 GitHub 後需透過 Actions 編譯。第十一階段針對大型地形破壞的性能及功能方塊遺留進行修正。

## 本版修正

- **箱子、熔爐、漏斗等功能方塊可以被爆炸消除**：舊版有 `!state.hasBlockEntity()`，會跳過所有帶方塊實體的方塊；本版已移除該限制。一般工作台等功能方塊也會按照可破壞性判定。
- **避免容器掉落大量物品**：刪除帶方塊實體的容器前清空 `Inventory`，並使用 `Block.SKIP_DROPS`；這表示**箱子裡的物品也會被永久消除**。不可還原，必須在備份測試世界使用。其他模組的特殊方塊如有自己的額外掉落回呼，仍可能需要相容性修正。
- **保留不可破壞方塊**：硬度為負的基岩、終界傳送門框、指令方塊等仍保留；也遵守 `canPlayerModifyAt` 檢查。蓄力時吸起方塊的功能仍不支援帶方塊實體的容器，本版修改針對**爆炸挖洞**。
- **減少排序負擔**：爆炸的三維橢圓方塊掃描，從「每方塊優先佇列」改為整數半徑分層的 `RadiusBucketQueue`，維持由中心往外的挖洞順序，不再對大量方塊做二元堆排序。
- **跳過空區塊段**：一次略過已載入區塊內的全空 16 格高度區段，不必逐格查詢。
- **限制單 tick 負荷**：除每 tick 最多方塊數外，新增單次地形處理時間預算、功能方塊移除上限 32 個／次，避免嚴重卡頓；最多仍允許 2 場同時爆炸。這是降低尖峰負載，不代表能在 6 秒挖完 200 格地形。
- **視覺範圍與挖洞範圍可分離**：保留最大 200 格爆炸光球與範圍傷害，但地形破壞半徑預設上限縮為 96 格，以便日常測試；可自行改為 200 格完整坑洞（處理時間與負載會顯著增加）。地形啟用時，光球仍依地形挖掘進度限制擴張，不會直接跑過未處理區域。
- **保留之前功能**：G 蓄力鎖定、放開取消；黃色網格球與風流；撞到蓄力球每秒 1 HP；爆炸範圍內每秒 9 HP；橢圓坑洞、水蒸發、邊緣砂石煙霧、爆炸後殘煙、創造模式死亡開關。

## 設定方式

關閉 Minecraft，開啟 `%appdata%\.minecraft\config\majin_explosion.properties`，修改後儲存並重啟遊戲：

```properties
# 開啟爆炸破壞（目前仍預設 false，避免誤炸存檔）
explosion_terrain_enabled=true
# 每 tick 上限：建議從 2048～4096 開始測試，能接受卡頓再提高
max_explosion_block_removals_per_tick=4096
# 每次地形處理最多使用約 6000 微秒 (= 6 毫秒)；保護伺服器單 tick
explosion_carve_micros_per_tick=6000
# 爆炸球最大 200，地形破壞半徑預設 96，必要時可調整至 200
max_explosion_terrain_radius=96
# 水蒸發、殘煙、創造模式免死
explosion_evaporates_water=true
residual_smoke_enabled=true
creative_caster_death_enabled=false
```

注意：**已存在的設定檔會保留舊數值**，新版只會補上缺少的設定。即使新 JAR 上線，舊的 `explosion_terrain_enabled=false` 仍需手動改成 `true`。

效能取捨：如果實測仍很慢，可先把 `max_explosion_terrain_radius` 降到 `48` 或 `64`。**全尺寸 200 格地形包含數百萬個體素，沒有能在所有電腦上安全地瞬間刪光的方法。** 本版本優先讓伺服器保持可回應；在大型建築或大量容器區域，實際速度仍可能明顯降低。

## 建置和測試

1. 先備份世界，**請勿直接在主存檔測試**。關閉 Minecraft。
2. 將這個 ZIP 內的 `src`、`tests`、`gradle.properties`、`build.gradle` 等更新到之前的 GitHub 專案根目錄，**保留已經正常運作的 `.github/workflows/main.yml`**。如果也要上傳本 ZIP 的 `.github/workflows/build.yml`，會額外建立一個編譯工作流程，不是必需。
3. 儲存（Commit）後打開 GitHub Actions，確認 `Fabric 1.20.1 build` 綠色勾勾。下載 Artifacts 解壓後，只取不帶 `-sources` 的 `majin-explosion-0.11.0.jar`。
4. 移除 mods 內舊版同名 JAR，只保留新版與 Fabric API。
5. 在新測試世界試 0.5 秒、1 秒、2 秒蓄力：建造裝了物品的箱子、熔爐、漏斗，確認新爆炸波掃過後方塊消失且不產生大量掉落物。**其中物品會永久消失**。
6. 再測試 3 秒以上蓄力，觀察 TPS 與延遲；不要一開始就滿 10 秒釋放 200 格爆炸。

### 離線驗證

```bash
python tests/check_stage11.py
```

此腳本會用 Java 17 編譯、執行 `RadiusBucketQueue`、三維橢圓、爆炸傷害節奏等純 Java 測試，並執行原始碼檢查。這**不能替代 Fabric/Gradle 編譯或實際遊戲測試**。
