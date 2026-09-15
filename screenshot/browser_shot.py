# -*- coding: utf-8 -*-
r"""browser_shot.py — 全局热键截取当前活动窗口并自动保存。

用法:
    python browser_shot.py                 # 使用 config.json 中的配置
    python browser_shot.py --dir D:\shots  # 临时覆盖保存目录
    python browser_shot.py --hotkey ctrl+alt+d  # 临时覆盖热键
    python browser_shot.py --pattern "{title}_{time}"  # 临时覆盖文件名模板

按 Ctrl+C 退出。保存目录/热键也可直接改 config.json（重启生效）。
"""

import argparse
import ctypes
import ctypes.wintypes as wintypes
import json
import os
import re
import string
import sys
import time
from datetime import datetime
from pathlib import Path

from PIL import ImageGrab

# ---------------------------------------------------------------------------
# Win32 常量与初始化
# ---------------------------------------------------------------------------
WM_HOTKEY = 0x0312
MOD_ALT = 0x0001
MOD_CONTROL = 0x0002
MOD_SHIFT = 0x0004
MOD_WIN = 0x0008

SM_XVIRTUALSCREEN = 76
SM_YVIRTUALSCREEN = 77

HOTKEY_ID = 1

user32 = ctypes.windll.user32

MODIFIER_NAMES = {
    "ctrl": MOD_CONTROL,
    "control": MOD_CONTROL,
    "alt": MOD_ALT,
    "shift": MOD_SHIFT,
    "win": MOD_WIN,
}

SCRIPT_DIR = Path(__file__).resolve().parent
CONFIG_PATH = SCRIPT_DIR / "config.json"

DEFAULT_CONFIG = {
    "hotkey": "ctrl+alt+s",
    "save_dir": str(Path.home() / "Pictures" / "BrowserShots"),
    "format": "png",
    "filename_pattern": "{timestamp}",
}


def enable_dpi_awareness():
    """让进程成为 DPI 感知，避免高 DPI 屏幕上窗口坐标被虚拟化导致截图错位。"""
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)  # PER_MONITOR_DPI_AWARE
    except Exception:
        try:
            user32.SetProcessDPIAware()
        except Exception:
            pass  # 老系统也不支持就按默认跑


def parse_hotkey(hotkey_str):
    """'ctrl+alt+s' -> (MOD_CONTROL|MOD_ALT, vk_code)。解析失败抛 ValueError。"""
    parts = [p.strip().lower() for p in hotkey_str.split("+") if p.strip()]
    if not parts:
        raise ValueError("热键不能为空")

    modifiers = 0
    key = None
    for part in parts:
        if part in MODIFIER_NAMES:
            modifiers |= MODIFIER_NAMES[part]
        elif key is None:
            key = part
        else:
            raise ValueError(f"热键只能包含一个非修饰键: {hotkey_str}")

    if key is None:
        raise ValueError(f"热键缺少主键: {hotkey_str}")

    if len(key) == 1 and key.isalnum():
        return modifiers, ord(key.upper())
    if len(key) == 2 and key[0] == "f" and key[1:].isdigit():
        vk = 0x70 + int(key[1:]) - 1  # F1=0x70
        if 1 <= int(key[1:]) <= 12:
            return modifiers, vk
    raise ValueError(f"不支持的主键 '{key}'（支持单字母/数字/F1-F12）")


def get_window_title(hwnd):
    length = user32.GetWindowTextLengthW(hwnd)
    if length == 0:
        return ""
    buf = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buf, length + 1)
    return buf.value


def capture_active_window():
    """截取前台窗口，返回 (PIL.Image, 窗口标题)。"""
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        raise RuntimeError("无法获取前台窗口")

    if user32.IsIconic(hwnd):
        raise RuntimeError("前台窗口处于最小化状态，已跳过")

    title = get_window_title(hwnd)

    rect = wintypes.RECT()
    if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        raise RuntimeError("无法获取窗口矩形")

    # 抓整个虚拟屏再裁剪：多显示器（含负坐标副屏）下坐标才正确
    full = ImageGrab.grab(all_screens=True)
    offset_x = user32.GetSystemMetrics(SM_XVIRTUALSCREEN)
    offset_y = user32.GetSystemMetrics(SM_YVIRTUALSCREEN)
    box = (
        rect.left - offset_x,
        rect.top - offset_y,
        rect.right - offset_x,
        rect.bottom - offset_y,
    )
    return full.crop(box), title


FILENAME_FIELDS = {"timestamp", "date", "time", "title"}


def validate_filename_pattern(pattern):
    """检查文件名模板只包含支持的占位符，非法时抛 ValueError。"""
    try:
        parsed = list(string.Formatter().parse(pattern))
    except ValueError as exc:
        raise ValueError(f"文件名模板格式非法: {pattern}（{exc}）")
    # field 为 None 表示纯文本段；'' 或数字（如 {} / {0}）是位置占位符，一律视为非法
    unknown = {field for _, field, _, _ in parsed if field is not None} - FILENAME_FIELDS
    if unknown:
        raise ValueError(
            f"文件名模板包含不支持的占位符: {', '.join(map(repr, sorted(unknown)))}"
            f"（支持: {', '.join(sorted(FILENAME_FIELDS))}）"
        )


