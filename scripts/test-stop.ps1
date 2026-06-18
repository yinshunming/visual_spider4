$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$cfgId = Get-Content "$PSScriptRoot\..\logs\sina-detail-config.id" -Raw

# 传 24 条 URL（3 条真实 URL 各重复 8 次），让任务跑足够久以便抓住 RUNNING 窗口
$urls = @()
for ($i = 0; $i -lt 8; $i++) {
  $urls += 'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzr2162424.shtml'
  $urls += 'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzi6392215.shtml'
  $urls += 'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzi6392137.shtml'
}
$body = @{ configId = [int]$cfgId; urls = $urls } | ConvertTo-Json -Depth 5
$r = Invoke-RestMethod -Method Post -Uri "$base/tasks" -ContentType 'application/json' -Body $body
$taskId = $r.data.id
Write-Host "停止测试任务已创建 taskId=$taskId"

# 紧 poll 直到看到 RUNNING，立即 stop
$stopped = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Milliseconds 800
  $t = (Invoke-RestMethod -Uri "$base/tasks/$taskId").data
  if ($t.status -eq 'RUNNING') {
    Write-Host ("抓到 RUNNING（crawled=$($t.crawledItems)/total=$($t.totalItems)），发起 stop ...")
    try {
      $stopResp = Invoke-RestMethod -Method Post -Uri "$base/tasks/$taskId/stop"
      Write-Host ("stop 返回 code=$($stopResp.code) message=$($stopResp.message)")
    } catch {
      Write-Host ("stop 异常: $($_.Exception.Message)")
    }
    $stopped = $true
    break
  }
  if ($t.status -ne 'RUNNING') {
    Write-Host ("第 ${i} 次：status=$($t.status)（任务可能已结束）")
    if ($t.status -eq 'COMPLETED' -or $t.status -eq 'FAILED') { break }
  }
}

if (-not $stopped) {
  Write-Host '未能在 RUNNING 窗口内发起 stop'
}

# 等任务真正收尾
Write-Host '等待任务收尾 ...'
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  $t = (Invoke-RestMethod -Uri "$base/tasks/$taskId").data
  if ($t.status -ne 'RUNNING') { break }
}
Write-Host '================ 停止后终态 ================'
$t | Select-Object id,status,totalItems,crawledItems,failedItems | Format-List
$premature = $t.totalItems -gt 0 -and $t.crawledItems -lt $t.totalItems
Write-Host ("提前结束验证: totalItems=$($t.totalItems) crawled=$($t.crawledItems) => $([bool]$premature)")
Set-Content -Path "$PSScriptRoot\..\logs\sina-stop-task.id" -Value $taskId -NoNewline
