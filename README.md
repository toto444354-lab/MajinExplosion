# 魔人自爆（Majin Explosion）— 第七階段原始碼

目標：**Minecraft Java 1.20.1 / Fabric / Java 17**。模組版本 **0.7.0**。

> **目前狀態：** 已完成原始碼修改、Python 靜態／演算法檢查，且使用真正的 `javac --release 17` 編譯並執行「純 Java 爆炸幾何」測試。**尚未完整執行 Fabric/Gradle build，也尚未在 Minecraft 中實測。**此專案不是已確認可載入的遊戲 Mod 成品。請勿在重要世界直接測試地形破壞。

## 第七階段：建置相容性、多人同步與安全預設

這一階段**沒有聲稱已完成 Minecraft 編譯**；目標是修正一個可由 Yarn 1.20.1 API 文件確認的存取錯誤，並使未編譯的結果不會被誤當成安裝用 JAR。

| 項目 | 本次實際變更 |
|---|---|
| `FallingBlockEntity` 相容性 | 移除從 Mod 套件直接讀寫 `entity.dropItem` 的程式碼（此欄位在 Yarn 1.20.1 為**套件私有**）；保留公開的 `setDestroyedOnLanding()` 和靠近核心時 `discard()` 清除碎塊，避免存取控制編譯錯誤。完整方塊掉落行為仍須實機驗證。 |
| 客戶端爆炸同步 | 新增 `BlastTimeline.java`，舊的網路快照不再把已經展開的能量球**倒退縮小**；仍以伺服器傳來的年齡同步前進。 |
| 關服清理 | 增加 `AbilityController.shutdown()`，清除所有玩家蓄力狀態和殘留方塊實體，避免同一 Java 行程內重新開啟單人世界時帶入舊資料。 |
| 按鍵狀態 | 離開伺服器或換連線時重設 G 按鍵記錄，避免重新登入時卡在「已按下」的舊狀態。 |
| 安全預設 | **第一次產生設定檔時**，`terrain_absorption_enabled=false` 及 `explosion_terrain_enabled=false`，防止測試時不小心改動世界。要測地形請自行改 `true`。已存在的設定檔不會被覆寫。 |
| 可重現驗證 | 加入獨立的 `BlastTimelineTest.java`、`tests/check_stage7.py`，以 `javac --release 17` 編譯並執行。五組（第三至第七階段）離線測試均已通過。 |
| 跨平台建置檢查 | 新增 `scripts/build_mod.py`，自動執行所有離線測試、尋找 Gradle、嘗試 `clean build`，**僅在實際找到 Gradle 產出的 JAR 時回報成功**。 |

### 第七階段實際建置結果

- 本次執行了 `python3 scripts/build_mod.py`，第三至第七階段測試全部通過，包含 Java 17 相容的純數學／時間軸編譯與執行。
- 執行環境**沒有 Gradle**，沒有 Gradle wrapper / Fabric 依賴快取，且無法連線至 Gradle 下載站。因此建置程序明確停止，**沒有生成 `majin-explosion-0.7.0.jar`**。
- 完整模組的 Fabric API 整合、渲染、傷害、自爆流程、地形變動及多人連線**仍需正式 Gradle 編譯與 Minecraft 實機驗證**。

### 建置方式（Windows 或 macOS / Linux）

請安裝 **JDK 17**（或 Gradle 8.6 支援的較新 JDK）、**Gradle 8.6**，以及 **Python 3**；第一次建置需網路下載 Minecraft / Fabric 依賴。進入解壓後的專案根目錄：

```bash
python scripts/build_mod.py
```

測試全部通過、Gradle `clean build` 成功後，腳本會確認 `build/libs/majin-explosion-0.7.0.jar` 存在並列出大小。若出現 Gradle、Fabric API 或 Yarn 編譯錯誤，請保留完整錯誤紀錄以利修正；**只有真的生成 JAR 才能放進 `mods` 資料夾**。沒有本機 Gradle 也可將整個專案上傳 GitHub，從 `Actions → Fabric 1.20.1 build → Run workflow` 執行（CI 未實際跑過）。

## 操作與爆炸規則

