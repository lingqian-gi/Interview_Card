#!/usr/bin/env node
/**
 * gen-cards.mjs —— 把 qa/ 下的分册 Markdown 解析成「卡片式 App」用的 cards.json
 *
 * ★ 设计目标：
 *   1. PC 侧是唯一数据源（Markdown），本脚本是"编译"步骤；
 *   2. 手机 App 只读 cards.json，不解析 Markdown、不联网；
 *   3. 卡片正面 = 问题；背面 = 一句话回答（兜底：一句话总结/开场定调/正文首段）。
 *
 * 用法：
 *   node scripts/gen-cards.mjs              # 生成 qa/cards.json 并打印统计
 *   node scripts/gen-cards.mjs --dry-run    # 只打印，不写文件
 *   node scripts/gen-cards.mjs -o xx.json   # 指定输出路径
 *
 * 输出结构见文件末尾的 schema 注释。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const QA = path.join(ROOT, 'qa');

const DRY = process.argv.includes('--dry-run');
const oIdx = process.argv.indexOf('-o');
// ★ 是否携带完整正文（默认不带，控制手机端体积；需要展开全文时加 --full）
const WITH_FULL = process.argv.includes('--full');
// --full 时默认写入 cards-full.json，避免覆盖轻量版
// --split 时按分册拆分输出到 qa/cards/ 目录
const WITH_SPLIT = process.argv.includes('--split');
const DEFAULT_NAME = WITH_FULL ? 'cards-full.json' : 'cards.json';
const OUT = path.join(QA, oIdx >= 0 && process.argv[oIdx + 1] ? process.argv[oIdx + 1] : DEFAULT_NAME);

// ─── 分册定义（与 gen-index.mjs 保持一致）───
const BOOKS = [
  { file: '01-H747-工业机柜.md',     id: '01', name: 'H747 工业机柜' },
  { file: '02-SmartCam-智能相机.md', id: '02', name: 'SmartCam 智能相机' },
  { file: '03-K230-QEMU.md',         id: '03', name: 'K230 QEMU' },
  { file: '04-计算机基础与调试.md',   id: '04', name: '计算机基础与调试' },
  { file: '05-八股与语言基础.md',     id: '05', name: '八股与语言基础' },
  { file: '06-HR与行为面试.md',       id: '06', name: 'HR 与行为面试' },
];

// ─── 工具函数 ───

/** 去掉 Markdown 行内标记，得到纯文本（用于卡片正反面短文本） */
function stripMd(s) {
  return s
    .replace(/`([^`]*)`/g, '$1')          // 行内代码
    .replace(/\*\*([^*]*)\*\*/g, '$1')    // 粗体
    .replace(/\*([^*]*)\*/g, '$1')        // 斜体
    .replace(/^\s*>\s?/gm, '')            // 引用符
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // 链接 → 文字
    .replace(/^["“”'']+/, '')             // ★ 去掉首部引号
    .replace(/["“”'']+$/, '')             // ★ 去掉尾部引号
    .replace(/[ \t]+/g, ' ')
    .trim();
}

/** 提取"一句话回答/总结"等短答案：兼容 **一句话回答** 与 #### 一句话回答 两种写法 */
function extractShortAnswer(lines, startIdx, endIdx) {
  // 优先级：一句话回答 > 一句话总结 > 开场定调 > 核心结论
  const KEYS = ['一句话回答', '一句话总结', '一句话开场', '一句话总纲', '开场定调', '一句话定调', '一句话概括', '核心结论'];
  for (const key of KEYS) {
    for (let i = startIdx; i < endIdx; i++) {
      const l = lines[i];
      // 命中"一句话回答/总结/开场/总纲"等标题行（兼容 **xx**、**xx：**、#### xx（…）写法）
      const hit =
        new RegExp(`^\\*\\*[^*]*${key}[^*]*\\*\\*`).test(l) ||
        new RegExp(`^\\*\\*\\s*${key}\\s*[：:]`).test(l) ||
        new RegExp(`^#{1,6}\\s*[^\\n]*${key}`).test(l);
      if (!hit) continue;

      // 收集该行 + 后续连续文本行（直到空行 / 下一个标题 / 元信息段）
      const buf = [];
      const inline = l
        .replace(/^\*\*[^*]*\*\*\s*[（(][^)）]*[)）]?\s*[：:]?/, '')  // **一句话回答**（…）：
        .replace(/^\*\*[^*]*\*\*\s*[：:]?/, '')                        // **一句话回答**：
        .replace(/^#{1,6}\s*[^\n]*(?:一句话?(回答|总结|开场|总纲|概括|定调)|开场定调|核心结论)[^\n]*?[（(][^)）]*[)）]\s*[：:]?/, '')
        .replace(/^#{1,6}\s*[^\n]*(?:一句话?(回答|总结|开场|总纲|概括|定调)|开场定调|核心结论)[^\n]*\s*[：:]?/, '')
        .replace(/^\*\*\s*(?:一句话?(开场|回答|总结|总纲|概括))\s*[：:]\s*\*\*/, '');  // **一句话开场：**
      if (inline.trim()) buf.push(inline);

      let inCode = false;
      let seenContent = false;            // ★ 是否已收集到正文（用于"标题后允许空行"）
      for (let j = i + 1; j < endIdx; j++) {
        const t = lines[j].trim();
        if (t === '') {
          if (seenContent) break;         // 已有内容 → 空行结束
          continue;                       // ★ 标题后紧跟的空行 → 跳过，继续找（引用块在下一行）
        }
        if (/^#{1,6}\s/.test(t)) break;                   // 下一个小标题
        if (/^---+$/.test(t)) break;                      // ★ 题目分隔线
        if (/^\*\*(关联|素材来源|诚实边界|关联题目|约束)\*\*/.test(t)) break;
        if (/^```/.test(t)) { inCode = !inCode; continue; }
        if (inCode) continue;
        if (/^\|/.test(t)) continue;                      // 表格行跳过
        if (/^<!--/.test(t)) continue;
        if (/^[│┌└├─┐┘┤┬┴┼]/.test(t)) continue;          // ASCII 图
        if (/^[-*+]\s/.test(t)) continue;                 // 列表项跳过
        if (/^<\/|^<a\s/.test(t)) continue;               // ★ HTML 锚点
        buf.push(lines[j]);
        if (stripMd(lines[j]).length > 0) seenContent = true;
      }
      const txt = stripMd(buf.join(' '));
      if (txt.length > 4) return txt.slice(0, 400);
    }
  }
  return '';
}

/** ★ 精简完整正文：只剥掉"App 内无意义的导航/锚点/失效链接"，保留复习内容（含诚实边界等） */
function cleanFullBody(body) {
  const out = [];
  let inCode = false;

  for (const rawLine of body.split('\n')) {
    const t = rawLine.trim();

    // 代码块整体保留（内部不做任何处理）
    if (/^```/.test(t)) { inCode = !inCode; out.push(rawLine); continue; }
    if (inCode) { out.push(rawLine); continue; }

    // ★ 只跳过"App 里没用"的行，其余全部保留
    if (/^>\s*\[↑/.test(t)) continue;                       // "> [↑ 本册目录]…" 导航行
    if (/^<a id=/.test(t)) continue;                        // HTML 锚点
    if (/^<!--/.test(t)) continue;                          // meta 注释
    if (/^---+$/.test(t)) continue;                         // 分隔线

    out.push(rawLine);
  }

  // 压掉 3 行以上连续空行
  let text = out.join('\n').replace(/\n{3,}/g, '\n\n').trim();
  // 清掉跨文件/页内链接（App 内点不动）→ 只留文字
  text = text.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');
  return text;
}

/** 兜底：取正文里第一段非元信息的普通文本 */
function extractFallback(lines, startIdx, endIdx) {
  let inCode = false;
  for (let i = startIdx; i < endIdx; i++) {
    const t = lines[i].trim();
    if (/^---+$/.test(t)) break;                       // ★ 题目结束（分隔线）
    if (!t) continue;
    if (/^```/.test(t)) { inCode = !inCode; continue; }
    if (inCode) continue;
    if (/^[>#|]/.test(t)) continue;                    // 引用/标题/表格
    if (/^<\/|<a\s/.test(t)) continue;                 // HTML 锚点/标签
    if (/^[│┌└├─┐┘┤┬┴┼]/.test(t)) continue;            // ASCII 图
    if (/^\*\*(关联|素材来源|诚实边界|关联题目|约束)\*\*/.test(t)) continue;
    if (/^<!--/.test(t)) continue;
    if (/^\*\*[^*]*一句话?(回答|总结|开场)[^*]*\*\*/.test(t)) continue;
    const txt = stripMd(t);
    if (txt.length < 15) continue;                     // 太短的不是摘要
    if (/^[①②③④⑤⑥⑦⑧⑨⑩]/.test(txt)) continue;
    if (/^(第\s*\d+\s*步|真实代码|细节\s*\d|用途说明|误区|话术)/.test(txt)) continue;
    return txt.slice(0, 400);
  }
  return '';
}

// ─── 解析单个分册 ───
function parseBook(book) {
  const p = path.join(QA, book.file);
  if (!fs.existsSync(p)) {
    console.error('❌ 找不到分册：' + book.file);
    process.exit(1);
  }
  const raw = fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n');
  const lines = raw.split('\n');

  // 找出所有题目起始行
  const heads = [];
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^### (Q\d+[a-z]?)[：:]\s*(.*)$/);
    if (m) heads.push({ line: i, id: m[1], title: m[2].trim() });
  }

  const cards = [];
  for (let h = 0; h < heads.length; h++) {
    const cur = heads[h];
    const bodyStart = cur.line + 1;
    const bodyEnd = (h + 1 < heads.length) ? heads[h + 1].line : lines.length;

    // 向上找 meta
    let meta = null;
    for (let j = cur.line - 1; j >= Math.max(0, cur.line - 4); j--) {
      const mm = lines[j].match(/^<!-- meta:\s*(.*?)\s*-->$/);
      if (mm) { meta = mm[1]; break; }
    }
    const kv = {};
    (meta || '').split(/\s+/).forEach(tok => {
      const [k, v] = tok.split('=');
      if (k && v !== undefined) kv[k] = v;
    });

    // 短答案：优先"一句话回答"，兜底"一句话总结/开场定调/正文首段"
    let short = extractShortAnswer(lines, bodyStart, bodyEnd);
    let source = '一句话回答';
    if (!short) { short = extractFallback(lines, bodyStart, bodyEnd); source = '正文兜底'; }

    // 完整正文（卡片"第二面展开"用）：从 bodyStart 到 bodyEnd，并精简
    const fullBody = cleanFullBody(lines.slice(bodyStart, bodyEnd).join('\n'));

    cards.push({
      id: cur.id,
      book: book.id,
      bookName: book.name,
      proj: kv.proj || '?',
      tags: (kv.tags || '').split(',').filter(Boolean),
      question: cur.title,
      answer: short,                 // ★ 卡片背面短答案
      answerSource: source,          // 来源标注（一句话回答 / 正文兜底）
      ...(WITH_FULL ? { answerFull: fullBody } : {}),  // ★ 完整正文（仅 --full 时）
      anchor: 'qa' + cur.id.replace(/^Q/, '').toLowerCase(),
    });
  }

  // ★ 按题号自然排序（数字序，Q2 < Q3 < ... < Q13 < Q128），
  //   避免"按文件出现顺序"造成的乱序（如 Q2, Q128, Q3…）
  cards.sort((a, b) => compareCardId(a.id, b.id));
  return cards;
}

/** 题号比较：按数字大小排序（Q66a 排在 Q66 之后） */
function compareCardId(a, b) {
  const na = parseInt(a.replace(/^Q/, ''), 10);
  const nb = parseInt(b.replace(/^Q/, ''), 10);
  if (na !== nb) return na - nb;
  return a.localeCompare(b);   // 同号（如 Q66 与 Q66a）按字符串
}

// ─── 主流程 ───
const allCards = [];
const bookStats = [];
for (const b of BOOKS) {
  const cards = parseBook(b);
  allCards.push(...cards);
  bookStats.push({ id: b.id, name: b.name, count: cards.length });
}

// ★ 全局按题号排序（"全部"视图 = Q1, Q2, Q3 … Q161，纯查号顺序）
allCards.sort((a, b) => compareCardId(a.id, b.id));

const noAnswer = allCards.filter(c => !c.answer || c.answer.length < 5);
const fallbackCount = allCards.filter(c => c.answerSource === '正文兜底').length;

const output = {
  version: new Date().toISOString().slice(0, 10),
  generatedAt: new Date().toISOString(),
  total: allCards.length,
  books: bookStats,
  cards: allCards,
};

const json = JSON.stringify(output, null, 2);

console.log('分册解析结果：');
bookStats.forEach(b => console.log(`  ${b.id} ${b.name.padEnd(18)} ${String(b.count).padStart(3)} 题`));
console.log(`合计 ${allCards.length} 题`);
console.log(`  · 有短答案：${allCards.length - noAnswer.length} 题（其中正文兜底 ${fallbackCount} 题）`);
if (noAnswer.length) {
  console.log(`  ⚠️ 无短答案（需人工检查）：${noAnswer.map(c => c.id).join('、')}`);
}

if (DRY) {
  console.log('\n--- DRY RUN，未写入 ---');
  console.log('样例（前 2 张）：');
  console.log(JSON.stringify(allCards.slice(0, 2), null, 2).slice(0, 1200));
} else if (WITH_SPLIT) {
  // ★ 按分册拆分：qa/cards/<bookId>.json + qa/cards/index.json
  const dir = path.join(QA, 'cards');
  fs.mkdirSync(dir, { recursive: true });
  let totalSplit = 0;
  for (const b of bookStats) {
    const cards = allCards.filter(c => c.book === b.id);
    const one = { book: b.id, bookName: b.name, count: cards.length, cards };
    const s = JSON.stringify(one, null, 2);
    fs.writeFileSync(path.join(dir, b.id + '.json'), s, 'utf8');
    totalSplit += Buffer.byteLength(s, 'utf8');
    console.log(`  qa/cards/${b.id}.json  ${cards.length} 题  ${(Buffer.byteLength(s, 'utf8') / 1024).toFixed(1)} KB`);
  }
  const idx = {
    version: output.version, generatedAt: output.generatedAt,
    total: allCards.length, books: bookStats,
  };
  fs.writeFileSync(path.join(dir, 'index.json'), JSON.stringify(idx, null, 2), 'utf8');
  console.log(`已拆分写入 qa/cards/ 共 ${bookStats.length} 个分册 + index.json，合计 ${(totalSplit / 1024).toFixed(1)} KB`);
} else {
  fs.writeFileSync(OUT, json, 'utf8');
  console.log(`\n已写入 ${path.relative(ROOT, OUT)} = ${(Buffer.byteLength(json, 'utf8') / 1024).toFixed(1)} KB`);
}

/**
 * cards.json Schema:
 * {
 *   version: string,            // 日期
 *   generatedAt: string,        // ISO 时间
 *   total: number,
 *   books: [{ id, name, count }],
 *   cards: [{
 *     id, book, bookName,       // 题号 / 分册
 *     proj, tags,               // meta 里的项目与标签（可做筛选）
 *     question,                 // ★ 卡片正面
 *     answer,                   // ★ 卡片背面（短答案，纯文本）
 *     answerSource,             // 短答案来源（一句话回答 / 正文兜底）
 *     answerFull,               // 完整正文（Markdown，可展开）
 *     anchor
 *   }]
 * }
 */
