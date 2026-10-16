#!/usr/bin/env node
/**
 * gen-merged.mjs —— 把 qa/ 下的 6 个分册合并成单文件 `qa/合并版.md`
 *
 * ★ 用途：单文件传阅 / 导出 PDF（跨文件锚点在这些场景下会失效，合并后可内链跳转）
 * ★ 原则：合并版是**生成物**，不要手工改；改题请改分册，然后重跑本脚本
 *
 * 用法：
 *   node scripts/gen-merged.mjs            # 生成并写入 qa/合并版.md
 *   node scripts/gen-merged.mjs --dry-run  # 只打印统计，不写入
 *   node scripts/gen-merged.mjs -o xx.md   # 指定输出路径
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const QA = path.join(ROOT, 'qa');

const DRY = process.argv.includes('--dry-run');
const oIdx = process.argv.indexOf('-o');
const OUT = path.join(QA, oIdx >= 0 && process.argv[oIdx + 1] ? process.argv[oIdx + 1] : '合并版.md');

// ─── 分册定义（★ 新增分册时改这里，顺序即合并顺序）───
// nav 不硬编码：从分册册首导航行自动提取（"本册 **N 题**｜xxx"），
// 保证合并版的题数与分册始终一致。
const BOOKS = [
  { file: '01-H747-工业机柜.md',     title: '01 · STM32H747 工业机柜环境监护', nav: '本册 **32 题**｜项目一 `项目一/`' },
  { file: '02-SmartCam-智能相机.md', title: '02 · SmartCam Linux 智能相机',     nav: '本册 **17 题**｜项目二 `项目二/`' },
  { file: '03-K230-QEMU.md',         title: '03 · K230 SoC QEMU 外设建模',       nav: '本册 **2 题**｜项目三 `项目三/`' },
  { file: '04-计算机基础与调试.md',   title: '04 · 计算机基础与调试',            nav: '本册 **9 题**｜跨项目 / 通用' },
  { file: '05-八股与语言基础.md',     title: '05 · 八股与语言基础',              nav: '本册 **11 题**｜与三项目无强相关（含备用项目 + 纯八股）' },
  { file: '06-HR与行为面试.md',       title: '06 · HR 与行为面试',               nav: '本册 **18 题**｜所有技术岗通用（HR 面 / 行为面）' },
];

// 从分册原始文本里抓"本册 **N 题**｜…"这一行（作为 nav 的权威来源）
function extractNav(raw, fallback) {
  const m = raw.match(/^> .*?本册 \*\*\d+ 题\*\*.*$/m);
  if (m) return m[0].replace(/^>\s*\[↑ 返回总索引\][^｜]*｜\s*/, '');
  return fallback;
}

const ANCHOR = '<a id="top-of-qa"></a>';

