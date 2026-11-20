#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_merge.py —— LimboEmulatorPlus 多模块工程合并后的静态自检（不依赖 Android SDK）

检查项：
  1. 所有模块 src/main 下 XML 语法合法
  2. 同一文件内资源名重复（aapt2 会报 duplicate value，例如原 styles.xml 里两个 RoundedDialog）
  3. 所有 @color/@drawable/@style/@string/@layout/@anim 引用可解析
  4. Java 里 R.xxx.yyy 引用可解析；package 与目录一致；花括号配平
  5. Manifest 里声明的类文件存在

用法：
  python3 tools/check_merge.py [项目根目录]
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

VALUE_TAGS = {
    'string': 'string',
    'string-array': 'string',
    'plurals': 'string',
    'color': 'color',
    'style': 'style',
    'dimen': 'dimen',
    'bool': 'bool',
    'integer': 'integer',
}


def find_modules(root):
    mods = []
    for name in sorted(os.listdir(root)):
        res = os.path.join(root, name, 'src', 'main', 'res')
        if os.path.isdir(res):
            mods.append(name)
    return mods


def strip_line_comments(code):
    """去掉 // 行注释，但要避开字符串里的 //（例如 https:// 里的）"""
    out = []
    for line in code.splitlines(keepends=True):
        in_str = False
        cut = len(line)
        i = 0
        while i < len(line) - 1:
            c = line[i]
            if c == '"' and (i == 0 or line[i - 1] != '\\'):
                in_str = not in_str
            if c == '/' and line[i + 1] == '/' and not in_str:
                cut = i
                break
            i += 1
        nl = '\n' if line.endswith('\n') else ''
        out.append(line[:cut] + nl)
    return ''.join(out)


