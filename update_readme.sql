SET NAMES utf8mb4;
USE gamesaving;

UPDATE article SET
readme_raw = '## 🪨 58K一桶岩浆生炸刷石机 — 完整使用指南

### 存档概览

欢迎使用基于 **58K一桶岩浆** 的无限生炸刷石机存档！本存档适用于 Minecraft 1.21.10 版本，使用 Fabric 0.18.0 模组加载器，实现了全自动石料生产流水线。

| 项目 | 详情 |
|------|------|
| 游戏版本 | Minecraft 1.21.10 |
| 模组加载器 | Fabric 0.18.0 |
| 存档类型 | 原版生存 |
| 核心机制 | 58K桶岩浆 + TNT复制 |
| 产量 | ~18,000 石头/小时 |

---

### 工作原理

> **核心思路**：利用 58K 桶岩浆的无限热源驱动刷石机，配合 TNT 复制装置实现自动爆破采集，再通过水流收集系统将石料汇入仓库。

整个装置分为三个模块：

1. **无限岩浆源** — 使用 58K 桶岩浆实现
2. **自动刷石机** — 高效 TNT 复制刷石装置
3. **全自动收集系统** — 自动收集石料资源

---

### 装置结构展示

#### ① 游戏内刷石机全景

![刷石机全景](images/screenshot_game.png)

*上图：刷石机主体结构，包含岩浆源、水流通道和TNT爆破舱*

#### ② 红石控制面板

![红石控制面板](images/screenshot_tool.png)

*上图：刷石机红石控制电路，负责TNT复制节律和收集系统开关*

#### ③ 收集系统产出结果

![收集产出](images/screenshot_result.png)

*上图：全自动收集系统终端，展示石料产出和分类存储*

---

### 快速上手

#### 使用方法

1. 下载并解压存档文件
2. 将解压后的文件夹放入 `.minecraft/saves/` 目录
3. 使用 Fabric 0.18.0 启动游戏
4. 进入存档，输入 `/tp @p 128 70 128` 传送至刷石机
5. 拉下控制杆即可启动全自动生产

#### 控制程序 (C语言)

```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_STONE_PER_HOUR 18000
#define MAGMA_BUCKETS     58000

typedef struct {
    int x, y, z;
    int stone_count;
    int tnt_used;
    char status[32];
} CobbleGenerator;

void init_generator(CobbleGenerator *gen, int x, int y, int z) {
    gen->x = x;
    gen->y = y;
    gen->z = z;
    gen->stone_count = 0;
    gen->tnt_used = 0;
    strcpy(gen->status, "IDLE");
    printf("[初始化] 刷石机位置: (%d, %d, %d)\n", x, y, z);
}

void start_generator(CobbleGenerator *gen) {
    strcpy(gen->status, "RUNNING");
    printf("[启动] 刷石机开始运行...\n");
    printf("[配置] 岩浆桶数: %d\n", MAGMA_BUCKETS);
    printf("[目标] 预期产量: %d 石头/小时\n", MAX_STONE_PER_HOUR);
}

void produce_stone(CobbleGenerator *gen, int cycles) {
    for (int i = 1; i <= cycles; i++) {
        gen->stone_count += 64;
        gen->tnt_used += 1;
        printf("[周期 %d] 产出: 64个石头 | 累计: %d | TNT消耗: %d\n",
               i, gen->stone_count, gen->tnt_used);
    }
}

void stop_generator(CobbleGenerator *gen) {
    strcpy(gen->status, "STOPPED");
    printf("\n========== 生产报告 ==========\n");
    printf("总产量:   %d 个石头\n", gen->stone_count);
    printf("TNT用量: %d 个\n", gen->tnt_used);
    printf("效率:     %.1f 石头/TNT\n",
           (double)gen->stone_count / gen->tnt_used);
    printf("状态:     %s\n", gen->status);
    printf("================================\n");
}

int main() {
    printf("╔══════════════════════════════╗\n");
    printf("║  58K岩浆刷石机 - 控制程序 v1.0  ║\n");
    printf("╚══════════════════════════════╝\n\n");

    CobbleGenerator machine;
    init_generator(&machine, 128, 70, 128);
    start_generator(&machine);
    produce_stone(&machine, 10);
    stop_generator(&machine);
    return 0;
}
```

编译运行：
```bash
gcc -o cobble_gen cobble_generator.c -Wall -O2
./cobble_gen
```

