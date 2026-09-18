// Divine & Beast —— 三个中立 boss 的材质生成器（开发工具，不参与打包）
//
// 用法： node tools/gen_textures.js
// 产出： src/main/resources/assets/divinebeast/textures/ 下的方块 / 锭 / 剑 / 盔甲图标 / 盔甲模型层
//       另在 assets/minecraft/textures/models/armor/ 放一份同名副本，兼容"按 material.getName() 取贴图"的旧路径。
//
// 只依赖 Node 内置 zlib，自己写 PNG 编码（无第三方库）。

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets');

// ---------------------------------------------------------------- PNG 编码
const CRC_TABLE = (() => {
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c;
    }
    return table;
})();

function crc32(buf) {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body), 0);
    return Buffer.concat([len, body, crc]);
}

function encodePng(width, height, rgba) {
    const stride = width * 4;
    const raw = Buffer.alloc((stride + 1) * height);
    for (let y = 0; y < height; y++) {
        raw[y * (stride + 1)] = 0; // filter: none
        rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
    }
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8;  // bit depth
    ihdr[9] = 6;  // color type RGBA
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
        chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]);
}

// ---------------------------------------------------------------- 画布
class Canvas {
    constructor(w, h) {
        this.w = w;
        this.h = h;
        this.px = Buffer.alloc(w * h * 4); // 全透明
    }

    set(x, y, c, a = 255) {
        if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
        const i = (y * this.w + x) * 4;
        this.px[i] = c[0];
        this.px[i + 1] = c[1];
        this.px[i + 2] = c[2];
        this.px[i + 3] = a;
    }

    fill(x0, y0, x1, y1, c, a = 255) {
        for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) this.set(x, y, c, a);
    }

    /** 按字符画填充：'X' 用主色，'o' 用亮色，'.' 透明 */
    drawMask(rows, colors, offsetX = 0, offsetY = 0) {
        rows.forEach((row, y) => {
            for (let x = 0; x < row.length; x++) {
                const ch = row[x];
                if (ch === '.' || ch === ' ') continue;
                const c = colors[ch];
                if (c) this.set(x + offsetX, y + offsetY, c);
            }
        });
    }

    save(file) {
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, encodePng(this.w, this.h, this.px));
    }
}

const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const shade = (c, t) => (t >= 0 ? mix(c, [255, 255, 255], t) : mix(c, [0, 0, 0], -t));
/** 确定性噪声：同一个 (x,y) 永远得到同一个值，方便复现 */
const noise = (x, y, seed) => {
    let h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791);
    h = (h ^ (h >>> 13)) * 1274126177;
    return ((h ^ (h >>> 16)) >>> 0) % 1000 / 1000;
};

// ---------------------------------------------------------------- 三个主题
const THEMES = {
    // 『我』：主世界泥土 + 绿宝石
    self: {
        blockBase: [122, 90, 56], blockDark: [92, 66, 40], blockLight: [150, 114, 74],
        emblem: [63, 217, 138], emblemDark: [24, 130, 78],
        metal: [201, 210, 218], metalLight: [242, 248, 252], metalDark: [124, 134, 145],
        accent: [63, 217, 138], accentDark: [24, 130, 78],
        emblemMask: [
            '..#..',
            '.###.',
            '#####',
            '..#..',
            '.###.',
        ],
    },
    // 『兽』：地狱下界岩 + 金
    beast: {
        blockBase: [122, 43, 34], blockDark: [84, 28, 22], blockLight: [156, 58, 44],
        emblem: [242, 178, 51], emblemDark: [163, 108, 20],
        metal: [232, 185, 60], metalLight: [255, 240, 168], metalDark: [150, 108, 24],
        accent: [192, 57, 43], accentDark: [124, 34, 25],
        emblemMask: [
            '#...#',
            '.#.#.',
            '..#..',
            '.#.#.',
            '#...#',
        ],
    },
    // 『祂』：末地石 + 虚空紫
    he: {
        blockBase: [217, 213, 168], blockDark: [178, 174, 132], blockLight: [240, 237, 198],
        emblem: [185, 140, 255], emblemDark: [111, 69, 184],
        metal: [75, 70, 97], metalLight: [138, 130, 168], metalDark: [40, 36, 54],
        accent: [185, 140, 255], accentDark: [111, 69, 184],
        emblemMask: [
            '..#..',
            '..#..',
            '#####',
            '..#..',
            '..#..',
        ],
    },
    // 『三相』：三色棱镜钢（三种主题色交替出现在装饰线上）
    three_phase: {
        metal: [196, 200, 220], metalLight: [255, 255, 255], metalDark: [88, 92, 120],
        accent: [63, 217, 138], accentDark: [111, 69, 184],
        accent3: [[63, 217, 138], [242, 178, 51], [185, 140, 255]],
    },
};

