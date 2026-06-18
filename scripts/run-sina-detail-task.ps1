$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$cfgId = Get-Content "$PSScriptRoot\..\logs\sina-detail-config.id" -Raw

$urls = @(
  'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzr2162424.shtml',
  'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzi6392215.shtml',
  'https://sports.sina.com.cn/basketball/nba/2026-06-18/doc-inicvvzi6392137.shtml'
)
$body = @{ configId = [int]$cfgId; urls = $urls } | ConvertTo-Json -Depth 5
$r = Invoke-RestMethod -Method Post -Uri "$base/tasks" -ContentType 'application/json' -Body $body
$taskId = $r.data.id
Write-Host "DETAIL_ONLY 任务已创建 taskId=$taskId status=$($r.data.status) totalItems=$($r.data.totalItems)"
Set-Content -Path "$PSScriptRoot\..\logs\sina-detail-task.id" -Value $taskId -NoNewline
