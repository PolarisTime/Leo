#!/usr/bin/env python3
"""检查 shell 脚本里「调用了未定义的函数」。

为什么需要它：`bash -n` 只做语法检查，而调用未定义函数属于**运行时**错误。
本套件曾因一次脚本替换把函数定义头改写成了裸调用，导致 run.sh 对所有阶段
都不可用（`resolve_shared_token: command not found`），而 `bash -n` 完全无法发现。

实现要点：先构造「代码掩码」——引号内与注释内的字符一律不计入命令位置，
否则字符串里的 `|` 会被误当成管道符、`可选 a|b|c` 这类提示语会被误报。
（这正是本脚本第一版的假阳性来源。）

局限（有意为之，宁可漏报不误报）：
  引号内的 `$(...)` 内层命令不做检查，因此 `x="$(my_func)"` 这类调用检测不到。
  它面向的是「顶层直接调用未定义函数」这一主要场景。

用法：python3 check-undefined-funcs.py <脚本...>
退出码：0 无问题；1 发现疑似未定义调用。
"""
import re
import shutil
import sys

CMD_POS = re.compile(
    r'(?:^|[;&|]|\b(?:then|else|do|fi|done|\{|\()\s+)([A-Za-z_][A-Za-z0-9_]*)',
    re.MULTILINE,
)
DEF = re.compile(r'^\s*(?:function\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\{', re.MULTILINE)

KEYWORDS = {
    'if', 'then', 'else', 'elif', 'fi', 'for', 'while', 'until', 'do', 'done',
    'case', 'esac', 'function', 'return', 'local', 'export', 'declare', 'readonly',
    'set', 'unset', 'shift', 'break', 'continue', 'in', 'select', 'time', 'eval',
    'source', 'exit', 'trap', 'true', 'false', 'test', 'cd', 'echo', 'printf',
    'read', 'wait', 'kill', 'sleep', 'exec', 'umask', 'command', 'type', 'hash',
    'let', 'getopts', 'builtin', 'alias', 'ulimit', 'shopt', 'enable',
}


def code_mask(text: str) -> list[bool]:
    """返回与 text 等长的掩码：True 表示该位置是「代码」（非引号、非注释）。"""
    mask = [True] * len(text)
    in_single = in_double = in_comment = False
    i = 0
    while i < len(text):
        c = text[i]
        if in_comment:
            if c == '\n':
                in_comment = False
                i += 1
                continue
            mask[i] = False
            i += 1
            continue
        if c == '\\' and not in_single and i + 1 < len(text):
            mask[i] = mask[i + 1] = not (in_single or in_double)
            i += 2
            continue
        if c == "'" and not in_double:
            in_single = not in_single
            mask[i] = False
            i += 1
            continue
        if c == '"' and not in_single:
            in_double = not in_double
            mask[i] = False
            i += 1
            continue
        if c == '#' and not in_single and not in_double:
            in_comment = True
            mask[i] = False
            i += 1
            continue
        mask[i] = not (in_single or in_double)
        i += 1
    return mask


def check(path: str) -> list[str]:
    text = open(path, encoding='utf-8', errors='ignore').read()
    mask = code_mask(text)
    defined = set(DEF.findall(text))
    problems = []
    for m in CMD_POS.finditer(text):
        start, end = m.start(1), m.end(1)
        if not mask[start]:          # 引号/注释内，不是命令位置
            continue
        name = m.group(1)
        if name in defined or name in KEYWORDS:
            continue
        after = text[end:end + 40]
        if after.startswith('=') and not after.startswith('=='):
            continue                 # 变量赋值
        if re.match(r'\s*\)', after):
            continue                 # case 分支标签
        if shutil.which(name):
            continue                 # 同名外部命令
        problems.append(name)
    return sorted(set(problems))


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    failed = False
    for path in sys.argv[1:]:
        problems = check(path)
        if problems:
            failed = True
            print(f'❌ {path}: 疑似调用了未定义的函数 -> {", ".join(problems)}')
        else:
            print(f'✅ {path}: 未发现未定义的函数调用')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