- **按住 G 蓄力**：上限 **10 秒（200 ticks）**。鬆開 G 可暫停並保留能量，再按可繼續蓄力。從正上方往下看，金色能量球、外圍氣流、被吸起的方塊都**順時針**旋轉。
- 蓄力期間：將 **6–24 格**內的非玩家生物拉向中心，每 **20 ticks** 嘗試給予 **9 HP** 傷害；從地形吸起有限數量的方塊，飛向玩家後粉碎、**不掉物品**。
- **按 H 自爆**：至少蓄力 1 tick 後，爆炸固定在施放瞬間的位置。從上方觀看，金白色光球表面的螺旋亮紋**逆時針**轉動，光球約 **240 ticks（12 秒）**逐漸向外擴張。
- 爆炸半徑按蓄力比例縮放，滿蓄力最大 **200 格**；形狀是三維橢圓球，垂直半徑為水平的 **0.52 倍**。
- 爆炸期間施放者被固定、暫時無敵與無重力；爆炸動畫結束後恢復原本的狀態，接著死亡。爆炸波對其他生物／玩家造成一次傷害及擊退。

## 第六階段沿用：閃白核心、逆時針震波與爆炸音效

| 項目 | 實際程式功能 |
|---|---|
| 白色爆發核心 | H 鍵觸發的 **前 14 ticks（約 0.7 秒）**，客戶端出現短暫白色高亮球殼並逐漸消散。是世界內特效，**不是全螢幕閃白**，也不影響玩家輸入。 |
| 逆時針震波 | 在水平 XZ、垂直 XY、垂直 YZ 三個平面繪製逐漸擴張的金白色震波環；環上的六個亮點沿逆時針方向移動。它們是**三組平面網格**，不是物理衝擊波模擬。 |
| 漸層光暈 | 爆炸球外圍新增淡金光暈，隨 240 ticks 動畫漸漸減弱。光暈、震波與閃白半徑皆受蓄力上限約束，滿蓄力最大 200 格。 |
| 爆炸音效 | 伺服器在發動與視覺結束時各播放一次原版爆炸音效，附近玩家都能聽到。暫無自訂音效素材。 |
| 可獨立測試的特效公式 | 新增 `BlastVisualProfile.java`、`BlastVisualProfileTest.java`、`tests/check_stage6.py`，**使用真實的 javac --release 17 編譯執行**，檢查閃光衰減、震波方向、半徑上限及不倒退。 |
| CI 延伸 | 既有 GitHub Actions 會執行第三至第七階段測試；完整 Fabric build 仍需在可下載依賴的機器執行。 |

**重要：** 這些都是客戶端的視覺幾何更新；G 蓄力、伺服器自爆傷害、玩家死亡、分批地形破壞及多人同步流程仍沿用第五階段。畫面仍待 Minecraft 實機確認，不可視為已完成可玩的成品。

## 第五階段沿用：立體金白光球與多人同步

| 項目 | 實際原始碼功能 |
|---|---|
| 立體球殼 | 客戶端在 `WorldRenderEvents.LAST` 繪製四層橢圓形網格：金橘色外暈、金色主殼、白金色內殼、白色核心。採用加色混合，減少「只有幾圈粒子」的空洞感。 |
| 逆時針亮紋 | 網格表面的五組螺旋亮紋由負向角度相位驅動。順／逆時針的方向基準均是**從正上方往下看**。 |
| 網路同步 | 伺服器只傳爆炸 UUID、維度、中心、半徑、蓄力與年齡；客戶端本地更新動畫。爆炸開始、每 20 ticks、結束都會同步，並處理中途加入的玩家。 |
| 維度安全 | 客戶端切換世界／維度時清理舊動畫；收到其他維度的封包會忽略。 |
| 原生粒子減量 | 伺服器只保留中心金白粒子／尾光；大型球殼改由客戶端繪製以減少遠距離大量粒子封包。 |
| 可重現建置 | 固定 Fabric Loom **1.6.12**，加入 GitHub Actions 的 **JDK 17 + Gradle 8.6** 建置工作流程（**尚未實際執行該 CI**）。 |
| Java 數學測試 | `BlastGeometry` 完全不依賴 Minecraft，可直接使用 `javac` 編譯執行，驗證半徑 200、橢圓縱軸、爆炸方向和邊界。 |

**特效限制：** 這是使用原版渲染系統的**加色透明立體網格、交叉平面震波環**，不是真正的動態光照、體積光、Bloom 後製或自訂 Shader。看起來會比粒子環連續，但亮度、遮擋、顯示距離和效能仍需進遊戲實測；並不保證在 200 格外完整可見。

## 地形破壞原型（沿用第四階段）