/** 三相装备的装饰色按 x 坐标三色轮转；其它主题用单一强调色。 */
const accentAt = (theme, index) => (theme.accent3
    ? theme.accent3[((index % theme.accent3.length) + theme.accent3.length) % theme.accent3.length]
    : theme.accent);

// ---------------------------------------------------------------- 方块贴图
function blockTexture(theme, seed) {
    const c = new Canvas(16, 16);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            const n = noise(x, y, seed);
            let col = theme.blockBase;
            if (n < 0.28) col = theme.blockDark;
            else if (n > 0.74) col = theme.blockLight;
            if (n > 0.93) col = shade(theme.blockBase, -0.28); // 零星深色斑点
            c.set(x, y, col);
        }
    }
    // 倒角：上/左提亮，下/右压暗
    for (let x = 0; x < 16; x++) {
        c.set(x, 0, shade(theme.blockLight, 0.18));
        c.set(x, 15, shade(theme.blockDark, -0.18));
    }
    for (let y = 0; y < 16; y++) {
        c.set(0, y, shade(theme.blockLight, 0.10));
        c.set(15, y, shade(theme.blockDark, -0.10));
    }
    // 中央徽记（+ 一圈暗色描边）
    const m = theme.emblemMask;
    const ox = Math.floor((16 - m[0].length) / 2);
    const oy = Math.floor((16 - m.length) / 2);
    for (let y = 0; y < m.length; y++) {
        for (let x = 0; x < m[y].length; x++) {
            if (m[y][x] === '#') {
                for (let dy = -1; dy <= 1; dy++) {
                    for (let dx = -1; dx <= 1; dx++) {
                        if (dx === 0 && dy === 0) continue;
                        c.set(ox + x + dx, oy + y + dy, theme.emblemDark);
                    }
                }
            }
        }
    }
    c.drawMask(m, { '#': theme.emblem }, ox, oy);
    return c;
}

// ---------------------------------------------------------------- 锭
function ingotTexture(theme) {
    const c = new Canvas(16, 16);
    const M = theme.metal, L = theme.metalLight, D = theme.metalDark;
    // 主体（等轴观感的梯形块）
    const rows = [
        { y: 5, x0: 6, x1: 11 },
        { y: 6, x0: 4, x1: 12 },
        { y: 7, x0: 3, x1: 12 },
        { y: 8, x0: 3, x1: 12 },
        { y: 9, x0: 3, x1: 12 },
        { y: 10, x0: 4, x1: 12 },
        { y: 11, x0: 5, x1: 11 },
    ];
    rows.forEach(({ y, x0, x1 }, idx) => {
        for (let x = x0; x <= x1; x++) {
            let col = M;
            if (idx === 0) col = L;
            else if (y === rows[rows.length - 1].y) col = D;
            c.set(x, y, col);
        }
    });
    // 上表面高光 + 徽记色描边（体现主题色）
    for (let x = 6; x <= 11; x++) c.set(x, 5, L);
    c.set(3, 7, L); c.set(4, 7, L);
    c.set(3, 11, D); c.set(12, 11, D);
    c.set(12, 8, D); c.set(12, 9, D);
    // 中央一道主题色刻线（三相主题会变成三色轮转）
    for (let x = 5; x <= 10; x++) c.set(x, 9, accentAt(theme, x - 5));
    for (let x = 6; x <= 10; x++) c.set(x, 8, shade(accentAt(theme, x - 6), 0.35));
    return c;
}

// ---------------------------------------------------------------- 剑
const SWORD_MASK = [
    '................',
    '.............oX.',
    '............oXX.',
    '...........oXX..',
    '..........oXX...',
    '.........oXX....',
    '........oXX.....',
    '.......oXX......',
    '......oXX.......',
    '.....oXX........',
    '..AAoXX.........',
    '..AAA X.........',
    '...HH...........',
    '..HH............',
    '.PH.............',
    '.PP.............',
];

