# uix — 浏览器探索 CLI(无扩展版)

基于 Playwright 的本地浏览器探索/自动化 CLI,是 BrowserSkill 路线 2 的实现:
用**直连 CDP/Playwright** 替代浏览器扩展,保留其核心工作流(observe → 交互 → 诊断)。

## 架构

```
uix CLI ──HTTP(127.0.0.1 + token)──▶ uix daemon ──Playwright──▶ Chrome/Edge
                                       (常驻,持有浏览器)   (独立 profile: ~/.uix/profile)
```

- 浏览器使用专用持久 profile —— 登录一次,长期复用(Chrome 136+ 禁止默认
  profile 开调试端口,专用 profile 正是官方推荐做法)
- daemon 自动按 Chrome → Edge → 内置 Chromium 顺序探测可用浏览器
- 仅监听回环地址 + 随机 token,无扩展、无调试端口暴露

## 快速开始

```bash
cd uix
npm install            # 若机器无 Chrome/Edge,再执行: npx playwright install chromium
node bin/uix.js start  # 打开浏览器窗口(持久 profile)
node bin/uix.js navigate https://example.com
node bin/uix.js observe
node bin/uix.js click @e1
node bin/uix.js console --errors
node bin/uix.js network --failures
node bin/uix.js screenshot --full-page
node bin/uix.js stop
```

可选:`npm link` 后直接使用 `uix <command>`。

## 目标定位(target)规则

| 写法 | 含义 |
|---|---|
| `@e3` | observe 返回的元素 ref(导航后失效,需重新 observe) |
| `#id` / `css 选择器` | CSS 定位 |
| `text=登录` | 按可见文本定位 |

## 命令一览

| 命令 | 说明 |
|---|---|
| `start [--headless]` / `stop` / `status` | 浏览器与 daemon 生命周期 |
| `navigate <url>` / `back` / `forward` / `reload` / `url` | 导航 |
| `observe` | 语义化元素清单(角色/名称/状态)+ `@eN` refs |
| `click/fill/press/hover/select/scroll-to/wheel` | 交互(带自动等待) |
| `screenshot [--full-page] [--ref <t>] [--out f]` | 截图 |
| `console [--errors]` / `network [--failures]` | 环形缓冲区诊断(seq 游标增量读取) |
| `evaluate <js>` | 页面内执行 JS |
| `tabs` / `tab-new` / `tab-use` / `tab-close` | 标签页管理 |
| `wait <ms>` | 等待 |

所有命令支持 `--json` 获取原始 JSON(便于 agent 消费)。

## 与 BrowserSkill 的对应关系

| BrowserSkill | uix | 说明 |
|---|---|---|
| 扩展(chrome.debugger CDP) | Playwright launchPersistentContext | 去扩展化 |
| Agent Window 隔离 | 独立 profile 浏览器实例 | 登录态隔离但持久 |
| `observe` VOM + @eN refs | `observe` + @eN refs(简化 VOM) | 同一套心智模型 |
| 借用/归还用户标签页 | 不适用(独立浏览器) | 无打扰用户的场景 |
| request-help 人机协作 | 手动在浏览器窗口操作即可 | 浏览器可见,直接人工接管 |
| console/network 缓冲 | 相同(seq 游标模型) | 一致 |

## 已知限制

- observe 仅覆盖主文档(iframe/shadow DOM 内元素未枚举,可用 CSS/evaluate 兜底)
- ref 在导航后失效(与 BrowserSkill 一致),交互失败先 `observe` 刷新
- 每次交互默认 8s 超时,可用 `--timeout <ms>` 调整
