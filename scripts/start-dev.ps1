# visual_spider4 一键启动脚本（PowerShell）
#
# 用法（在 D:\opencodeSpace\visual_spider4 目录下）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
#
# 行为：
#   1. 校验本机 PG 是否在 5432 监听
#   2. 在 logs/ 下写日志：backend.log、frontend.log
#   3. 用 Start-Process 后台拉起 Spring Boot (8080) 和 Vite (5173)
#   4. 等几秒后打印端口监听情况
#
# 不依赖 docker / 沙箱；假定你已经按 runbook.md 装好 PostgreSQL 16 并建好 visual_spider4 库

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root 'logs'
$backend = Join-Path $root 'backend'
$frontend= Join-Path $root 'frontend'
$beLog   = Join-Path $logsDir 'backend.log'
$feLog   = Join-Path $logsDir 'frontend.log'

if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
'' | Set-Content -Path $beLog
'' | Set-Content -Path $feLog

function Test-PortListening([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return [bool]$conn
}

# --- 1. 校验 PG ---
Write-Host '[1/3] 检查 PostgreSQL 5432 ...' -ForegroundColor Cyan
$pgReady = & pg_isready -h localhost -p 5432 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "PG 还没起来：$pgReady" -ForegroundColor Red
    Write-Host '请按 docs/runbook.md 启动本机 PG（net start postgresql-x64-16），然后再跑我。' -ForegroundColor Red
    exit 1
}
Write-Host "  PG ok: $pgReady" -ForegroundColor Green

# --- 2. 启动后端 ---
Write-Host '[2/3] 启动 Spring Boot (后台，日志 → logs/backend.log) ...' -ForegroundColor Cyan
$beCmd = "cd /d `"$backend`" && mvn spring-boot:run"
Start-Process -FilePath cmd.exe -ArgumentList '/c', $beCmd -RedirectStandardOutput $beLog -RedirectStandardError $beLog -WindowStyle Hidden

# --- 3. 启动前端 ---
Write-Host '[3/3] 启动 Vite dev server (后台，日志 → logs/frontend.log) ...' -ForegroundColor Cyan
$feCmd = "cd /d `"$frontend`" && npm run dev -- --host"
Start-Process -FilePath cmd.exe -ArgumentList '/c', $feCmd -RedirectStandardOutput $feLog -RedirectStandardError $feLog -WindowStyle Hidden

# --- 等启动 ---
Write-Host '等待后端、前端监听端口 ...' -NoNewline
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $be = Test-PortListening 8080
    $fe = Test-PortListening 5173
    if ($be -and $fe) { break }
    Write-Host '.' -NoNewline
}
Write-Host ''

# --- 报告 ---
Write-Host '================ 启动结果 ================' -ForegroundColor Yellow
Write-Host "Backend (8080) : $(if (Test-PortListening 8080) {'UP'} else {'DOWN'})"
Write-Host "Frontend (5173): $(if (Test-PortListening 5173) {'UP'} else {'DOWN'})"
Write-Host ''
Write-Host '健康检查命令：'
Write-Host '  curl http://localhost:8080/api/v1/health'
Write-Host '  浏览器打开 http://localhost:5173/'
Write-Host ''
Write-Host '实时日志：'
Write-Host "  Get-Content $beLog -Wait"
Write-Host "  Get-Content $feLog -Wait"
Write-Host ''
if (-not (Test-PortListening 8080)) {
    Write-Host '后端还没起来？看 backend.log 末尾几行：' -ForegroundColor Red
    Get-Content $beLog -Tail 30
}