- 將方塊真正設為空氣，預期形成中心深、外圍淺的橢圓形大坑。滿蓄力時理論坑洞中心向下約 **56 格**、向上約 **35 格**。
- 每個爆炸工作每 tick 最多掃描 **4096** 個方塊、前進 **256** 列，預設最多實際移除 **512** 個方塊；最多同時兩個工作。大型坑洞可能在視覺效果結束、玩家死亡後**繼續挖掘許久**。
- 只處理**當下已載入的區塊**，絕不強迫載入區塊；因此遠距離坑洞可能不完整。跳過基岩、不可破壞方塊、方塊實體（箱子等），以及遊戲本身判定不可修改的位置；不保證相容所有領地／保護 Mod。
- 地形破壞沒有跨伺服器重新啟動保存，斷線待死狀態也只記在執行中的伺服器記憶體。
- 大量挖掘不產生掉落物、會略過部分鄰接更新，可能出現暫時浮空的沙子等現象。這仍是效能優先的**未實測原型**。

## 設定檔

第一次啟動會建立 `config/majin_explosion.properties`。修改設定後重新啟動世界／伺服器：

```properties
terrain_absorption_enabled=false
mob_damage_enabled=true
blocks_per_second=8
max_active_debris_per_player=24
explosion_terrain_enabled=false
explosion_damage_enabled=true
max_explosion_block_removals_per_tick=512
```

為避免誤毀世界，**第七階段第一次產生設定檔時預設就是 `explosion_terrain_enabled=false` 和 `terrain_absorption_enabled=false`**，先測按鍵、3D 網格、傷害和死亡。地形測試請務必使用備份世界，從 1 秒短蓄力開始。

## 手動完整建置方式（需要可連網環境）

安裝 **JDK 17**、**Gradle 8.6**。目前專案沒有 Gradle Wrapper。進入根目錄後執行：

```bash
gradle --no-daemon build
```

成功後，放入 `build/libs/majin-explosion-0.7.0.jar` 至 Minecraft Java **1.20.1 Fabric** 的 `mods` 目錄，並安裝相容的 **Fabric API**（開發設定為 `0.92.2+1.20.1`）。

若使用 GitHub，可將專案上傳到自己的 repository，手動執行 `Actions → Fabric 1.20.1 build → Run workflow`。`.github/workflows/build.yml` 會在 GitHub 的 Linux runner 上嘗試建置並上傳 jar。**尚未跑過，因此可能仍暴露 API 相容性錯誤；應以 workflow 的實際紀錄為準。**

### 已可在本地執行的驗證

```bash
python tests/check_stage3.py
python tests/check_stage4.py
python tests/check_stage5.py
python tests/check_stage6.py
python tests/check_stage7.py
```

`check_stage5.py`、`check_stage6.py`、`check_stage7.py` 會分別呼叫 `javac --release 17` 與 `java`，實際編譯並運行 `tests/BlastGeometryTest.java`、`tests/BlastVisualProfileTest.java` 與 `tests/BlastTimelineTest.java`。其他測試是檔案契約／演算法模型檢查，不能當成 Minecraft 整合測試。若某台電腦無 Java/JDK，第三項會提示缺少 `javac`。

## 下一步

1. 在真正的 JDK 17／Gradle 8.6 可連網環境執行完整編譯，修正任何 Yarn / Fabric API 的版本誤差。
2. 遊戲內對照爆炸逆時針方向、FPS、是否顯示過亮或背面透光；必要時調整 mesh 層數、透明度與深度測試。
3. 設計 200 格坑洞的跨重啟保存、卸載區塊續挖、額外的領地保護判定與可配置效能限制。

### 原始碼入口

- `AbilityController`：G/H 控制、蓄力進度、順時針蓄力粒子。
- `ChargeEffects`：生物吸引與傷害、實體飛行方塊。
- `ExplosionEffects`：爆炸伺服器邏輯、自爆傷害／死亡、分批地形挖洞、動畫同步。
- `BlastSyncPayload`：S2C 的爆炸快照格式。
- `BlastSphereRenderer`：客戶端的四層金白橢圓網格與逆時針亮紋。
- `BlastGeometry`：可獨立編譯的爆炸數學。
- `BlastVisualProfile`：白色脈衝、逆時針震波環、光暈衰退曲線（純 Java）。
- `AbilityConfig`：設定檔。

- `BlastTimeline`：第七階段新增的純 Java 網路快照時鐘，防止爆炸動畫倒退。
- `scripts/build_mod.py`：本地建置指令，若缺少 Gradle 或 JAR 則明確報錯。
