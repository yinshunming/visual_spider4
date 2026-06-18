$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$cfgId = Get-Content "$PSScriptRoot\..\logs\sina-config.id" -Raw

$body = "{`"configId`":$cfgId,`"urls`":null}"
$r = Invoke-RestMethod -Method Post -Uri "$base/tasks" -ContentType 'application/json' -Body $body
$taskId = $r.data.id
Write-Host "任务已创建 taskId=$taskId status=$($r.data.status) totalItems=$($r.data.totalItems)"
Set-Content -Path "$PSScriptRoot\..\logs\sina-task.id" -Value $taskId -NoNewline
