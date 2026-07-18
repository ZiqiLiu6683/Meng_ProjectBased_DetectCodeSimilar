# Windows 实验运行手册（i9-13900 / 16 GB）

从裸机到跑完 BCB 语法全集。所有命令在 **PowerShell** 中执行。
预计环境搭建 1–2 小时，BCB 全量抽取 + 运行 1–2 个通宵。

---

## 1. 环境安装

1. **JDK 17**（Temurin）：https://adoptium.net → 安装后验证 `java -version`
2. **Maven**：https://maven.apache.org/download.cgi → 解压，把 `bin` 加入 PATH，验证 `mvn -version`
3. **Python 3.10+**：https://www.python.org/downloads/（勾选 "Add to PATH"），验证 `python --version`
4. **Git**：https://git-scm.com/download/win

## 2. 代码迁移

```powershell
# 任选目录，例如 D:\work
cd D:\work
git clone <你的仓库地址或从 Mac 拷贝整个 Meng_ProjectBased_DetectCodeSimilar 文件夹>
cd Meng_ProjectBased_DetectCodeSimilar\code-sim
mvn -P semantic-analysis -DskipTests clean compile
mvn -P semantic-analysis dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
```

验证驱动（用仓库自带样例手工造 3 行 manifest 跑一遍）：

```powershell
# manifest 内容示例（路径改成绝对路径，正斜杠即可）:
# pair_id,left_path,right_path
# smoke_1,D:/work/.../code-sim/samples/A1.java,D:/work/.../code-sim/samples/A2.java
java -Xmx1g -cp "target/classes;$(Get-Content target/cp.txt)" `
  com.ziqi.codesim.next.semantic.eval.BatchPairMain smoke.csv smoke.jsonl
```

注意 Windows 类路径分隔符是 **分号**。`got NEW ...` 的 WALA 刷屏是无害噪音，
分片脚本会把它收进 .log 文件。

## 3. 数据下载

### 3.1 BigCloneEval + BCB 数据库

```powershell
cd D:\work
git clone https://github.com/jeffsvajlenko/BigCloneEval.git
```

- BCB H2 数据库：按 BigCloneEval 仓库 README 的链接下载（bcb.h2.db，约 3–4 GB）。
  若链接失效，数据库也随 "BigCloneBench_BCEvalVersion" 发布包分发
  （https://github.com/clonebench/BigCloneBench 的 releases 页面）。
  放到 `D:\work\BigCloneEval\bigclonebenchdb\`，数据库路径（去掉 .h2.db 后缀）
  即 `D:\work\BigCloneEval\bigclonebenchdb\bcb`。
- IJaDataset 语料（bcb_reduced / era 版）：同样按 BigCloneEval README 下载
  （IJaDataset_BCEvalVersion，约 1–2 GB 解压后为 `bcb_reduced\<功能目录>\...`）。
  解压到 `D:\work\bcb_reduced`。
- H2 驱动 jar：BigCloneEval 的 `libs\` 目录自带（h2-1.3.176.jar）。

### 3.2 SemanticCloneBench（后续 E5 用）

https://github.com/Chenivan/SemanticCloneBench （或 clones.usask.ca 页面链接）
下载后解压到 `D:\work\SemanticCloneBench`。

## 4. BCB 抽取（先探结构，再全量）

```powershell
cd D:\work\Meng_ProjectBased_DetectCodeSimilar\code-sim

# 第一步：探明数据库表结构（把输出发给 Claude 核对后再继续）
python scripts\experiments\bcb_extract.py probe `
  --db D:\work\BigCloneEval\bigclonebenchdb\bcb `
  --h2 D:\work\BigCloneEval\libs\h2-1.3.176.jar

# 第二步：小样本试抽（每类 20 对），验证包装/路径/manifest
python scripts\experiments\bcb_extract.py extract `
  --db D:\work\BigCloneEval\bigclonebenchdb\bcb `
  --h2 D:\work\BigCloneEval\libs\h2-1.3.176.jar `
  --bcb D:\work\bcb_reduced --out results\bcb_smoke --per-type 20 --negatives 40

# 试跑（单进程）：
java -Xmx1g -cp "target/classes;$(Get-Content target/cp.txt)" `
  com.ziqi.codesim.next.semantic.eval.BatchPairMain `
  results\bcb_smoke\manifest.csv results\bcb_smoke\run.jsonl

# 评分：
python scripts\experiments\score_results.py `
  --results results\bcb_smoke\run.jsonl --labels results\bcb_smoke\labels.csv `
  --out results\bcb_smoke\scored

# 第三步：全量抽取（--per-type 0 = 每个语法类别全取）
python scripts\experiments\bcb_extract.py extract `
  --db ... --h2 ... --bcb D:\work\bcb_reduced `
  --out results\bcb_full --per-type 0 --negatives 2000
```

## 5. 并行全量运行

```powershell
# 8 分片 × 1 GB 堆 ≈ 峰值 10 GB 内存，16 GB 机器安全
python scripts\experiments\run_shards.py `
  --manifest results\bcb_full\manifest.csv `
  --out results\bcb_full\run --shards 8 --xmx 1g
```

- 断点续跑：中断后重复同一命令即可，已完成的对自动跳过。
- 进度：`Get-Content results\bcb_full\run\shard_00.log -Tail 5 -Wait`
- 跑完评分：`score_results.py --results results\bcb_full\run\merged.jsonl
  --labels results\bcb_full\labels.csv --out results\bcb_full\scored`

## 6. 完成后交回

把以下文件发回给 Claude 分析（或直接在共享文件夹里同步）：
`results\bcb_full\extraction_report.md`、`results\bcb_full\scored\summary.md`、
`results\bcb_full\scored\scored_pairs.csv`、任一分片 .log 的尾部 50 行。

---

### 故障速查

| 症状 | 处理 |
| --- | --- |
| `H2 query failed` | 数据库路径多写/漏写 `.h2.db` 后缀（参数里应不带后缀）；或版本不符，把 probe 输出发 Claude |
| OutOfMemoryError | `--xmx 1g` 提到 `2g` 并把 `--shards` 降到 5 |
| 大量 status=error | 正常预期一部分（片段编译失败→fallback），extraction_report 和 scored 汇总里有编译分诊统计；error 率 >50% 时发样本给 Claude |
| 磁盘不足 | bcb_full/pairs 会有几十万小文件，预留 ≥ 20 GB |
