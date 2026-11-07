# 魔人自爆（Majin Explosion）— 第十階段 v0.10.0

**Minecraft Java 1.20.1 / Fabric / Java 17** 的模組原始碼。
這份 ZIP **不是**可直接放進 `mods` 的 JAR；需要在 GitHub Actions 建置。

本版依照實測回饋修正了爆炸持續傷害、三維橢圓坑洞與效能，同時新增蒸發水、爆炸後煙霧、創造模式死亡開關與**沿擴張邊緣向外上方噴起的砂石煙塵**。

## 實際行為

| 功能 | 本版內容 |
|---|---|
| G 蓄力 | 按住 G、玩家不能移動；放開取消，蓄力清零；滿 10 秒仍持續吸力；H 在按住 G 時釋放 |
| 黃色能量球 | 3D 客戶端球面，非火焰粒子；吸入風流由四面八方接近球體 |
| 接觸傷害 | 生物被吸往球面，途中無傷害，碰到球面每秒 1 HP |
| 爆炸傷害 | **整個擴張的橢圓體積內**每秒嘗試對生物造成 **9 HP**，不再只在外圈命中一次（仍受 Minecraft 原生無敵幀等限制） |
| 形狀 | 按單一方塊的「三維橢圓到達距離」排序挖除，避免先挖到底的圓柱牆；中心深、邊緣淺 |
| 速度 | 理想視覺展開時間調整至 **120 ticks = 約 6 秒**。啟用同步地形時，實際可能比 6 秒長很多，以避免卡服 |
| 水 | 選項 `explosion_evaporates_water`：爆炸清除水源、流水與含水方塊；周圍水可能照原版機制再次流入 |
| **砂石噴射** | **新功能**：每 2 ticks 在當前爆炸邊界發射分散在 360° 的方塊碎片與灰褐色煙塵。碎石沿半徑向外、同時向上拋，灰塵較慢地往外擴散。靠近地表發生，並隨光球同步向外移動 |
| 爆炸殘煙 | 爆炸完成後，在已載入的坑洞範圍持續分散冒煙，預設 20 秒漸淡 |
| 創造模式 | `creative_caster_death_enabled=false` 時不會因自己的自爆死亡；生存模式仍會死亡 |

### 如何開啟地形破壞（非常重要）

先關閉 Minecraft，按 `Win + R` 輸入：

```text
%appdata%\.minecraft\config\majin_explosion.properties
```

找到下列設定，修改後存檔並重開 Minecraft：

```properties
terrain_absorption_enabled=true
explosion_terrain_enabled=true
explosion_evaporates_water=true
explosion_rim_debris_enabled=true
rim_debris_particles_per_burst=18
rim_smoke_particles_per_burst=14
residual_smoke_enabled=true
residual_smoke_seconds=20
smoke_particles_per_tick=12
creative_caster_death_enabled=false
max_explosion_block_removals_per_tick=4096
```

- `terrain_absorption_enabled` 控制蓄力吸起方塊；`explosion_terrain_enabled` 才控制爆炸挖洞。
- **新的邊緣砂石噴射只會在爆炸地形破壞開啟時出現**；若地形破壞關閉，只有能量球、傷害等效果。
- **本版會把缺少的「新設定」補進既有 properties，並保留舊設定值**。如果原先地形破壞是 `false`，更新 JAR 後也仍會是 `false`，請自行改為 `true`。
- `rim_debris_particles_per_burst`、`rim_smoke_particles_per_burst` 各自限制在 **0–48**，每兩 ticks 至多各生成一次指定數量，沒有新增常駐方塊實體。若 FPS / 網路吃緊，先降成 8 和 6，或設定 `explosion_rim_debris_enabled=false`。
- `max_explosion_block_removals_per_tick` 限制在 **64–8192**；提高會加速挖洞但也提高瞬間負載，必須自行測 TPS。
- 煙霧、蒸發與死亡選項修改後同樣要重新啟動世界／伺服器。

## 推薦第一次測試

1. **先備份世界**，在全新測試世界測，不要使用主存檔。
2. 在 GitHub Actions 編譯第十階段，確認真正產生 `majin-explosion-0.10.0.jar`。
3. 關閉遊戲，從 `mods` 移除先前的 0.8/0.9 JAR，只保留新 JAR 和 Fabric API。
4. 確認新設定檔中的 `explosion_terrain_enabled=true`，重開遊戲。
5. 在平坦地面按住 G 約 **0.5–1 秒**，按住 G 時按 H。先看小半徑的橢圓坑洞、往外拋出的碎石煙塵、範圍內每秒 9 HP、清水及 20 秒殘煙。
6. 再逐步測試 2、3、5 秒蓄力。**不要一開始就測滿蓄力 200 格**：那可能涉及大量方塊及未載入區塊。
7. 創造模式免死亡開關預設為 `false`，可在測試世界中安全反覆施放；改成 `true` 後重新進世界測試死亡效果。

## 技術限制／效果逼近程度

- **砂石噴射是有速度的原版方塊粒子、塵粒和煙霧**，不是完整擬真流體或圖片中大型體積煙塵模型。會從爆炸邊界「向外且向上」飛散，能呈現砂石被衝擊波掀起的方向感，但雲團大小與遮蔽效果仍須實際遊戲調整。
- 只在**已載入**的區塊刪除方塊；基岩、無法破壞方塊、帶方塊實體的方塊及無建造權限區域跳過；不保證半徑 200 格的整個範圍每格都完整挖除。
- 當啟用地形同步時，光球不能超前已完成的挖除進度。如果世界較慢，視覺展開會超過 6 秒；這是穩定性限制，不是已解決的效能瓶頸。
- 消除水不代表永久禁止水回填。爆炸周邊的水仍可依原版規則重新流入坑洞。
- **本環境僅完成原始碼靜態檢查和獨立 Java 數學測試；尚未執行完整 Gradle/Fabric 編譯及遊戲實測。** GitHub Actions 成功與遊戲實測後才能視為可用版本。

## 編譯方法

將 ZIP **內部**的 `src/`、`tests/`、`build.gradle`、`gradle.properties` 等更新到 GitHub 儲存庫根目錄，保留既有能工作的 `.github/workflows/main.yml`。上傳後 Commit，等待 `Fabric 1.20.1 build` 顯示綠勾，下載 Artifacts ZIP，解壓後只把**不是 `-sources.jar`** 的 0.10.0 JAR 放入 `mods`。

離線檢查可執行：

```bash
python tests/check_stage10.py
```

此指令會使用 `javac --release 17` 編譯並執行爆炸半徑、橢圓挖洞、持續傷害節奏、砂石放射軌跡、殘煙與創造模式死亡選項的純 Java 測試，不等於 Minecraft 實際相容性驗證。