// ─── 解析单个分册 ───
// 返回 { ids, body }
//   ids  : 该册的题号列表（按文件出现顺序），用于顶部「分册目录」
//   body : 清洗后的正文（删掉原始 H1 标题与「返回总索引」行，重挂到合并版 H1 下）
function parseBook(book) {
  const p = path.join(QA, book.file);
  if (!fs.existsSync(p)) {
    console.error('❌ 找不到分册：' + book.file);
    process.exit(1);
  }
  const raw = fs.readFileSync(p, 'utf8');
  const eol = raw.includes('\r\n') ? '\r\n' : '\n';
  const L = raw.split(/\r?\n/);

  const ids = [];
  const out = [];
  let headSkip = 0; // 跳过开头的 H1 + 「返回总索引」导航行
  for (let i = 0; i < L.length; i++) {
    const line = L[i];

    // 收集题号（本册目录表 + 正文 ### QN 都会命中，去重即可）
    const m = line.match(/^### (Q\d+)[：:]/) || line.match(/^\| \*\*(Q\d+)\*\* \|/);
    if (m && !ids.includes(m[1])) ids.push(m[1]);

    // 清洗册首导航：第 1 行是 "# 0N · xxx"（本册 H1），其后紧跟 "> [↑ 返回总索引]..."
    if (headSkip < 2) {
      if (line.startsWith('# ')) { headSkip++; continue; }
      if (line.startsWith('>') && /返回总索引/.test(line)) { headSkip++; continue; }
    }
    out.push(line);
  }

  // 去掉正文首尾的空行，避免多册拼接时堆叠
  while (out.length && out[0].trim() === '') out.shift();
  while (out.length && out[out.length - 1].trim() === '') out.pop();
  // 去掉正文末尾自带的分隔线（分隔由本脚本统一插入）
  while (out.length && /^-{3,}$/.test(out[out.length - 1].trim())) {
    out.pop();
    while (out.length && out[out.length - 1].trim() === '') out.pop();
  }

  return { ids, body: out.join(eol), eol, nav: extractNav(raw, book.nav) };
}

const parsed = BOOKS.map(b => ({ ...b, ...parseBook(b) }));
const eol = parsed[0].eol;

// ─── 拼装输出 ───
const parts = [];

parts.push('# 面试总复习（合并版）');
parts.push('');
parts.push('> **★ 本文件由 6 个分册合并生成**（`scripts/gen-merged.mjs`），用于"单文件传阅 / 导出 PDF"。');
parts.push('> **★ 日常维护请改 `qa/` 下的分册，不要改本文件**（会被覆盖）。');
parts.push('> 生成时间：' + new Date().toISOString().slice(0, 10));
parts.push('');
parts.push('---');
parts.push('');
parts.push('## 分册目录');
parts.push('');
// 题号清单：把 Q1、Q2 这类连号压缩成 Q1–Q3
function rangeList(ids) {
  const nums = ids.filter(x => /^Q\d+$/.test(x)).map(x => Number(x.slice(1))).sort((a, b) => a - b);
  const odd = ids.filter(x => !/^Q\d+$/.test(x)); // 如 Q66a，单列在后面
  const groups = [];
  for (let i = 0; i < nums.length; i++) {
    const s = nums[i];
    let e = s;
    while (i + 1 < nums.length && nums[i + 1] === e + 1) { e = nums[++i]; }
    groups.push(e - s >= 2 ? `Q${s}–Q${e}` : (e - s === 1 ? `Q${s}、Q${e}` : `Q${s}`));
  }
  return [...groups, ...odd].join('、');
}
parsed.forEach(b => {
  parts.push(`- **${b.title.replace(/^0\d · /, '')}**（${b.ids.filter(x => !/[a-z]$/.test(x)).length} 题）：${rangeList(b.ids)}`);
});
parts.push('');
parts.push('> **★ 本文件所有锚点均为册内锚点（如 `#qa5`）**，在合并版内可直接点击跳转。');
parts.push('> 但分册里的"返回总索引"指向 `../面试总复习.md#top-of-qa`，在合并版中请用页面顶部目录。');
parts.push('');
parts.push(ANCHOR);
parts.push('');
parts.push('---');

parsed.forEach(b => {
  parts.push('');
  parts.push('# ' + b.title);
  parts.push('');
  parts.push('> [↑ 返回总索引](#top-of-qa)｜' + b.nav);
  parts.push('');
  parts.push(b.body);
  parts.push('');
  parts.push('---');
  parts.push('');
});

// 收尾：规整分隔线
// ① 3 行以上空行 → 2 行
// ② 连续两个及以上的 `---`（分册自带 + 脚本追加）→ 只留 1 个
let text = parts.join(eol);
text = text.replace(new RegExp('(' + eol + '){3,}', 'g'), eol + eol);
text = text.replace(new RegExp('(?:(?:^|' + eol + ')---' + eol + ')+', 'g'), eol + '---' + eol);
text = text.replace(new RegExp('(' + eol + '){3,}', 'g'), eol + eol) + eol;

// ─── 统计 & 写出 ───
const totalQ = parsed.reduce((n, b) => n + b.ids.filter(x => !/[a-z]$/.test(x)).length, 0);
console.log('分册：' + parsed.length + ' 册');
parsed.forEach(b => console.log(
  '  ' + b.file + '  →  ' + b.ids.length + ' 题（' + b.ids.join('、') + '）'
));
console.log('合计 ' + totalQ + ' 题（含追加深挖小节另计 ' + parsed.reduce((n, b) => n + b.ids.filter(x => /[a-z]$/.test(x)).length, 0) + ' 个）');
console.log('输出体量 = ' + (Buffer.byteLength(text, 'utf8') / 1024).toFixed(1) + ' KB / ' + text.split(/\r?\n/).length + ' 行');

if (DRY) {
  console.log('--- DRY RUN，未写入 ---');
} else {
  fs.writeFileSync(OUT, text, 'utf8');
  console.log('已写入 ' + path.relative(ROOT, OUT));
}
