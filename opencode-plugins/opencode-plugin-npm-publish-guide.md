# 将 productivity 插件打包为 npm 包并在 OpenCode 中引用的步骤

> 目标:把 OpenCode 的 productivity 插件发布为 npm 包,在 `opencode.json` 的 `plugin` 数组中按包名引用。
> 参考文档: <https://opencode.ai/docs/plugins/>

## 一、初始化 npm 包

```bash
mkdir opencode-productivity
cd opencode-productivity
npm init -y
```

命名建议遵循社区惯例(如 `opencode-helicone-session`、`opencode-wakatime`):

- 公开包:`opencode-productivity`
- 作用域包:`@你的用户名/opencode-productivity`(推荐,不容易重名,且 OpenCode 的 `plugin` 数组明确支持 scoped 包)

## 二、目录结构与 package.json

```
opencode-productivity/
├── package.json
├── tsconfig.json
├── src/
│   └── index.ts        # 插件入口
└── dist/               # 构建产物(发布时只包含这个)
```

`package.json` 关键字段:

```json
{
  "name": "@your-scope/opencode-productivity",
  "version": "0.1.0",
  "type": "module",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "files": ["dist"],
  "scripts": {
    "build": "tsc -p ."
  },
  "dependencies": {
    "@opencode-ai/plugin": "*"
  },
  "keywords": ["opencode", "opencode-plugin", "productivity"]
}
```

要点:

- **`@opencode-ai/plugin` 放 dependencies**(而不是 devDependencies):插件里要 `import { tool } from "@opencode-ai/plugin"` 这类运行时工具,Bun 安装你的包时需要一并装上
- **`files: ["dist"]`** 避免把源码、node_modules 发上去
- `keywords` 里加 `opencode-plugin` 便于在生态列表里被检索到

> 捷径:OpenCode 用 Bun 加载插件,Bun 原生跑 TS,所以也可以不做构建、直接 `"main": "src/index.ts"` 发布源码。但编译成 JS + `.d.ts` 类型声明更稳妥通用。

## 三、入口文件骨架

一个插件模块可以导出**一个或多个**插件函数,每个函数接收 context、返回 hooks 对象:

```ts
import { type Plugin, tool } from "@opencode-ai/plugin"

export const Productivity: Plugin = async ({ project, client, $, directory, worktree }) => {
  return {
    event: async ({ event }) => {
      // 如: session.idle 时通知、统计时长等
    },
    "tool.execute.before": async (input, output) => {
      // 如: 拦截/改写工具调用
    },
    tool: {
      // teach: tool({ description: "...", args: {...}, execute: async (args, ctx) => {...} }),
    },
  }
}
```

## 四、发布前本地测试(推荐流程)

1. **先当本地插件开发**:把 `src/` 下的文件直接放进项目的 `.opencode/plugins/` 目录,OpenCode 启动时自动加载,改完即测
2. 如果本地插件用到了第三方依赖,在配置目录(`.opencode/` 或 `~/.config/opencode/`)放一个 `package.json` 声明,OpenCode 启动时会用 `bun install` 自动装好
3. 调试稳定后,把代码挪回 npm 包工程

## 五、发布

```bash
npm run build       # 生成 dist/
npm login
npm publish --access public   # scoped 包首次发布必须加 --access public
```

## 六、在 opencode.json 中引用

```json
{
  "$schema": "https://opencode.ai/config.json",
  "plugin": ["@your-scope/opencode-productivity"]
}
```

- 数组里可以 npm 包和本地插件混用,也可以列多个包
- 重启 OpenCode 后,Bun 会自动安装该包并加载,缓存位置在 `~/.cache/opencode/node_modules/`

## 七、验证与更新

1. 启动时无报错、hooks 生效即成功(可用 `client.app.log()` 在插件里打结构化日志辅助排查,别用 `console.log`)
2. 更新流程:改代码 → `version` 加版本号 → `npm publish` → OpenCode 下次启动拉新版本;如果发现本地缓存没刷新,删掉 `~/.cache/opencode/node_modules/` 里对应目录强制重装

## 注意事项

- `plugin` 数组只认包名(由 Bun 从 npm 安装),**不能填本地路径**——想引用本地代码就用第四步的 `.opencode/plugins/` 方式,这是两者唯一的切换点
- 插件加载顺序:全局配置 → 项目配置 → 全局插件目录(`~/.config/opencode/plugins/`)→ 项目插件目录(`.opencode/plugins/`),所有 hooks 按顺序执行
