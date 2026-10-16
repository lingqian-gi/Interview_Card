#!/usr/bin/env node
/**
 * gen-index.mjs —— 从 qa/ 分册自动生成主文件的「按项目分布」「按主题分组」两张索引表
 *
 * ★ 设计目标：新增一道题时，只需：
 *   1. 在对应分册里加正文（含 <a id="qaN"></a> + <!-- meta: ... --> + ### QN：标题）
 *   2. 跑 `node scripts/gen-index.mjs`
 *   → 主文件的两张索引表自动更新（不再手工同步）
 *
 * 用法：
 *   node scripts/gen-index.mjs              # 生成并写入（含「本章目录表」）
 *   node scripts/gen-index.mjs --dry-run    # 只打印，不写入
 *
 * ★ 三块自动生成区：
 *   ① 「按项目分布」表     → <!-- AUTO-GEN:START --> 区
 *   ② 「按主题分组」表     → 同上区
 *   ③ 「本章目录表」        → <!-- TOC-GEN:START --> 区（★ 2026/10 改为极简表：
 *        按分册分组，每个题号跳该册的 #本册目录 —— 不再重复正文摘要）
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const QA = path.join(ROOT, 'qa');
const MAIN = path.join(ROOT, '面试总复习.md');

const DRY = process.argv.includes('--dry-run');

// ─── 分册定义（★ 新增分册时改这里）───
const BOOKS = [
  { file: '01-H747-工业机柜.md',     name: 'STM32H747 工业机柜' },
  { file: '02-SmartCam-智能相机.md', name: 'SmartCam 智能相机' },
  { file: '03-K230-QEMU.md',         name: 'K230 QEMU' },
  { file: '04-计算机基础与调试.md',   name: '计算机基础与调试' },
  { file: '05-八股与语言基础.md',     name: '八股与语言基础' },
  { file: '06-HR与行为面试.md',       name: 'HR 与行为面试' },
];

// ─── 解析分册：抽出每题的 id / 标题 / meta ───
function parseBook(file) {
  const p = path.join(QA, file);
  const L = fs.readFileSync(p, 'utf8').split(/\r?\n/);
  const items = [];
  for (let i = 0; i < L.length; i++) {
    const m = L[i].match(/^### (Q\d+)：(.*)$/);
    if (!m) continue;
    const id = m[1], title = m[2].trim();
    // 向上找锚点 + meta
    let anchor = null, meta = null;
    for (let j = i - 1; j >= Math.max(0, i - 4); j--) {
      const ma = L[j].match(/^<a id="(qa\d+[a-z]?)"><\/a>$/);
      if (ma && !anchor) anchor = ma[1];
      const mm = L[j].match(/^<!-- meta: (.*?) -->$/);
      if (mm && !meta) meta = mm[1];
    }
    // 解析 meta
    const kv = {};
    (meta || '').split(/\s+/).forEach(tok => {
      const [k, v] = tok.split('=');
      if (k && v !== undefined) kv[k] = v;
    });
    items.push({
      id, title, anchor: anchor || id.toLowerCase(),
      proj: kv.proj || '?', tags: (kv.tags || '').split(',').filter(Boolean),
      sub: Number(kv.sub || 0), book: file,
    });
  }
  return items;
}

const all = [];
for (const b of BOOKS) all.push(...parseBook(b.file));
all.sort((a, b) => Number(a.id.slice(1)) - Number(b.id.slice(1)));

// ─── 生成「按项目分布」───
// ★ 项目归类（可扩展）；'跨项目' / '八股' 单列
const PROJ_ORDER = ['H747', 'SmartCam', 'K230', '跨项目', '八股', 'HR'];
const projGroups = {};
all.forEach(it => {
  (projGroups[it.proj] = projGroups[it.proj] || []).push(it);
});
const projLabel = {
  H747: '**H747 工业机柜**', SmartCam: '**SmartCam**', K230: '**K230 QEMU**',
  跨项目: '**跨项目 / 通用**', 八股: '**★ 纯八股 / 语言基础**',
  HR: '**★ HR / 行为面试**',
};
const projLines = [];
projLines.push('**按项目分布**（★ 由 `scripts/gen-index.mjs` 从分册 meta 自动生成，勿手工改）：');
projLines.push('');
projLines.push('| 项目 | 题号 | 题数 | 所在分册 |');
projLines.push('|---|---|---|---|');
for (const k of PROJ_ORDER) {
  const g = projGroups[k];
  if (!g || !g.length) continue;
  const ids = g.map(x => `[${x.id}](./qa/${x.book}#${x.anchor})`).join('、');
  const book = [...new Set(g.map(x => x.book))];
  projLines.push(`| ${projLabel[k] || k} | ${ids} | **${g.length}** | ${book.map(f => '`' + f.replace(/\.md$/, '') + '`').join(' ')} |`);
}
projLines.push('');

// ─── 生成「按主题分组」───
const tagGroups = {};
all.forEach(it => {
  (it.tags.length ? it.tags : ['未分类']).forEach(t => {
    (tagGroups[t] = tagGroups[t] || []).push(it);
  });
});
// 按题数降序
const tagLines = [];
tagLines.push('**按主题分组**（★ 由 `scripts/gen-index.mjs` 从分册 meta 自动生成，勿手工改）：');
tagLines.push('');
tagLines.push('| 主题 | 题目 | 题数 |');
tagLines.push('|---|---|---|');
Object.entries(tagGroups)
  .sort((a, b) => b[1].length - a[1].length)
  .forEach(([tag, g]) => {
    const ids = g.map(x => `[${x.id}](./qa/${x.book}#${x.anchor})`).join('、');
    tagLines.push(`| **${tag}** | ${ids} | ${g.length} |`);
  });
tagLines.push('');

// ─── 写入主文件：替换 AUTO-GEN 标记区 ───
let main = fs.readFileSync(MAIN, 'utf8');
const eol = main.includes('\r\n') ? '\r\n' : '\n';

const START = '<!-- AUTO-GEN:START -->';
const END = '<!-- AUTO-GEN:END -->';
const block = [START, ...projLines, ...tagLines, END].join(eol);

let next;
if (main.includes(START) && main.includes(END)) {
  const re = new RegExp(START.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  next = main.replace(re, block);
  console.log('已更新现有 AUTO-GEN 区');
} else {
  // 首次：插到「## 4.6 分册导航」之前
  const idx = main.indexOf('## 4.6');
  if (idx < 0) { console.error('❌ 找不到 ## 4.6，无法插入'); process.exit(1); }
  next = main.slice(0, idx) + block + eol + eol + '---' + eol + eol + main.slice(idx);
  console.log('已插入新的 AUTO-GEN 区（在 ## 4.6 之前）');
}

console.log('');
console.log('解析到 ' + all.length + ' 题；项目组 ' + Object.keys(projGroups).length + ' 个；主题 ' + Object.keys(tagGroups).length + ' 个');

// ─── ★ 生成「本章目录表」（一行一题，按序号升序）───
// 列：题号 / 问题（标题） / 所属分册 / 跳转
// 跳转统一指向该题所属分册的 #本册目录（稳定大锚点），不直连题目锚点。
const TOC_START = '<!-- TOC-GEN:START -->';
const TOC_END = '<!-- TOC-GEN:END -->';

// 分册短名（书名 → 显示用）
const BOOK_SHORT = {
  '01-H747-工业机柜.md': '01 H747',
  '02-SmartCam-智能相机.md': '02 SmartCam',
  '03-K230-QEMU.md': '03 K230',
  '04-计算机基础与调试.md': '04 计算机基础',
  '05-八股与语言基础.md': '05 八股与语言',
  '06-HR与行为面试.md': '06 HR 与行为',
};

function buildTocTable() {
  const lines = [];
  lines.push('> [↑ 返回目录](#top-of-qa)｜**点「跳转」到该题所属分册的「本册目录」**，再定位到具体题；题目正文与摘要都在分册里。');
  lines.push('');
  lines.push('| 题号 | 问题 | 所属分册 | 跳转 |');
  lines.push('|---|---|---|---|');
  // all 已按题号升序排好
  for (const it of all) {
    const short = BOOK_SHORT[it.book] || it.book.replace(/\.md$/, '');
    lines.push(`| **${it.id}** | ${it.title} | ${short} | [→](./qa/${it.book}#本册目录) |`);
  }
  lines.push('');
  lines.push('> 共 **' + all.length + '** 题。各题正文见对应分册；题内「追加深挖小节」（如 Q66a）在所属题目下方。');
  lines.push('');
  return lines;
}

const tocBlock = [TOC_START, ...buildTocTable(), TOC_END].join(eol);

if (next.includes(TOC_START) && next.includes(TOC_END)) {
  const reToc = new RegExp(
    TOC_START.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + TOC_END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  );
  next = next.replace(reToc, tocBlock);
  console.log('「本章目录表」：✅ 已重新生成（' + all.length + ' 行，一行一题按序号）');
} else {
  console.log('「本章目录表」：⚠️ 未找到 ' + TOC_START + ' 标记（跳过生成）');
}

// ─── ★ 生成「4.6 分册导航表」（题数 + 体量按真实文件算）───
const NAV_START = '<!-- NAV-GEN:START -->';
const NAV_END = '<!-- NAV-GEN:END -->';
const NAV_META = {
  '01-H747-工业机柜.md': { title: '01 · STM32H747 工业机柜环境监护', desc: '项目一 `项目一/`' },
  '02-SmartCam-智能相机.md': { title: '02 · SmartCam Linux 智能相机', desc: '项目二 `项目二/`' },
  '03-K230-QEMU.md': { title: '03 · K230 SoC QEMU 外设建模', desc: '项目三 `项目三/`' },
  '04-计算机基础与调试.md': { title: '04 · 计算机基础与调试', desc: '跨三项目的通用基础' },
  '05-八股与语言基础.md': { title: '05 · 八股与语言基础', desc: '与三项目无强相关（含备用项目 + 纯八股）' },
  '06-HR与行为面试.md': { title: '06 · HR 与行为面试', desc: '所有技术岗通用（HR 面 / 行为面）' },
};

function buildNavTable() {
  const lines = [];
  lines.push('| 分册 | 内容 | 题数 | 体量 | 打开 |');
  lines.push('|---|---|---|---|---|');
  for (const b of BOOKS) {
    const items = all.filter(it => it.book === b.file);
    const kb = (Buffer.byteLength(fs.readFileSync(path.join(QA, b.file), 'utf8'), 'utf8') / 1024).toFixed(0);
    const m = NAV_META[b.file] || { title: b.file, desc: b.name };
    lines.push(`| **${m.title}** | ${m.desc} | **${items.length}** | ${kb} KB | [打开](./qa/${b.file}) |`);
  }
  return lines;
}

const navBlock = [NAV_START, ...buildNavTable(), NAV_END].join(eol);
if (next.includes(NAV_START) && next.includes(NAV_END)) {
  const reNav = new RegExp(
    NAV_START.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + NAV_END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  );
  next = next.replace(reNav, navBlock);
  console.log('「4.6 分册导航表」：✅ 已重新生成（' + BOOKS.length + ' 册，题数与体量按真实文件）');
} else {
  console.log('「4.6 分册导航表」：⚠️ 未找到 ' + NAV_START + ' 标记（跳过生成）');
}

// ─── ★ 自动修正「册首题数」（手工维护的行，历史上漂移过 4 次）───
// 每个分册第 3 行形如：> ...｜本册 **N 题**｜...
// 这里按真实解析出的题数回写，避免再次漂移。
function fixBookCounts() {
  const fixes = [];
  for (const b of BOOKS) {
    const p = path.join(QA, b.file);
    const raw = fs.readFileSync(p, 'utf8');
    const expect = all.filter(it => it.book === b.file).length;
    const m = raw.match(/｜本册 \*\*(\d+) 题\*\*｜/);
    if (!m) { fixes.push(`${b.file} 缺少头部标记`); continue; }
    if (Number(m[1]) !== expect) {
      const fixed = raw.replace(/｜本册 \*\*\d+ 题\*\*｜/, `｜本册 **${expect} 题**｜`);
      fs.writeFileSync(p, fixed, 'utf8');
      fixes.push(`${b.file} 册首题数 ${m[1]} → ${expect}`);
    }
  }
  return fixes;
}

const bookFixes = fixBookCounts();
if (bookFixes.length) {
  console.log('已自动修正册首题数：');
  bookFixes.forEach(f => console.log('   · ' + f));
}

// ─── ★ 自检（self-check）：校验三块生成区彼此一致，不一致则 fail-fast ───
// 背景：三块索引各自独立地从旧快照 main 做 replace，若某块标记被改坏或
//       读到旧快照，脚本会「静默跳过」并留下互相矛盾的索引区（曾发生 Q101/Q108）。
//       这里在写盘前统一校验，避免"表面成功、实际残缺"。
function selfCheck() {
  const problems = [];

  const total = all.length;

  // ① TOC-GEN 区：必须含全部题号 + 表尾题数一致
  const tocStartIdx = next.indexOf(TOC_START);
  const tocEndIdx = next.indexOf(TOC_END);
  if (tocStartIdx < 0 || tocEndIdx < 0) {
    problems.push('TOC-GEN 标记缺失（START/END 未同时找到）');
  } else {
    const tocText = next.slice(tocStartIdx, tocEndIdx);
    const tocIds = (tocText.match(/^\| \*\*(Q\d+)\*\* \|/gm) || []);
    if (tocIds.length !== total) {
      problems.push(`TOC-GEN 表行数 ${tocIds.length} ≠ 解析题数 ${total}`);
    }
    const countM = tocText.match(/共 \*\*(\d+)\*\* 题/);
    if (!countM) {
      problems.push('TOC-GEN 缺少「共 **N** 题」表尾');
    } else if (Number(countM[1]) !== total) {
      problems.push(`TOC-GEN 表尾题数 ${countM[1]} ≠ 解析题数 ${total}`);
    }
    const missing = all.filter(it => !tocText.includes('**' + it.id + '**')).map(it => it.id);
    if (missing.length) problems.push('TOC-GEN 缺题：' + missing.join('、'));
  }

  // ② AUTO-GEN 区：项目表题数合计 + 主题表题数合计 必须等于总题数
  const autoStartIdx = next.indexOf(START);
  const autoEndIdx = next.indexOf(END);
  if (autoStartIdx < 0 || autoEndIdx < 0) {
    problems.push('AUTO-GEN 标记缺失（START/END 未同时找到）');
  } else {
    const autoText = next.slice(autoStartIdx, autoEndIdx);
    // 项目表：| **标签** | ids | **N** | book |  → 取 **N**
    const projCounts = [...autoText.matchAll(/\| \*\*(\d+)\*\* \| `/g)].map(m => Number(m[1]));
    const projTotal = projCounts.reduce((a, b) => a + b, 0);
    if (projCounts.length && projTotal !== total) {
      problems.push(`AUTO-GEN 项目表题数合计 ${projTotal} ≠ 解析题数 ${total}`);
    }
    // 主题表：| **主题** | ids | N |  → 末列纯数字
    const tagBody = autoText.slice(autoText.indexOf('| 主题 |'));
    const tagCounts = [...tagBody.matchAll(/\| (\d+) \|$/gm)].map(m => Number(m[1]));
    const tagTotal = tagCounts.reduce((a, b) => a + b, 0);
    if (tagCounts.length && tagTotal !== total) {
      problems.push(`AUTO-GEN 主题表题数合计 ${tagTotal} ≠ 解析题数 ${total}（一题多主题属正常，仅当明显错位时排查）`);
    }
  }

  // ③ NAV-GEN 区：各分册题数合计 == 总题数，且每册行数 == 该册真实题数
  const navStartIdx = next.indexOf(NAV_START);
  const navEndIdx = next.indexOf(NAV_END);
  if (navStartIdx < 0 || navEndIdx < 0) {
    problems.push('NAV-GEN 标记缺失（START/END 未同时找到）');
  } else {
    const navText = next.slice(navStartIdx, navEndIdx);
    const navCounts = [...navText.matchAll(/\| \*\*(\d+)\*\* \| \d+ KB \|/g)].map(m => Number(m[1]));
    const navTotal = navCounts.reduce((a, b) => a + b, 0);
    if (navTotal !== total) {
      problems.push(`NAV-GEN 分册题数合计 ${navTotal} ≠ 解析题数 ${total}`);
    }
  }

  // ④ 跨块一致性：TOC 区块题号集合 必须与 AUTO-GEN 区块（本项目表）题号集合一致。
  //    这是真正的"块间矛盾"检测 —— 曾因 TOC 读到旧快照而缺 Q101/Q108。
  const tocStart2 = next.indexOf(TOC_START);
  const tocEnd2 = next.indexOf(TOC_END);
  const autoStart2 = next.indexOf(START);
  const autoEnd2 = next.indexOf(END);
  if (tocStart2 >= 0 && tocEnd2 >= 0 && autoStart2 >= 0 && autoEnd2 >= 0) {
    const tocText = next.slice(tocStart2, tocEnd2);
    const autoText = next.slice(autoStart2, autoEnd2);
    // TOC 区用 `| **Q2** |`；AUTO-GEN 区用 `[Q2](./...)` —— 两种格式都抽取。
    const idsIn = s => new Set([
      ...[...s.matchAll(/\*\*(Q\d+)\*\*/g)].map(m => m[1]),
      ...[...s.matchAll(/\[(Q\d+)\]\(\.\/qa\//g)].map(m => m[1]),
    ]);
    const tocSet = idsIn(tocText);
    const autoSet = idsIn(autoText);
    const onlyToc = [...tocSet].filter(x => !autoSet.has(x));
    const onlyAuto = [...autoSet].filter(x => !tocSet.has(x));
    if (onlyToc.length || onlyAuto.length) {
      problems.push(
        '块间不一致：' +
        (onlyToc.length ? `仅 TOC 有 [${onlyToc.join('、')}]；` : '') +
        (onlyAuto.length ? `仅 AUTO-GEN 有 [${onlyAuto.join('、')}]` : '')
      );
    }
  }

  // ⑤ ★ 册首题数校验：每个分册第 3 行的「本册 **N 题**」必须 == 该册真实题数。
  //    背景：这行是手工维护、不入任何生成区，历史上已漂移 4 次（Q101/Q108/Q109/Q122）。
  //    ★ 注意：脚本可自动修正它（见下方 fixBookCounts），此处仅做"修正后"的最终校验。
  for (const b of BOOKS) {
    const bookItems = all.filter(it => it.book === b.file);
    const expect = bookItems.length;
    const raw = fs.readFileSync(path.join(QA, b.file), 'utf8');
    const m = raw.match(/｜本册 \*\*(\d+) 题\*\*｜/);
    if (!m) {
      problems.push(`${b.file} 缺少「本册 **N 题**」头部标记`);
      continue;
    }
    if (Number(m[1]) !== expect) {
      problems.push(`${b.file} 册首题数 ${m[1]} ≠ 真实题数 ${expect}`);
    }
  }

  return problems;
}

const problems = selfCheck();
if (problems.length) {
  console.error('');
  console.error('❌ 自检未通过（共 ' + problems.length + ' 项）：');
  for (const p of problems) console.error('   · ' + p);
  console.error('');
  console.error('已中止写入，主文件保持原样。请检查分册 meta / 锚点 / 标记区后重跑。');
  process.exit(1);
}

if (DRY) {
  console.log('');
  console.log('✅ 自检通过（三块索引区一致，共 ' + all.length + ' 题）');
  console.log('--- DRY RUN，未写入 ---');
  console.log(block);
} else {
  fs.writeFileSync(MAIN, next, 'utf8');
  console.log('');
  console.log('✅ 自检通过（三块索引区一致，共 ' + all.length + ' 题）');
  console.log('主文件已更新 = ' + (Buffer.byteLength(next, 'utf8') / 1024).toFixed(1) + ' KB / ' + next.split(/\r?\n/).length + ' 行');
}