function swordTexture(theme) {
    const c = new Canvas(16, 16);
    c.drawMask(SWORD_MASK, {
        'X': theme.metal,        // 剑身
        'o': theme.metalLight,   // 剑刃（受光面）
        'A': theme.accent,       // 护手
        'H': [107, 74, 43],      // 握柄
        'P': theme.accentDark,   // 柄尾
    });
    // 剑身暗侧描边
    SWORD_MASK.forEach((row, y) => {
        for (let x = 0; x < row.length; x++) {
            if (row[x] !== 'X') continue;
            const right = row[x + 1];
            if (right === '.' || right === undefined) c.set(x, y, theme.metalDark);
            if (y + 1 < SWORD_MASK.length && SWORD_MASK[y + 1][x] === '.') c.set(x, y, theme.metalDark);
        }
    });
    // 三相剑：护手按 x 三色轮转
    if (theme.accent3) {
        SWORD_MASK.forEach((row, y) => {
            for (let x = 0; x < row.length; x++) {
                if (row[x] === 'A') c.set(x, y, accentAt(theme, x));
            }
        });
    }
    return c;
}

// ---------------------------------------------------------------- 盔甲图标
const ARMOR_ICONS = {
    helmet: [
        '................',
        '................',
        '....########....',
        '...##########...',
        '..############..',
        '..###oooooo###..',
        '..##oo....oo##..',
        '..##o......o##..',
        '..##o......o##..',
        '..###o....o###..',
        '..####o..o####..',
        '..####....####..',
        '...##......##...',
        '................',
        '................',
        '................',
    ],
    chestplate: [
        '................',
        '................',
        '..###......###..',
        '..####....####..',
        '..############..',
        '..############..',
        '..###oooooo###..',
        '..###oooooo###..',
        '..############..',
        '...##########...',
        '...##########...',
        '....########....',
        '....########....',
        '.....######.....',
        '................',
        '................',
    ],
    leggings: [
        '................',
        '................',
        '..############..',
        '..############..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '..###o....o###..',
        '...##......##...',
        '................',
        '................',
    ],
    boots: [
        '................',
        '................',
        '................',
        '................',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..#####..#####..',
        '..#####..#####..',
        '..######o######.',
        '..######o######.',
        '................',
        '................',
        '................',
    ],
};

function armorIconTexture(theme, piece) {
    const c = new Canvas(16, 16);
    c.drawMask(ARMOR_ICONS[piece], {
        '#': theme.metal,
        'o': theme.accent,
    });
    const rows = ARMOR_ICONS[piece];
    // 三相：装饰线三色轮转
    if (theme.accent3) {
        rows.forEach((row, y) => {
            for (let x = 0; x < row.length; x++) {
                if (row[x] === 'o') c.set(x, y, accentAt(theme, x));
            }
        });
    }
    // 描边 + 高光：外轮廓压暗、上沿提亮
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            if (rows[y][x] === '.') continue;
            const up = y > 0 ? rows[y - 1][x] : '.';
            const down = y < 15 ? rows[y + 1][x] : '.';
            if (down === '.') c.set(x, y, theme.metalDark);
            else if (up === '.' && rows[y][x] !== 'o') c.set(x, y, theme.metalLight);
        }
    }
    return c;
}

// ---------------------------------------------------------------- 盔甲模型层（64×32）
// 原版人形盔甲贴图布局：头盔(0,0)-(32,16) 身体(16,16)-(40,32) 手臂(40,16)-(56,32) 腿(0,16)-(16,32)
function armorLayerTexture(theme, inner) {
    const c = new Canvas(64, 32);
    const boxes = [
        [0, 0, 32, 16],   // 头盔
        [16, 16, 40, 32], // 身体
        [40, 16, 56, 32], // 手臂
        [0, 16, 16, 32],  // 腿
    ];
    const seed = inner ? 7 : 3;
    for (const [x0, y0, x1, y1] of boxes) {
        for (let y = y0; y < y1; y++) {
            for (let x = x0; x < x1; x++) {
                const n = noise(x, y, seed);
                let col = theme.metal;
                if (n < 0.22) col = theme.metalDark;
                else if (n > 0.82) col = theme.metalLight;
                c.set(x, y, col);
            }
        }
        // 上沿高光 / 下沿阴影
        for (let x = x0; x < x1; x++) {
            c.set(x, y0, theme.metalLight);
            c.set(x, y1 - 1, theme.metalDark);
        }
    }
    // 头盔额带 + 胸甲中线用主题色（三相同样三色轮转）
    for (let x = 0; x < 32; x++) c.set(x, 12, accentAt(theme, x));
    for (let x = 20; x < 28; x++) c.set(x, 22, accentAt(theme, x));
    for (let y = 22; y < 30; y++) c.set(27, y, theme.accentDark);
    return c;
}