def sanitize_title(title):
    """窗口标题转成合法文件名片段：去 Windows 非法字符、截断、去首尾空白。"""
    title = re.sub(r'[\\/:*?"<>|\r\n\t]', "_", title)
    title = title.strip(" .")
    return title[:60] or "untitled"


def expand_filename(pattern, title):
    """按模板展开文件名（不含扩展名）。模板含 / 时会生成子目录路径。"""
    now = datetime.now()
    values = {
        "timestamp": now.strftime("%Y-%m-%d_%H-%M-%S"),
        "date": now.strftime("%Y-%m-%d"),
        "time": now.strftime("%H-%M-%S"),
        "title": sanitize_title(title),
    }
    return pattern.format(**values)


def build_filename(save_dir, pattern, title, ext):
    """按模板生成文件路径；同秒冲突时追加 _1、_2 …。"""
    base = expand_filename(pattern, title)
    candidate = Path(save_dir) / f"{base}.{ext}"
    counter = 0
    while candidate.exists():
        counter += 1
        candidate = Path(save_dir) / f"{base}_{counter}.{ext}"
    return candidate


def load_config():
    """读取 config.json；不存在则生成默认配置文件。"""
    if not CONFIG_PATH.exists():
        CONFIG_PATH.write_text(
            json.dumps(DEFAULT_CONFIG, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return dict(DEFAULT_CONFIG)
    try:
        with open(CONFIG_PATH, encoding="utf-8") as f:
            config = json.load(f)
    except (json.JSONDecodeError, OSError) as exc:
        print(f"[错误] 读取 {CONFIG_PATH.name} 失败: {exc}，使用默认配置")
        return dict(DEFAULT_CONFIG)
    merged = dict(DEFAULT_CONFIG)
    merged.update({k: v for k, v in config.items() if k in DEFAULT_CONFIG})
    return merged


def main():
    # 重定向/管道输出时强制 UTF-8 并按行刷新，避免中文日志乱码/缓冲不显示
    for stream in (sys.stdout, sys.stderr):
        if not stream.isatty():
            try:
                stream.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
            except Exception:
                pass

    parser = argparse.ArgumentParser(description="全局热键截取当前活动窗口")
    parser.add_argument("--dir", help="覆盖保存目录（优先于 config.json）")
    parser.add_argument("--hotkey", help="覆盖热键，如 ctrl+alt+s（优先于 config.json）")
    parser.add_argument("--pattern", help='覆盖文件名模板，如 "{title}_{time}"（优先于 config.json）')
    args = parser.parse_args()

    config = load_config()
    if args.dir:
        config["save_dir"] = args.dir
    if args.hotkey:
        config["hotkey"] = args.hotkey
    if args.pattern:
        config["filename_pattern"] = args.pattern

    try:
        modifiers, vk = parse_hotkey(config["hotkey"])
    except ValueError as exc:
        print(f"[错误] {exc}")
        sys.exit(1)

    try:
        validate_filename_pattern(config["filename_pattern"])
    except ValueError as exc:
        print(f"[错误] {exc}")
        sys.exit(1)

    enable_dpi_awareness()

    if not user32.RegisterHotKey(None, HOTKEY_ID, modifiers, vk):
        print(f"[错误] 注册热键 {config['hotkey']} 失败（可能已被其他程序占用）")
        sys.exit(1)

    save_dir = Path(config["save_dir"])
    ext = config["format"].lower().lstrip(".")
    pattern = config["filename_pattern"]

    print("=" * 56)
    print("  Browser Shot — 活动窗口截图工具")
    print(f"  热键    : {config['hotkey']}")
    print(f"  保存到  : {save_dir}")
    print(f"  格式    : {ext}")
    print(f"  文件名  : {pattern}（占位符: timestamp/date/time/title）")
    print("  退出    : Ctrl+C")
    print("=" * 56)

    msg = wintypes.MSG()
    try:
        while True:
            # 轮询而非阻塞 GetMessage，保证 Ctrl+C 能及时退出
            while user32.PeekMessageW(ctypes.byref(msg), None, 0, 0, 1):  # PM_REMOVE
                if msg.message == WM_HOTKEY and msg.wParam == HOTKEY_ID:
                    try:
                        img, title = capture_active_window()
                        path = build_filename(save_dir, pattern, title, ext)
                        path.parent.mkdir(parents=True, exist_ok=True)
                        img.save(path)
                        now = datetime.now().strftime("%H:%M:%S")
                        print(f'[{now}] 已保存: {path}（窗口: "{title}"）')
                    except Exception as exc:
                        print(f"[失败] {exc}")
            time.sleep(0.05)
    except KeyboardInterrupt:
        print("\n已退出")
    finally:
        user32.UnregisterHotKey(None, HOTKEY_ID)


if __name__ == "__main__":
    main()
