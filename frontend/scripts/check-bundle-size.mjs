import { readdirSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 产物体积守卫。
 *
 * <p>存在的理由：AGENTS.md 第 5 节记着一条实测教训 —— `manualChunks` 里按包名
 * 通配分配 element-plus，会让 80 多个没用到的组件被强制打进一个 810 KB 的 chunk，
 * 而构建依然成功、页面依然能用，只有体积 silently 涨到 1.47 MB。
 * 这种回归靠人记「改完要核对体积」是守不住的，交给命令。
 *
 * <p>阈值取「当前实测 + 一点余量」，不是「理想值」：
 * 它的作用是发现**突变**（有人把按需引入改回去了），不是追求最小。
 */

const LIMITS = {
  js: 650 * 1024,
  css: 180 * 1024,
  // 单个 chunk 的上限：一整桶 element-plus 会明显超过它
  chunk: 400 * 1024,
}

const assetsDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist', 'assets')

let files
try {
  files = readdirSync(assetsDir)
} catch {
  console.error(`找不到构建产物：${assetsDir}\n先运行 pnpm build`)
  process.exit(1)
}

const sizes = files.map((name) => ({ name, size: statSync(join(assetsDir, name)).size }))
const sumOf = (ext) =>
  sizes.filter((f) => f.name.endsWith(ext)).reduce((total, f) => total + f.size, 0)

const js = sumOf('.js')
const css = sumOf('.css')
const largest = sizes.filter((f) => f.name.endsWith('.js')).sort((a, b) => b.size - a.size)[0]

const kb = (bytes) => `${(bytes / 1024).toFixed(0)} KB`

console.log(`JS 合计   ${kb(js)}（上限 ${kb(LIMITS.js)}）`)
console.log(`CSS 合计  ${kb(css)}（上限 ${kb(LIMITS.css)}）`)
if (largest) {
  console.log(`最大 chunk ${largest.name} ${kb(largest.size)}（上限 ${kb(LIMITS.chunk)}）`)
}

const problems = []
if (js > LIMITS.js) problems.push(`JS 合计 ${kb(js)} 超过 ${kb(LIMITS.js)}`)
if (css > LIMITS.css) problems.push(`CSS 合计 ${kb(css)} 超过 ${kb(LIMITS.css)}`)
if (largest && largest.size > LIMITS.chunk) {
  problems.push(`单个 chunk ${largest.name} 达 ${kb(largest.size)}，超过 ${kb(LIMITS.chunk)}`)
}

if (problems.length > 0) {
  console.error('\n产物体积异常：')
  for (const problem of problems) console.error(`  - ${problem}`)
  console.error(
    '\n最常见的原因是 vite.config.ts 的 manualChunks 又按包名通配分配了 element-plus，\n' +
      '导致整个桶文件被打包。判据：grep -c ElTransfer dist/assets/*.js 应全为 0。',
  )
  process.exit(1)
}

console.log('\n产物体积在阈值内。')