预期输出：
```
╔══════════════════════════════╗
║  58K岩浆刷石机 - 控制程序 v1.0  ║
╚══════════════════════════════╝

[初始化] 刷石机位置: (128, 70, 128)
[启动] 刷石机开始运行...
[配置] 岩浆桶数: 58000
[目标] 预期产量: 18000 石头/小时
[周期 1] 产出: 64个石头 | 累计: 64 | TNT消耗: 1
[周期 2] 产出: 64个石头 | 累计: 128 | TNT消耗: 2
...
========== 生产报告 ==========
总产量:   640 个石头
TNT用量: 10 个
效率:     64.0 石头/TNT
状态:     STOPPED
================================
```

---

### 常见问题

- **Q: 启动后不刷石头？** A: 检查岩浆源是否完整，确保 58K 桶岩浆全部就位
- **Q: TNT 不复制？** A: 确认红石信号强度足够，检查 `tnt_dup` 模块的朝向
- **Q: 收集系统堵塞？** A: 清理水流通道中的卡住的掉落物

---

### 注意事项

> ⚠️ **重要**：请使用对应版本加载存档，跨版本可能导致存档损坏。建议备份原存档后再替换。
>
> ⚠️ 本装置为高性能刷石机，低配电脑可能出现 TPS 下降，建议分配至少 4GB 内存给 Minecraft。',

readme_content = '<h2>🪨 58K一桶岩浆生炸刷石机 — 完整使用指南</h2>
<h3>存档概览</h3>
<p>欢迎使用基于 <strong>58K一桶岩浆</strong> 的无限生炸刷石机存档！本存档适用于 Minecraft 1.21.10 版本，使用 Fabric 0.18.0 模组加载器，实现了全自动石料生产流水线。</p>
<table>
<thead><tr><th>项目</th><th>详情</th></tr></thead>
<tbody>
<tr><td>游戏版本</td><td>Minecraft 1.21.10</td></tr>
<tr><td>模组加载器</td><td>Fabric 0.18.0</td></tr>
<tr><td>存档类型</td><td>原版生存</td></tr>
<tr><td>核心机制</td><td>58K桶岩浆 + TNT复制</td></tr>
<tr><td>产量</td><td>~18,000 石头/小时</td></tr>
</tbody>
</table>
<hr>
<h3>工作原理</h3>
<blockquote><strong>核心思路</strong>：利用 58K 桶岩浆的无限热源驱动刷石机，配合 TNT 复制装置实现自动爆破采集，再通过水流收集系统将石料汇入仓库。</blockquote>
<p>整个装置分为三个模块：</p>
<ol>
<li><strong>无限岩浆源</strong> — 使用 58K 桶岩浆实现</li>
<li><strong>自动刷石机</strong> — 高效 TNT 复制刷石装置</li>
<li><strong>全自动收集系统</strong> — 自动收集石料资源</li>
</ol>
<hr>
<h3>装置结构展示</h3>
<h4>① 游戏内刷石机全景</h4>
<p><img src="images/screenshot_game.png" alt="刷石机全景"></p>
<p><em>上图：刷石机主体结构，包含岩浆源、水流通道和TNT爆破舱</em></p>
<h4>② 红石控制面板</h4>
<p><img src="images/screenshot_tool.png" alt="红石控制面板"></p>
<p><em>上图：刷石机红石控制电路，负责TNT复制节律和收集系统开关</em></p>
<h4>③ 收集系统产出结果</h4>
<p><img src="images/screenshot_result.png" alt="收集产出"></p>
<p><em>上图：全自动收集系统终端，展示石料产出和分类存储</em></p>
<hr>
<h3>快速上手</h3>
<h4>使用方法</h4>
<ol>
<li>下载并解压存档文件</li>
<li>将解压后的文件夹放入 <code>.minecraft/saves/</code> 目录</li>
<li>使用 Fabric 0.18.0 启动游戏</li>
<li>进入存档，输入 <code>/tp @p 128 70 128</code> 传送至刷石机</li>
<li>拉下控制杆即可启动全自动生产</li>
</ol>
<h4>控制程序 (C语言)</h4>
<pre><code class="language-c">#include &lt;stdio.h&gt;
#include &lt;stdlib.h&gt;
#include &lt;string.h&gt;

#define MAX_STONE_PER_HOUR 18000
#define MAGMA_BUCKETS     58000