// ---------------------------------------------------------------- 工具（三相专用）
// 'M' = 金属头，'H' = 木柄；柄是从左下往右上的一条斜线，头部按工具形状另画。
const TOOL_MASKS = {
    pickaxe: [
        '................',
        '..M..........M..',
        '..MM........MM..',
        '...MM......MM...',
        '....MM....MM....',
        '.....MM..MM.....',
        '......MMMM......',
        '.......HH.......',
        '......HH........',
        '.....HH.........',
        '....HH..........',
        '...HH...........',
        '..HH............',
        '.HH.............',
        'HH..............',
        '................',
    ],
    axe: [
        '................',
        '.......MMM......',
        '......MMMMM.....',
        '.....MMMMMM.....',
        '.....MMMMMMM....',
        '.....MMMMMM.....',
        '......MMMM......',
        '.......HH.......',
        '......HH........',
        '.....HH.........',
        '....HH..........',
        '...HH...........',
        '..HH............',
        '.HH.............',
        'HH..............',
        '................',
    ],
    shovel: [
        '................',
        '......MMMM......',
        '.....MMMMMM.....',
        '.....MMMMMM.....',
        '.....MMMMMM.....',
        '......MMMM......',
        '.......MM.......',
        '.......HH.......',
        '......HH........',
        '.....HH.........',
        '....HH..........',
        '...HH...........',
        '..HH............',
        '.HH.............',
        'HH..............',
        '................',
    ],
    hoe: [
        '................',
        '..........MMM...',
        '.........MMMMM..',
        '.......MMMMM....',
        '.......MMM......',
        '......HH........',
        '.....HH.........',
        '....HH..........',
        '...HH...........',
        '..HH............',
        '.HH.............',
        'HH..............',
        '................',
        '................',
        '................',
        '................',
    ],
};

function toolTexture(theme, mask) {
    const c = new Canvas(16, 16);
    c.drawMask(mask, {
        'M': theme.metal,
        'H': [107, 74, 43],
    });
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            const ch = mask[y][x];
            if (ch === '.') continue;
            const down = y < 15 ? mask[y + 1][x] : '.';
            const up = y > 0 ? mask[y - 1][x] : '.';
            if (down === '.') c.set(x, y, ch === 'M' ? theme.metalDark : [78, 52, 30]);
            else if (up === '.' && ch === 'M') c.set(x, y, theme.metalLight);
        }
    }
    // 三相：头部撒上三色光点
    if (theme.accent3) {
        for (let y = 0; y < 16; y++) {
            for (let x = 0; x < 16; x++) {
                if (mask[y][x] === 'M' && (x + y) % 4 === 0) c.set(x, y, accentAt(theme, x));
            }
        }
    }
    return c;
}

// ---------------------------------------------------------------- 投掷物以外的输出
const BLOCK_IDS = ['self', 'beast', 'he'];
const ITEM_IDS = ['self', 'beast', 'he', 'three_phase'];
const PIECES = ['helmet', 'chestplate', 'leggings', 'boots'];
let written = 0;

function write(file, canvas) {
    canvas.save(file);
    written++;
}

ITEM_IDS.forEach((id, index) => {
    const theme = THEMES[id];
    const tex = path.join(ASSETS, 'divinebeast', 'textures');

    write(path.join(tex, 'item', `${id}_ingot.png`), ingotTexture(theme));
    write(path.join(tex, 'item', `${id}_sword.png`), swordTexture(theme));
    PIECES.forEach(piece => {
        write(path.join(tex, 'item', `${id}_${piece}.png`), armorIconTexture(theme, piece));
    });
    for (const inner of [false, true]) {
        // 1.20.1 的盔甲贴图路径由 ArmorMaterial#getName() 决定：
        // name = "divinebeast:self" → assets/divinebeast/textures/models/armor/self_layer_1.png
        write(path.join(tex, 'models', 'armor', `${id}_layer_${inner ? 2 : 1}.png`),
            armorLayerTexture(theme, inner));
    }
    // 三相额外自带一套工具图标（镐 / 斧 / 锹 / 锄）
    if (id === 'three_phase') {
        for (const tool of Object.keys(TOOL_MASKS)) {
            write(path.join(tex, 'item', `${id}_${tool}.png`), toolTexture(theme, TOOL_MASKS[tool]));
        }
    }
});

BLOCK_IDS.forEach((id, index) => {
    write(path.join(ASSETS, 'divinebeast', 'textures', 'block', `${id}_block.png`),
        blockTexture(THEMES[id], index + 1));
});

console.log(`wrote ${written} png files`);
