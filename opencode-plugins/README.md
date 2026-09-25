# Playwright 卡住无法继续 — 排查手册(opencode 场景)

> **适用症状**:opencode 中调用 Playwright(通常经 playwright MCP 工具)后,工具调用永远不返回——agent 一直等待、无报错,只能手动 abort;浏览器可能开了也可能没开,页面始终打不开。
>
> **性质**:通用排查 runbook(可交给大模型自动执行)+ cdc 工程实战记录(待填)。
>
> **创建**:2026-09-25 | **状态**:文中引用的 opencode 侧 issue 截至当日均未官方解决,本文档汇总已验证机制与社区 workaround。

---

## 1. 问题背景

### 1.1 cdc 工程案例(待补全)

在另一台电脑上用 opencode + playwright 对 cdc 工程做自动化测试,playwright 无法打开指定页面,表现为 MCP 工具调用卡住、无法继续。证据收集完成后填入第 4 节。

### 1.2 社区已知问题(均为 Open,截至 2026-09-25)

> 注:opencode 仓库已由 `sst/opencode` 迁移至 `anomalyco/opencode`,旧链接自动重定向。

**opencode 侧,按相关度排序:**

| Issue | 平台 | 症状 | 与本问题的关系 |
|---|---|---|---|
| [#47546](https://github.com/anomalyco/opencode/issues/47546) ★ | **Windows 11** | bash 工具完成后留下存活的 detached 子进程(如 playwright 浏览器 daemon)→ subagent 循环在下一次 LLM 请求前卡死;TUI 永远显示"命令执行中",只能手动 abort,事后杀进程也无法恢复 | **最强匹配**:确定性复现,触发场景就是 Playwright;机制已被报告者实验定位(见 2.2 L2) |
| [#42191](https://github.com/anomalyco/opencode/issues/42191) | macOS | 本地 stdio MCP server(Python + Playwright)在 Desktop 托管下永久挂起,子进程根本没被创建;同一代码在终端跑完全正常 | Desktop(Electron)托管阻断孙进程创建 |
| [#50170](https://github.com/anomalyco/opencode/issues/50170) | Windows 11 | agent 运行不退出的命令(`bun run dev` 等)永远卡住;插件里 playwright MCP 为 Connected | "跑命令后卡住无法继续"的通用形态 |
| [#42270](https://github.com/anomalyco/opencode/issues/42270) | macOS | `service restart` 后旧 daemon 及 playwright MCP 子进程不被杀掉,出现双实例、泄漏 ~1.5GB | zombie 进程累积机制 |
| [#33028](https://github.com/anomalyco/opencode/issues/33028) / [#40468](https://github.com/anomalyco/opencode/issues/40468) / [#26220](https://github.com/anomalyco/opencode/issues/26220) | 跨平台 | 工具调用完成后 subagent 无限挂起 / busy 永久卡住 / 无限循环 | #47546 的 issue 家族,说明问题**不限 Windows** |
| [#50073](https://github.com/anomalyco/opencode/issues/50073) | Windows | MCP 工具(含 Playwright)不经过 plugin 的 tool.execute 钩子,`doom_loop` 防护对浏览器循环失效;`steps` 无默认上限 | 防护机制对 MCP 浏览器循环失效 |
| [#40829](https://github.com/anomalyco/opencode/issues/40829) | — | abort 会话不终止运行中的 bash 工具 | 解释"卡住后 abort 也救不回来"的体验 |

**playwright-mcp(microsoft/playwright-mcp)侧:**

| Issue | 平台/版本 | 症状 | Workaround |
|---|---|---|---|
| [#1540](https://github.com/microsoft/playwright-mcp/issues/1540)(Closed) | **Windows** | `npx` / `cmd.exe /c npx` 启动时 stdio 管道接不上,MCP **静默挂起**(Windows 上 npx 实为 npx.cmd 批处理包装) | **直接用 `node <绝对路径>/node_modules/@playwright/mcp/cli.js`** |
| [#1757](https://github.com/microsoft/playwright-mcp/issues/1757) | Windows 11, 0.0.81 | `--cdp-endpoint` 连真实 Chrome profile 时,任一被 Memory Saver 丢弃的后台 tab 都会让 `connectOverCDP` 挂 30s 超时,所有工具永远失败 | 关闭 Memory Saver / 用独立 `--user-data-dir` profile / 连接前激活各 tab |
| [#872](https://github.com/microsoft/playwright-mcp/issues/872)(Closed) | 0.0.33 | `browser_navigate` 无限挂起、浏览器窗口根本不出 | 升级版本 |
| [#881](https://github.com/microsoft/playwright-mcp/issues/881)(Closed) | DevContainers | v0.0.33 进程永久挂起 | 退回 v0.0.32 |
| [#1194](https://github.com/microsoft/playwright-mcp/issues/1194)(Closed) | WSL2 | `browser_install` 挂 3300+ 秒 | — |
| [#1254](https://github.com/microsoft/playwright-mcp/issues/1254)(Closed) | Windows + Edge headed | `browser_run_code` 无限挂起无输出 | — |
| [#1279](https://github.com/microsoft/playwright-mcp/issues/1279)(Closed) | — | navigate 触发 alert/dialog 时挂起 | 代码里预先注册 dialog handler |
| [#1732](https://github.com/microsoft/playwright-mcp/issues/1732) / [#1712](https://github.com/microsoft/playwright-mcp/issues/1712)(Closed) | — | `--extension` 模式选错 Chrome profile → 连接 promise 无超时永久挂起 | `PLAYWRIGHT_MCP_EXECUTABLE_PATH` 指向固定 profile |
| [#1458](https://github.com/microsoft/playwright-mcp/issues/1458)(Closed) | macOS | `playwright-cli close` 留下 headed Chrome zombie 进程 | 定期清理进程树 |
| [#1565](https://github.com/microsoft/playwright-mcp/issues/1565)(Closed) | — | 长时间运行(2–8 天)后无法再启动浏览器 | 重启宿主进程 |

中文社区(v2ex/掘金/CSDN/知乎)未找到完全匹配的公开帖。

---

## 2. 卡住的分层模型(分析核心)

"MCP 工具调用不返回"意味着调用链上**有一层断了**。整条链:

```
opencode agent ──stdio──▶ playwright MCP server 进程 ──launch/CDP──▶ 浏览器进程 ──网络/事件──▶ 目标页面
              L1 连接层                    L2 进程层                    L3 浏览器层              L4 导航层
```

### L1 连接层(opencode ↔ MCP server)

server 根本没起来,或 stdio 管道接不通。

- **特征**:**所有** MCP 工具(包括最简单的)都不返回;日志里 MCP server 标记 failed 或干脆无启动记录;甚至看不到 server 进程。
- **已知成因**:
  - Windows 上用 `npx` 启动 MCP → npx.cmd 批处理包装破坏 stdio 管道(playwright-mcp #1540)
  - Desktop(Electron)托管阻断孙进程创建 → server 永远不被 spawn(opencode #42191)
  - 多个本地 MCP 并发冷启动全部失败(opencode #48743)

### L2 进程层(MCP server 自身)

server 进程活着,但事件循环卡死,或被宿主的进程管理问题拖住。

- **特征**:server 进程存在,但对任何工具调用无响应;或出现重复实例;opencode 日志停在权限评估/工具调度处再无下文。
- **已知成因**:
  - **管道 EOF 机制**(opencode #47546,已被实验验证):bash 工具调用留下的存活 detached 后代进程(Playwright 浏览器 daemon 是最典型形态)继承了 server 的 stdout/stderr 管道 → server 等管道 EOF 永远等不到。日志签名:最后一行 `evaluated permission=bash ... action=allow` 之后彻底沉默
  - `service restart` 残留旧 daemon + 双 MCP 实例(opencode #42270)
  - 长时运行退化,只能重启宿主(playwright-mcp #1565)

### L3 浏览器层(server ↔ 浏览器)

server 响应正常,但浏览器起不来或连不上。

- **特征**:轻量工具(如 `browser_install`、快照/列表类)能返回,但 `browser_navigate` / 启动类工具不返回;浏览器窗口可能根本不出现。
- **已知成因**:
  - `--cdp-endpoint` 连真实 Chrome profile 时被 Memory Saver 丢弃的 tab 卡死 `connectOverCDP`(playwright-mcp #1757)
  - 浏览器未安装 / `browser_install` 挂起(#1194)
  - profile 被占用/选错(#1732/#1712)
  - zombie chrome 进程占用调试端口(#1458、#42270)

### L4 导航层(浏览器 ↔ 页面)

浏览器开了、标签页也有了,但 `page.goto` 挂起。

- **特征**:浏览器可见、地址栏有 URL、页面白屏或一直转圈。
- **已知成因**:
  - 页面弹出 alert/dialog 未处理,navigate promise 永不 resolve(#1279)
  - 网络:DNS 解析不出内网域名;代理问题(**Playwright 默认不走系统代理**——开了系统代理访问外网的机器上,要么设置 `HTTPS_PROXY`/`HTTP_PROXY` 环境变量让浏览器走代理,要么把内网域名加入 `NO_PROXY`;反过来系统代理劫持内网域名也会卡)
  - `waitUntil` 条件(load/networkidle)在该页面上永不满足

**判断口诀:由外向内,先用"最小工具"探测哪层还活着——L1 用最简单的 MCP 工具,L2 看进程与日志,L3 只启浏览器不导航,L4 换 URL 交叉验证。**

---

## 3. 诊断 runbook(给大模型执行)

> **执行约定**:每一步把证据(命令 + 输出摘要)记入第 4 节模板;遵守 3.4 的安全规则,**不要在诊断过程中自己触发卡死**。

### 阶段 0:证据收集(只读,不动任何东西)

| # | 收集项 | 方法 |
|---|---|---|
| 0.1 | opencode 版本与形态 | `opencode --version`;确认 CLI / TUI / Desktop 哪种形态;是否在 subagent 里跑 |
| 0.2 | playwright MCP 配置 | 读 opencode 配置文件里 playwright server 的 `command`(是 `npx` 还是 `node` 直启?)、包版本、`--browser`/`--cdp-endpoint`/`--extension` 等参数 |
| 0.3 | OS 与网络环境 | Windows/macOS/Linux;是否开了系统代理;目标 URL 类型(localhost/内网/公网/需登录) |
| 0.4 | 卡住时的进程快照 | Windows:`tasklist /FI "IMAGENAME eq node.exe"` + `... chrome.exe`;macOS/Linux:`pgrep -af "node|chrome|playwright"`。重点:有几个 playwright MCP 实例?有无 zombie chrome? |
| 0.5 | opencode 日志尾部 | 找日志文件(如 `~/.local/share/opencode/log/`);若最后一行是 `evaluated permission=bash ... action=allow` 后彻底沉默 → 直接指向 #47546 机制 |
| 0.6 | 复现路径 | 哪个工具调用卡住的、之前成功调用过哪些、卡住前 agent 做了什么(有没有跑过会留下后台进程的命令) |

### 阶段 1:分层探测(四个二分测试,由外向内)

**T1 — 探 L1(连接层)**:调用一个最小的 MCP 工具(如 `browser_install` 或 server 自带的列表/状态类工具),等 60 秒。
- 返回 → L1 通,进 T2
- 不返回 → **分支 A**(L1 断)

**T2 — 探 L3/L4(绕开 opencode,直接跑 Playwright 脚本)**:在终端(不是 opencode 会话里)写最小脚本:

```js
// minimal-probe.js — 显式超时,绝不裸跑
const { chromium } = require('playwright');
(async () => {
  const browser = await Promise.race([
    chromium.launch({ headless: false }),
    new Promise((_, rej) => setTimeout(() => rej(new Error('LAUNCH TIMEOUT 60s')), 60000)),
  ]);
  const page = await browser.newPage();
  await Promise.race([
    page.goto(process.env.TARGET_URL || 'https://example.com', { waitUntil: 'domcontentloaded', timeout: 60000 }),
    new Promise((_, rej) => setTimeout(() => rej(new Error('GOTO TIMEOUT 60s')), 65000)),
  ]);
  console.log('OK title=', await page.title());
  await browser.close();
  process.exit(0);
})().catch(e => { console.error('PROBE FAIL:', e.message); process.exit(1); });
```

运行:`TARGET_URL=<目标地址> node minimal-probe.js > probe.log 2>&1`(输出重定向到文件)。
- 成功(`OK title=...`)→ Playwright 本体没问题,**问题在 L1/L2(opencode 集成层)** → 分支 B
- `LAUNCH TIMEOUT` / 浏览器窗口没出 → **L3** → 分支 C
- `GOTO TIMEOUT` → **L4** → 分支 D
- 有其他明确报错(如 `net::ERR_*`)→ 按报错直接定位(连接拒绝=服务没起;NAME_NOT_RESOLVED=DNS;PROXY 相关=代理)

**T3 — 探 L3(只启浏览器,不导航)**:把 T2 脚本里 goto 换成 `page.goto('about:blank')`。
- about:blank 都挂 → 纯 L3(浏览器启动/连接问题)
- about:blank 通、真实 URL 挂 → L4(网络/页面问题)

**T4 — 探 L4(换 URL 交叉验证)**:分别试 `https://example.com`、目标内网 URL、目标 IP(绕过 DNS)。
- 公网通、内网不通 → DNS/代理/VPN 问题
- IP 通、域名不通 → DNS 问题
- 全不通 → 代理或网络层

### 阶段 2:按分支深入

**分支 A(L1 断——所有 MCP 工具都不返回)**:
1. 检查 MCP 配置的 `command`:Windows 上是 `npx` → **改为 `node <绝对路径>/node_modules/@playwright/mcp/cli.js` 直启**(#1540 的 workaround)
2. Desktop 形态 → 换终端 CLI 跑同一份 MCP 配置(#42191)
3. 看 opencode 日志里 MCP server 是否 failed / 根本无 spawn 记录(#48743)

**分支 B(L2——Playwright 本体正常,问题在 opencode 集成层)**:
1. 对照 0.4/0.5 的证据:是否有残留 playwright daemon / 双 MCP 实例 / 日志停在 `action=allow` → 命中 #47546 / #42270 机制
2. workaround(按机制反推):
   - **不要在 subagent 的 bash 调用里留下存活的后台进程**;浏览器操作尽量在 primary 会话跑
   - 必须留后台进程时,把其后代的 stdout/stderr **重定向到文件**而非继承管道(避开管道 EOF 死锁)
   - 重启前先杀干净:`taskkill /F /T /IM node.exe`(注意误伤范围)或按 PID 杀进程树
3. 给 agent 设显式 `steps` 上限,防 doom loop(#50073:默认无上限且 MCP 工具绕过防护钩子)

**分支 C(L3——浏览器起不来)**:
1. `--cdp-endpoint` 连真实 Chrome profile → 关闭 Memory Saver / 换独立 `--user-data-dir` profile / 连接前激活所有 tab(#1757)
2. 确认浏览器已安装:`npx playwright install chromium`(注意 #1194:这一步自己也可能挂,挂了看网络/WSL)
3. 清理 zombie chrome(占用调试端口):`taskkill /F /IM chrome.exe` 后重试(#1458/#42270)
4. `--extension` 模式检查 profile 选择(#1732)

**分支 D(L4——导航挂起)**:
1. 目标页面是否有 alert/dialog → 脚本里预先 `page.on('dialog', d => d.dismiss())`(#1279)
2. 代理:Playwright 默认**不走系统代理**——需要时设 `HTTPS_PROXY`/`HTTP_PROXY`;系统代理劫持内网域名时设 `NO_PROXY=<内网域名>`
3. DNS:内网域名解析不出 → 换 IP 直连验证;`waitUntil: 'networkidle'` 永不满足的页面改用 `domcontentloaded`

### 阶段 3:收敛与停止条件

- 定位到层并套用 workaround后,**重跑 T1–T4 全链验证**;四层全通且页面打开 → 结案,填第 4 节
- 证据收集完仍无法定位 → 填好第 4 节模板,带证据去对应 issue 跟进(#47546 最可能)或发新 issue

### 3.4 安全规则(诊断时避免自己触发卡死)

1. **一切长命令加显式超时**(脚本内 `Promise.race`,命令层 `timeout`)
2. **子进程输出一律重定向到文件**,不继承管道
3. **不在 subagent 会话里跑会留下浏览器 daemon 的命令**(已知确定触发器,#47546)
4. 每轮诊断结束清理进程树,不留后台 node/chrome

---

## 4. cdc 案例记录(待填模板)

> 在另一台电脑上按第 3 节 runbook 执行后,把结果填进来。

| 字段 | 值 | 采集日期 |
|---|---|---|
| opencode 版本 / 形态(CLI/TUI/Desktop) | | |
| 是否在 subagent 中触发 | | |
| playwright MCP 配置(command / 版本 / 参数) | | |
| OS / 是否开系统代理 | | |
| 目标 URL(类型即可:localhost/内网/公网) | | |
| 卡住的工具调用 | | |
| 卡住时进程快照(几个 node/chrome 实例) | | |
| opencode 日志最后一行 | | |
| T1 结果(最小 MCP 工具是否返回) | | |
| T2 结果(独立脚本:OK / LAUNCH TIMEOUT / GOTO TIMEOUT / 报错原文) | | |
| T3 结果(about:blank) | | |
| T4 结果(换 URL 交叉) | | |
| 定位到的层(L1/L2/L3/L4)与命中 issue | | |
| 生效的 workaround | | |
| 验证结果(重跑 T1–T4) | | |

---

## 5. 附录:投喂 prompt

把下面整段粘贴给 opencode(在那台电脑的**终端 CLI**里),让它自动执行阶段 0+1 并填表:

```
我遇到了"playwright MCP 工具调用卡住不返回"的问题。请严格按以下步骤排查,过程中遵守安全规则,不要留下任何后台进程:

【阶段 0:证据收集】
1. 记录你的版本和运行形态(CLI/TUI/Desktop),以及本次会话是否为 subagent
2. 读出 playwright MCP server 的完整配置(command、包版本、启动参数)
3. 执行 tasklist / pgrep,统计当前 node、chrome、playwright 相关进程及数量
4. 读出你的日志文件最后 30 行,原样记录
5. 描述复现路径:卡住的是哪个工具调用、之前成功过哪些

【阶段 1:分层探测】
T1. 调用最简单的 playwright MCP 工具(如 browser_install 或状态类工具),最多等 60 秒,记录是否返回
T2. 在终端写一个最小探测脚本:chromium.launch(headless:false) + page.goto(目标URL),两步都用 Promise.race 加 60 秒显式超时,stdout/stderr 重定向到 probe.log;运行后把 probe.log 内容告诉我
T3. 把 T2 的目标 URL 换成 about:blank 再跑一次,记录结果差异
T4. 把目标 URL 分别换成 https://example.com 和目标的裸 IP 地址各跑一次,记录哪些通哪些不通

【安全规则】
- 所有命令加超时;所有子进程输出重定向到文件
- 不要在 bash 调用里留下任何存活的后台进程;每步结束清理进程树
- 任何一步卡住超过 60 秒就停止,记录"该步超时"作为证据继续下一步

【输出】
把以上所有结果整理成一张表:字段包括 步骤/命令/结果/判读。最后根据结果判断卡住的层:
- 所有 MCP 工具都不返回 → L1(连接层)
- MCP 全挂但 T2 独立脚本成功 → L2(opencode 集成层,重点看日志是否停在 action=allow、有无残留 playwright daemon)
- T2 报 LAUNCH TIMEOUT 或浏览器不出 → L3(浏览器层)
- T2 报 GOTO TIMEOUT 或仅特定 URL 挂 → L4(导航层:代理/DNS/dialog)
只收集和判断,不要尝试修复;把表格给我,由我决定下一步。
```

---

## 参考 issue 索引

- opencode(迁移后仓库 `anomalyco/opencode`):[#47546](https://github.com/anomalyco/opencode/issues/47546) · [#42191](https://github.com/anomalyco/opencode/issues/42191) · [#50170](https://github.com/anomalyco/opencode/issues/50170) · [#42270](https://github.com/anomalyco/opencode/issues/42270) · [#33028](https://github.com/anomalyco/opencode/issues/33028) · [#40468](https://github.com/anomalyco/opencode/issues/40468) · [#26220](https://github.com/anomalyco/opencode/issues/26220) · [#50073](https://github.com/anomalyco/opencode/issues/50073) · [#40829](https://github.com/anomalyco/opencode/issues/40829) · [#48743](https://github.com/anomalyco/opencode/issues/48743) · [#50710](https://github.com/anomalyco/opencode/issues/50710) · [#34925](https://github.com/anomalyco/opencode/issues/34925) · [PR #7302](https://github.com/anomalyco/opencode/pull/7302)
- microsoft/playwright-mcp:[#1757](https://github.com/microsoft/playwright-mcp/issues/1757) · [#1540](https://github.com/microsoft/playwright-mcp/issues/1540) · [#872](https://github.com/microsoft/playwright-mcp/issues/872) · [#881](https://github.com/microsoft/playwright-mcp/issues/881) · [#1194](https://github.com/microsoft/playwright-mcp/issues/1194) · [#1254](https://github.com/microsoft/playwright-mcp/issues/1254) · [#1279](https://github.com/microsoft/playwright-mcp/issues/1279) · [#1293](https://github.com/microsoft/playwright-mcp/issues/1293) · [#1732](https://github.com/microsoft/playwright-mcp/issues/1732) · [#1712](https://github.com/microsoft/playwright-mcp/issues/1712) · [#1458](https://github.com/microsoft/playwright-mcp/issues/1458) · [#1565](https://github.com/microsoft/playwright-mcp/issues/1565)