def collect_module(root, mod):
    """返回 (resources {kind:set(name)}, per-file named items for duplicate check)"""
    res_dir = os.path.join(root, mod, 'src', 'main', 'res')
    resources = {}
    file_items = []  # (file, kind, name)

    for dirpath, dirnames, filenames in os.walk(res_dir):
        folder = os.path.basename(dirpath)
        base = folder.split('-')[0]
        for fn in filenames:
            path = os.path.join(dirpath, fn)
            # 文件型资源：所有文件都算（png/xml/webp/jpg...）
            if base in ('drawable', 'layout', 'anim', 'menu', 'xml', 'mipmap'):
                rname = os.path.splitext(fn)[0]
                resources.setdefault(base, set()).add(rname)
                file_items.append((path, base, rname))
            if not fn.endswith('.xml'):
                continue
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue
            # values 里的具名资源
            if base == 'values':
                for el in tree.getroot().iter():
                    name = el.get('name')
                    if not name or el.tag not in VALUE_TAGS:
                        continue
                    kind = VALUE_TAGS[el.tag]
                    resources.setdefault(kind, set()).add(name)
                    file_items.append((path, kind, name))
                    if kind == 'style':
                        # R 里点号转下划线
                        resources['style'].add(name.replace('.', '_'))
    return resources, file_items, res_dir


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    mods = find_modules(root)
    if not mods:
        print('未找到含 src/main/res 的模块，检查路径: %s' % root)
        return 1

    problems = 0
    union = {}
    all_file_items = []

    for mod in mods:
        resources, file_items, res_dir = collect_module(root, mod)
        all_file_items.extend(file_items)
        for kind, names in resources.items():
            union.setdefault(kind, set()).update(names)

        # 1. XML 语法
        for dirpath, _, filenames in os.walk(os.path.join(root, mod, 'src', 'main')):
            if '/jni/' in dirpath:
                continue
            for fn in filenames:
                if fn.endswith('.xml'):
                    p = os.path.join(dirpath, fn)
                    try:
                        ET.parse(p)
                    except ET.ParseError as e:
                        print('[XML 错误] %s: %s' % (os.path.relpath(p, root), e))
                        problems += 1

        # 2. 同文件内重复资源名
        seen = {}
        for path, kind, name in file_items:
            key = (path, kind, name)
            if key in seen:
                print('[同文件重复定义] %s 里的 %s/%s' % (os.path.relpath(path, root), kind, name))
                problems += 1
            seen[key] = True

    # 3. 所有 XML 引用
    ref_pat = re.compile(r'@(string|color|drawable|style|layout|anim|dimen|menu|id)/([A-Za-z0-9_.]+)')
    # 框架 / AppCompat 自带样式，不在工程资源里
    framework_prefixes = ('Theme.AppCompat', 'Theme.Material', 'Theme.DeviceDefault',
                          'Widget.AppCompat', 'Widget.Material', 'TextAppearance.',
                          'Base.', 'android.', 'androidx.')
    for mod in mods:
        res_dir = os.path.join(root, mod, 'src', 'main', 'res')
        for dirpath, _, filenames in os.walk(res_dir):
            for fn in filenames:
                if not fn.endswith('.xml'):
                    continue
                path = os.path.join(dirpath, fn)
                with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                    text = f.read()
                for m in ref_pat.finditer(text):
                    kind, name = m.group(1), m.group(2)
                    if kind == 'id':
                        continue
                    if name.startswith(framework_prefixes):
                        continue
                    if name not in union.get(kind, set()):
                        print('[引用无法解析] @%s/%s ← %s' % (kind, name, os.path.relpath(path, root)))
                        problems += 1

    # 4. Java 检查
    # 负向后顾排除 android.R.xxx（框架资源）
    r_pat = re.compile(r'(?<!android\.)\bR\.(\w+)\.(\w+)')
    for mod in mods:
        java_base = os.path.join(root, mod, 'src', 'main', 'java')
        if not os.path.isdir(java_base):
            continue
        for dirpath, _, filenames in os.walk(java_base):
            for fn in filenames:
                if not fn.endswith('.java'):
                    continue
                path = os.path.join(dirpath, fn)
                with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                    code = f.read()

                # package 与路径
                rel = os.path.relpath(path, java_base)
                expect = os.path.dirname(rel).replace(os.sep, '.')
                m = re.search(r'^\s*package\s+([\w.]+);', code, re.M)
                if m and m.group(1) != expect:
                    print('[package 不一致] %s 声明 %s 应为 %s' % (os.path.relpath(path, root), m.group(1), expect))
                    problems += 1

                # 括号配平：先去块注释，再去行注释（字符串感知），再去字符串/字符
                stripped = re.sub(r'/\*.*?\*/', '', code, flags=re.S)
                stripped = strip_line_comments(stripped)
                stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', stripped)
                stripped = re.sub(r"'(?:[^'\\]|\\.)*'", "''", stripped)
                if stripped.count('{') != stripped.count('}'):
                    print('[花括号不配平] %s' % os.path.relpath(path, root))
                    problems += 1

                for m in r_pat.finditer(code):
                    kind, name = m.group(1), m.group(2)
                    if kind not in union:
                        continue
                    if name not in union[kind]:
                        print('[R 引用无法解析] R.%s.%s ← %s' % (kind, name, os.path.relpath(path, root)))
                        problems += 1

    # 5. Manifest 类路径（只检查 activity/service/receiver/provider/application 标签）
    check_tags = {'activity', 'service', 'receiver', 'provider', 'application'}
    for mod in mods:
        mf = os.path.join(root, mod, 'src', 'main', 'AndroidManifest.xml')
        if not os.path.isfile(mf):
            continue
        try:
            tree = ET.parse(mf)
        except ET.ParseError as e:
            print('[Manifest 语法错误] %s: %s' % (mod, e))
            problems += 1
            continue
        ns = '{http://schemas.android.com/apk/res/android}'
        for el in tree.getroot().iter():
            tag = el.tag.split('.')[-1]
            if tag not in check_tags:
                continue
            cls = el.get(ns + 'name')
            if not cls:
                continue
            # 相对类名（以 . 开头或无点）需要结合本模块包名，这里只检查全限定名
            if not cls.startswith(('com.', 'org.')):
                continue
            rel = cls.replace('.', os.sep) + '.java'
            found = any(
                os.path.isfile(os.path.join(root, mod2, 'src', 'main', 'java', rel))
                for mod2 in mods)
            if not found:
                print('[Manifest 类不存在] %s ← %s' % (cls, mod))
                problems += 1

    if problems == 0:
        print('自检通过：XML 语法、重复定义、资源引用、R 引用、package、括号、Manifest 类路径全部正常。')
        print('模块: %s' % ', '.join(mods))
        print('全局资源统计: ' + ', '.join('%s=%d' % (k, len(v)) for k, v in sorted(union.items())))
    else:
        print('共 %d 个问题。' % problems)
    return 0 if problems == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
