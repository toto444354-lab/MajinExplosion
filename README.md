# 魔人自爆 Majin Explosion — 第八階段（0.8.0）

Minecraft **Java 1.20.1 / Fabric / Java 17** 的原創模組原始碼。這是第七階段之後的修正版，**需要重新編譯成 `.jar`，不能把 ZIP 直接放進 `mods`**。模組使用 Fabric API（Gradle 定義 `0.92.2+1.20.1`）。

> 驗證狀態：第 3–8 階段的離線 Python 稽核及純 Java 數學測試已執行。**本環境沒有 Gradle / Fabric 依賴，因此第八階段尚未執行完整 Gradle 編譯、也尚未在 Minecraft 實測。** 第七階段的 GitHub 成功建置不能代表第八階段已成功。

## 第八階段改動（包含最新要求）

| 項目 | 實作 |
|---|---|
| 按住 G | 只有持續按住 G 才蓄力；人物固定原地不可走路／跳躍，但可以轉視角。蓄力最多 10 秒；滿蓄力之後仍保持吸力和光球。 |
| 放開 G | 取消本次蓄力、**進度歸零**、消除光球／吸力、恢復移動。沒有保留進度的暫停模式。 |
| 按 H | 必須在**仍按住 G** 時按 H 才能開始自爆。原有爆炸施法者固定、12 秒擴張、結束死亡的邏輯保留。 |
| 黃色圓體 | 完全移除蓄力時用 `FLAME` 拼球的做法。客戶端使用**四層透明金黃色球面網格**呈現連續 3D 圓球，表面亮紋順時針旋轉；不像爆炸，蓄力球為正球體而非橢圓形。 |
| 風被吸進來 | 球外圍以**32 組淡金白色 3D 風帶**從吸力範圍朝球面移動，同時順時針捲動；同樣不使用火焰粒子。風帶是視覺網格，真正的吸力由伺服器處理。 |
| 光球碰撞 | 伺服器使用**與畫面相同半徑的球面碰撞邊界**，生物靠近時被擋在球殼之外，不再一路拉到玩家身上。這是伺服器計算／限制實體位置的碰撞場，**不是獨立註冊的 Minecraft 方塊或自訂 Entity**。 |
| 生物傷害 | **吸引途中不造成傷害。** 生物碰上光球外殼時，嘗試造成 **1 HP（半顆心）** 傷害，對每個生物至多每 20 ticks（約 1 秒）一次。實際扣血可能受護甲、無敵幀、抗性等機制影響。 |
| 自爆波傷害 | 對每個命中的目標**只判定一次 9 HP（4.5 顆心）**，其餘自爆特效及向外擴張規則保留。 |
| 方塊吸取 | 開啟地形吸取後，飛行方塊在接近**球面**時碎裂，不是飛到玩家身上才碎。 |
| 多人可見 | 伺服器以 `ChargeSyncPayload` 把球的位置、進度和狀態同步給附近玩家；由各客戶端繪製 3D 球與風帶。放開 G 或轉換爆炸時立即發送消除訊息。 |

蓄力球半徑從 **0.7 格**增加到 **2.25 格**，生物吸引範圍從 **6 格**提高到 **24 格**。角度方向皆以**從玩家正上方向下看**為準：蓄力與風順時針，自爆逆時針。

## 地形破壞為什麼仍然預設關閉？

滿蓄力的爆炸水平半徑上限仍是 **200 格**；若預設直接挖洞，可能破壞大量方塊。為了避免使用者把新測試版放進既有存檔就毀掉地圖，**地形吸取及爆炸挖洞仍預設 `false`**。

第一次啟動後，先關閉遊戲，編輯：

`%appdata%\.minecraft\config\majin_explosion.properties`

或你啟動器實際遊戲目錄中的 `config/majin_explosion.properties`。找到：

```properties
terrain_absorption_enabled=false
explosion_terrain_enabled=false
```

**只在新測試世界、有備份時**把兩個 `false` 改成 `true`、存檔並重新啟動遊戲。其他設定如 `mob_damage_enabled=true`（控制撞球傷害）、`explosion_damage_enabled=true`（控制爆炸傷害）則預設啟用。若存在舊版設定檔，新版不會自動覆蓋原先值。地形只在已載入區塊內分批處理；200 格的坑洞可能不完整或在爆炸動畫結束後持續生成。

## 建置（GitHub Actions）

1. 將本專案**根目錄裡的檔案**更新到你的 GitHub repository 根目錄，確保 `build.gradle`、`gradle.properties`、`src`、`tests` 位於最外層（不能多包一層 `MajinExplosion`）。
2. `.github/workflows/build.yml` 是這份原始碼內附的工作流程。若你原本已自行建立 `.github/workflows/main.yml`，它也可以執行 `gradle --no-daemon build`；**兩個工作流程可能會各自執行**，不代表出現兩次就是錯誤。
3. 到 GitHub 專案的 **Actions** 查看最新執行。成功後在 **Artifacts** 下載 `.zip`，解壓取得 `majin-explosion-0.8.0.jar`，不要使用 `-sources.jar`。
4. **把舊的 `majin-explosion-0.7.0.jar` 從 `mods` 移除**，才放入新版 `majin-explosion-0.8.0.jar`。保留 Fabric API；不要同時放兩個版本。
5. 啟動 Minecraft Fabric 1.20.1，先在新世界、地形破壞關閉的狀態測試按住 G、鬆開 G、滿蓄力碰撞、按住 G 時按 H。

若 GitHub 出現紅叉，點開失敗執行的 **Build Minecraft Mod** 或 **Compile and test Fabric mod**，把第一個 Java 編譯錯誤傳回來，**不要把這個版本當成已驗證的可用 JAR**。

### 離線測試

```bash
python tests/check_stage3.py
python tests/check_stage4.py
python tests/check_stage5.py
python tests/check_stage6.py
python tests/check_stage7.py
python tests/check_stage8.py
```

`check_stage8.py` 會真正用 Java 17 編譯並執行 `ChargeRulesTest` 與 `ChargeFieldTest`，覆蓋蓄力上限、旋轉方向、光球碰撞邊界及風流由外往內的公式；另外會靜態檢查 1 HP／9 HP 與同步程式碼，但**不會執行 Minecraft 的真實碰撞或渲染**。

## 注意與後續

- 大型黃色光球採用可見的加色透明網格，不是真正體積光、動態照明或 Bloom。**顯示效果和 FPS 需要遊戲內測試**。
- 遠距離吸力可能會被地形阻擋或受到 Minecraft 實體物理機制影響，首次測試建議在平地放幾隻豬、牛或殭屍。
- 生物碰撞採用幾何式球面限制，若實際遊戲出現抖動、瞬移或漏判，需要根據實測資料調整。大量生物同時被吸仍須效能測試。
- 原有的爆炸地形拆除依然只處理已載入的區塊並以 tick 預算分批執行。不要在重要存檔開啟它。
