// 反解 PNG 并按 ASCII 打印，用于在没有图像预览的环境里确认材质形状（开发工具）
// 用法： node tools/check_textures.js <png> [<png> ...]
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

function decode(file) {
    const buf = fs.readFileSync(file);
    let off = 8, w = 0, h = 0, colorType = 6;
    const idat = [];
    while (off < buf.length) {
        const len = buf.readUInt32BE(off);
        const type = buf.toString('ascii', off + 4, off + 8);
        const data = buf.subarray(off + 8, off + 8 + len);
        if (type === 'IHDR') {
            w = data.readUInt32BE(0);
            h = data.readUInt32BE(4);
            colorType = data[9];
        } else if (type === 'IDAT') idat.push(data);
        else if (type === 'IEND') break;
        off += 12 + len;
    }
    const bpp = colorType === 6 ? 4 : 3;
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const stride = w * bpp;
    const out = Buffer.alloc(stride * h);
    for (let y = 0; y < h; y++) {
        const filter = raw[y * (stride + 1)];
        const src = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
        for (let x = 0; x < stride; x++) {
            const a = x >= bpp ? out[y * stride + x - bpp] : 0;
            const b = y > 0 ? out[(y - 1) * stride + x] : 0;
            const c = (x >= bpp && y > 0) ? out[(y - 1) * stride + x - bpp] : 0;
            let v = src[x];
            if (filter === 1) v += a;
            else if (filter === 2) v += b;
            else if (filter === 3) v += (a + b) >> 1;
            else if (filter === 4) {
                const p = a + b - c;
                const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
            }
            out[y * stride + x] = v & 0xFF;
        }
    }
    return { w, h, bpp, px: out };
}

const RAMP = ' .:-=+*#%@';
for (const arg of process.argv.slice(2)) {
    const file = path.resolve(arg);
    const { w, h, bpp, px } = decode(file);
    console.log(`\n=== ${path.basename(file)}  ${w}x${h} bpp=${bpp} ===`);
    for (let y = 0; y < h; y++) {
        let line = '';
        for (let x = 0; x < w; x++) {
            const i = (y * w + x) * bpp;
            const a = bpp === 4 ? px[i + 3] : 255;
            if (a < 16) { line += '  '; continue; }
            const lum = (px[i] * 0.299 + px[i + 1] * 0.587 + px[i + 2] * 0.114) / 255;
            line += RAMP[Math.min(RAMP.length - 1, Math.floor(lum * RAMP.length))] + ' ';
        }
        console.log(line);
    }
}
