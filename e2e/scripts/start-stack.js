// e2e/scripts/start-stack.js
// 启动后端 jar (8080) + 前端 vite dev (5173),跑测试,再关掉
// 用法: node scripts/start-stack.js -- npx playwright test
const { spawn } = require('child_process')
const path = require('path')
const http = require('http')
const fs = require('fs')

const ROOT = path.resolve(__dirname, '..', '..')
const LOG_DIR = path.resolve(__dirname, '..', 'logs')
fs.mkdirSync(LOG_DIR, { recursive: true })

const procs = []

function logFile(name) {
  return path.join(LOG_DIR, `${name}.log`)
}

function start(name, cmd, args, opts = {}) {
  const out = fs.openSync(logFile(name), 'a')
  const err = fs.openSync(logFile(name), 'a')
  const p = spawn(cmd, args, {
    cwd: opts.cwd || ROOT,
    env: { ...process.env, ...(opts.env || {}) },
    stdio: ['ignore', out, err],
    shell: true
  })
  p.on('exit', (code) => {
    console.error(`[${name}] exited with code ${code}`)
  })
  procs.push({ name, p })
  return p
}

function killAll() {
  for (const { name, p } of procs) {
    try {
      p.kill('SIGTERM')
    } catch (e) {}
  }
}

process.on('SIGINT', () => { killAll(); process.exit(130) })
process.on('SIGTERM', () => { killAll(); process.exit(143) })

function waitForUrl(url, timeoutMs, label) {
  const start = Date.now()
  return new Promise((resolve, reject) => {
    const tick = () => {
      const req = http.get(url, (res) => {
        res.resume()
        if (res.statusCode < 500) {
          console.log(`[ready] ${label} after ${Date.now() - start}ms`)
          resolve()
        } else if (Date.now() - start > timeoutMs) {
          reject(new Error(`${label} did not become ready (status ${res.statusCode})`))
        } else {
          setTimeout(tick, 500)
        }
      })
      req.on('error', () => {
        if (Date.now() - start > timeoutMs) {
          reject(new Error(`${label} never responded`))
        } else {
          setTimeout(tick, 500)
        }
      })
      req.setTimeout(2000, () => req.destroy())
    }
    tick()
  })
}

async function main() {
  const sep = process.argv.indexOf('--')
  const testCmd = sep >= 0 ? process.argv.slice(sep + 1) : ['npx', 'playwright', 'test']
  if (testCmd.length === 0) {
    console.error('No test command after --')
    process.exit(2)
  }

  const jarPath = path.join(ROOT, 'backend', 'target', 'visual-spider-backend-0.0.1-SNAPSHOT.jar')
  if (!fs.existsSync(jarPath)) {
    console.error(`Backend jar not found: ${jarPath}`)
    console.error('Run: cd backend && mvn -o clean package -DskipTests')
    process.exit(2)
  }

  console.log('[start] backend jar')
  start('backend', 'java', ['-jar', jarPath])

  console.log('[start] frontend dev')
  start('frontend', 'npm.cmd', ['run', 'dev'], { cwd: path.join(ROOT, 'frontend') })

  try {
    await waitForUrl('http://localhost:8080/api/v1/health', 60_000, 'backend')
    await waitForUrl('http://localhost:5173', 30_000, 'frontend')
  } catch (e) {
    console.error('[start] stack did not become ready:', e.message)
    console.error('See logs in', LOG_DIR)
    killAll()
    process.exit(1)
  }

  console.log('[run] test command:', testCmd.join(' '))
  const testProc = spawn(testCmd[0], testCmd.slice(1), {
    cwd: __dirname + '/..',
    env: process.env,
    stdio: 'inherit',
    shell: true
  })

  testProc.on('exit', (code) => {
    console.log(`[done] test exit code ${code}`)
    killAll()
    setTimeout(() => process.exit(code ?? 0), 1000)
  })
}

main().catch((e) => {
  console.error(e)
  killAll()
  process.exit(1)
})
