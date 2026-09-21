// Generate evidence/README.md from collected data.
import fs from "node:fs";

const rs = JSON.parse(fs.readFileSync("C:/software/cc/team/browser-skill/uix/tmp/collected.json", "utf8"));
const esc = (s) => String(s).replace(/\|/g, "\\|");
const diffCn = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };

const table = (list) =>
  "| 题号 | 题名 | 通过率 | 难度 | 点赞最高题解 | 作者 | 点赞 | 题解链接 |\n|---|---|---|---|---|---|---|---|\n" +
  list
    .map((r) => {
      const q = r.problem;
      const s = r.topSolution;
      return `| ${esc(q.frontendQuestionId)} | ${esc(q.titleCn || q.title)} | ${(q.acRate * 100).toFixed(1)}% | ${diffCn[q.difficulty]} | ${esc(s.title)}${s.official ? "(官方)" : ""} | ${esc(s.author)} | ${s.likes} | [查看](https://leetcode.cn${s.href}) |`;
    })
    .join("\n");

const top = rs.filter((r) => r.problem.group === "top");
const bottom = rs.filter((r) => r.problem.group === "bottom");

const md = `# LeetCode CN 探索证据 —— 通过率最高/最低 10 题 · UI 一致性检查 · 点赞最高题解

> 采集时间:2026-09-21 · 工具:uix(Playwright 直连,无浏览器扩展)· 数据源:leetcode.cn GraphQL 题库全量 ${JSON.parse(fs.readFileSync("C:/software/cc/team/browser-skill/uix/tmp/all-problems.json", "utf8")) && ""}4447 题(已排除会员题)

## 文件索引

- \`ui-differences.png\` / \`ui-differences.html\` —— 20 题 UI 一致性检查矩阵 + 关键发现
- \`<题号>-<题名>/01-problem.png\` —— 题目描述页截图(标题/难度/描述/编辑器)
- \`<题号>-<题名>/02-top-solution.png\` —— 该题点赞最高题解页截图
- \`<题号>-<题名>/meta.json\` —— 结构化数据(UI 检查结果、题解排名、错误样本)

## UI 一致性结论(详见 ui-differences.png)

1. **核心 UI 20/20 一致**:标题、难度标签、Monaco 编辑器、通过率展示、题解/讨论/提交记录 Tab 均正常;**全部 20 页 console JS 错误为 0**。
2. **差异①**:\`3617 查找具有螺旋学习模式的学生\` 是唯一缺失「提示」区的题目页。
3. **差异②**:仅 2879/2884/2881/3614 四题(数据库/Pandas 新题型)显示「解决方案」Tab,属题型差异而非故障。
4. **网络层**:每页 0~3 个失败请求;24 次为 Google Analytics 被网络拦截(非站点问题),6 次为 assets.leetcode.cn 用户头像加载失败(轻微资源缺陷)。

## 通过率最高 10 题(及各自点赞最高的题解)

${table(top)}

## 通过率最低 10 题(及各自点赞最高的题解)

${table(bottom)}

## 说明

- 题解排名方式:打开每题的题解列表页,滚动加载后提取全部卡片(fa-triangle 图标计为点赞数),取点赞数最大者;官方题解若在列表中展示点赞数则一并参与排名。
- 截图 \\\`02-top-solution.png\\\` 为该题解详情页顶部(含标题/作者/正文开头);点赞数以列表卡片数据为准,记录于各 meta.json。
`;

fs.writeFileSync("C:/software/cc/team/browser-skill/evidence/README.md", md);
console.log("README written:", md.length, "chars");