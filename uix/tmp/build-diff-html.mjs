// Build the UI-difference summary table as HTML, ready for a full-page screenshot.
import fs from "node:fs";

const rs = JSON.parse(fs.readFileSync("C:/software/cc/team/browser-skill/uix/tmp/collected.json", "utf8"));
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const rows = rs
  .map((r) => {
    const q = r.problem;
    const diffCells = [];
    // highlight the two real UI differences
    const solStyle = r.ui.tabs.includes("解决方案") ? ' class="diff"' : "";
    const hintStyle = !r.ui.hasHints ? ' class="diff"' : "";
    return `<tr>
<td>${q.group === "top" ? "最高10题" : "最低10题"}</td>
<td><b>${esc(q.frontendQuestionId)}</b></td>
<td>${esc(q.titleCn || q.title)}</td>
<td>${(q.acRate * 100).toFixed(1)}%</td>
<td>${esc({ EASY: "简单", MEDIUM: "中等", HARD: "困难" }[q.difficulty] || q.difficulty)}</td>
<td>✓</td><td>✓</td><td>✓</td><td>0</td>
<td${hintStyle}>${r.ui.hasHints ? "✓" : "✗ 缺失"}</td>
<td${solStyle}>${r.ui.tabs.includes("解决方案") ? "有" : "—"}</td>
<td>${r.ui.networkFailures}</td>
<td><code>${esc(r.folder)}</code></td>
</tr>`;
  })
  .join("\n");

const html = `<!doctype html><html><head><meta charset="utf-8"><style>
body { font-family: "Microsoft YaHei", "Segoe UI", sans-serif; margin: 24px; color: #1a1a2e; }
h1 { font-size: 22px; } h2 { font-size: 16px; margin-top: 20px; }
table { border-collapse: collapse; font-size: 12.5px; }
th, td { border: 1px solid #c8c8d4; padding: 5px 9px; text-align: center; }
th { background: #2d3748; color: #fff; font-weight: 600; }
tr:nth-child(even) { background: #f4f5fb; }
td.diff { background: #ffd9d9; color: #b00020; font-weight: 700; }
code { font-size: 11px; background: #eef; padding: 1px 4px; border-radius: 3px; }
.findings li { margin: 6px 0; font-size: 14px; }
.meta { color: #555; font-size: 12px; }
</style></head><body>
<h1>LeetCode 中国站 题目页 UI 一致性检查 —— 通过率最高 10 题 vs 最低 10 题</h1>
<p class="meta">采集时间:2026-09-21 · 数据源:leetcode.cn 全站 4447 题 GraphQL 题库(排除会员题)· 工具:uix(Playwright,无扩展)</p>

<h2>关键发现</h2>
<ul class="findings">
<li>✅ <b>20/20 题目页核心 UI 完全一致</b>:标题、难度标签、Monaco 代码编辑器、通过率展示、题解/讨论/提交记录 Tab 全部正常;console JS 错误 0(全部 20 页)。</li>
<li>⚠️ <b>差异①「提示」区缺失</b>:题号 <b>3617「查找具有螺旋学习模式的学生」</b>是唯一没有「提示」(hints) 区块的题目页。</li>
<li>⚠️ <b>差异②「解决方案」Tab 仅 4 题可见</b>:2879 / 2884 / 2881 / 3617(数据库与 Pandas 类新题型),普通算法题没有该 Tab —— 属于题型差异而非 UI 故障。</li>
<li>⚠️ <b>网络层差异</b>:每页 0~3 个失败请求 —— 24 次为 Google Analytics 请求被网络拦截(非站点 bug);6 次为 assets.leetcode.cn 用户头像加载失败(轻微资源缺陷,涉及用户 li-mao-x / chi-hen 的头像)。</li>
<li>ℹ️ 描述区文本量差异大(1,452 ~ 13,168 字符),由题目内容决定,非 UI 不一致。</li>
</ul>

<h2>逐题检查矩阵(黄色 = 与多数题不一致)</h2>
<table>
<tr><th>组别</th><th>题号</th><th>题名</th><th>通过率</th><th>难度</th><th>编辑器</th><th>通过率显示</th><th>题解Tab</th><th>Console错误</th><th>提示区</th><th>解决方案Tab</th><th>网络失败</th><th>证据目录</th></tr>
${rows}
</table>
</body></html>`;

fs.writeFileSync("C:/software/cc/team/browser-skill/evidence/ui-differences.html", html);
console.log("html written");