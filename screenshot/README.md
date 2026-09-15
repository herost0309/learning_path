# Browser Shot — 活动窗口截图工具

按全局热键（默认 `Ctrl+Alt+D`）截取**当前活动窗口**（通常是浏览器），自动保存为带时间戳的 PNG 到指定目录。

> 注：`Ctrl+Alt+S` 在本机已被其他软件占用（注册失败会启动报错），因此默认热键为 `Ctrl+Alt+D`。

- 单文件 Python 脚本，唯一依赖 Pillow（Win32 调用全部走 ctypes，无需 pywin32）
- 热键系统级注册，焦点在管理员权限应用里也能触发
- 支持多显示器（含副屏在主屏左侧的负坐标布局）

## 快速开始

```bash
pip install -r requirements.txt
python browser_shot.py
```

启动后打印当前配置，聚焦浏览器窗口按 `Ctrl+Alt+S`，截图即保存。

## 更换保存目录（不同工作阶段）

两种方式：

1. **改配置文件**：编辑同目录下 `config.json` 的 `save_dir`，重启脚本生效
2. **命令行临时覆盖**（不改配置文件）：

```bash
python browser_shot.py --dir "D:\work\phase2\shots"
```

`config.json` 首次运行自动生成：

```json
{
  "hotkey": "ctrl+alt+d",
  "save_dir": "C:\\Users\\ldd\\Pictures\\BrowserShots",
  "format": "png",
  "filename_pattern": "{timestamp}"
}
```

- `hotkey`：支持 `ctrl` / `alt` / `shift` / `win` 修饰键组合，主键支持单字母、数字、F1-F12，如 `"ctrl+alt+d"`、`"f8"`
- `format`：`png` 或 `jpg`

## 自定义文件名（filename_pattern）

支持以下占位符：

| 占位符 | 示例值 | 说明 |
|---|---|---|
| `{timestamp}` | `2026-09-15_20-49-17` | 日期_时间（默认） |
| `{date}` | `2026-09-15` | 日期 |
| `{time}` | `20-49-17` | 时间 |
| `{title}` | `哔哩哔哩_bilibili` | 窗口标题（自动去除文件名非法字符，最长 60 字符） |

示例：

- `"shot_{timestamp}"` → `shot_2026-09-15_20-49-17.png`
- `"{date}/{timestamp}"` → 按天分子文件夹：`2026-09-15\2026-09-15_20-49-17.png`
- `"{title}_{time}"` → `哔哩哔哩_bilibili_20-49-17.png`

同一秒内冲突仍自动追加 `_1`、`_2`；含不支持的占位符会在启动时报错退出。命令行也可临时覆盖：

```bash
python browser_shot.py --pattern "{title}_{time}"
```

## 临时换热键

```bash
python browser_shot.py --hotkey ctrl+alt+d
```

## 可选：打包成单文件 exe

```bash
pip install pyinstaller
pyinstaller --onefile --console browser_shot.py
```

生成的 `dist\browser_shot.exe` 可拷到任意机器运行（无需 Python 环境）。
开机自启：`Win+R` 输入 `shell:startup`，把 exe 的快捷方式放进去。

## 已知限制

- 截取的是**按热键时焦点所在**的窗口——想在别的窗口操作时截图，需先点一下目标窗口
- 前台窗口最小化时跳过本次捕获（会在控制台提示）
- 窗口被其他窗口遮挡的部分会拍到遮挡内容（活动窗口通常在最前，一般不受影响）