typedef struct {
    int x, y, z;
    int stone_count;
    int tnt_used;
    char status[32];
} CobbleGenerator;

void init_generator(CobbleGenerator *gen, int x, int y, int z) {
    gen-&gt;x = x;
    gen-&gt;y = y;
    gen-&gt;z = z;
    gen-&gt;stone_count = 0;
    gen-&gt;tnt_used = 0;
    strcpy(gen-&gt;status, "IDLE");
    printf("[初始化] 刷石机位置: (%d, %d, %d)\n", x, y, z);
}

void start_generator(CobbleGenerator *gen) {
    strcpy(gen-&gt;status, "RUNNING");
    printf("[启动] 刷石机开始运行...\n");
    printf("[配置] 岩浆桶数: %d\n", MAGMA_BUCKETS);
    printf("[目标] 预期产量: %d 石头/小时\n", MAX_STONE_PER_HOUR);
}

void produce_stone(CobbleGenerator *gen, int cycles) {
    for (int i = 1; i &lt;= cycles; i++) {
        gen-&gt;stone_count += 64;
        gen-&gt;tnt_used += 1;
        printf("[周期 %d] 产出: 64个石头 | 累计: %d | TNT消耗: %d\n",
               i, gen-&gt;stone_count, gen-&gt;tnt_used);
    }
}

void stop_generator(CobbleGenerator *gen) {
    strcpy(gen-&gt;status, "STOPPED");
    printf("\n========== 生产报告 ==========\n");
    printf("总产量:   %d 个石头\n", gen-&gt;stone_count);
    printf("TNT用量: %d 个\n", gen-&gt;tnt_used);
    printf("效率:     %.1f 石头/TNT\n",
           (double)gen-&gt;stone_count / gen-&gt;tnt_used);
    printf("状态:     %s\n", gen-&gt;status);
    printf("================================\n");
}

int main() {
    printf("╔══════════════════════════════╗\n");
    printf("║  58K岩浆刷石机 - 控制程序 v1.0  ║\n");
    printf("╚══════════════════════════════╝\n\n");

    CobbleGenerator machine;
    init_generator(&amp;machine, 128, 70, 128);
    start_generator(&amp;machine);
    produce_stone(&amp;machine, 10);
    stop_generator(&amp;machine);
    return 0;
}</code></pre>
<p>编译运行：</p>
<pre><code class="language-bash">gcc -o cobble_gen cobble_generator.c -Wall -O2
./cobble_gen</code></pre>
<p>预期输出：</p>
<pre><code>╔══════════════════════════════╗
║  58K岩浆刷石机 - 控制程序 v1.0  ║
╚══════════════════════════════╝

[初始化] 刷石机位置: (128, 70, 128)
[启动] 刷石机开始运行...
[配置] 岩浆桶数: 58000
[目标] 预期产量: 18000 石头/小时
[周期 1] 产出: 64个石头 | 累计: 64 | TNT消耗: 1
[周期 2] 产出: 64个石头 | 累计: 128 | TNT消耗: 2
...
========== 生产报告 ==========
总产量:   640 个石头
TNT用量: 10 个
效率:     64.0 石头/TNT
状态:     STOPPED
================================</code></pre>
<hr>
<h3>常见问题</h3>
<ul>
<li><strong>Q: 启动后不刷石头？</strong> A: 检查岩浆源是否完整，确保 58K 桶岩浆全部就位</li>
<li><strong>Q: TNT 不复制？</strong> A: 确认红石信号强度足够，检查 <code>tnt_dup</code> 模块的朝向</li>
<li><strong>Q: 收集系统堵塞？</strong> A: 清理水流通道中的卡住的掉落物</li>
</ul>
<hr>
<h3>注意事项</h3>
<blockquote>⚠️ <strong>重要</strong>：请使用对应版本加载存档，跨版本可能导致存档损坏。建议备份原存档后再替换。<br><br>⚠️ 本装置为高性能刷石机，低配电脑可能出现 TPS 下降，建议分配至少 4GB 内存给 Minecraft。</blockquote>'
WHERE id = 1;

SELECT '=== README更新验证 ===' AS info;
SELECT
  id,
  title,
  CHAR_LENGTH(readme_raw) AS raw_chars,
  CHAR_LENGTH(readme_content) AS html_chars,
  status
FROM article WHERE id = 1;
