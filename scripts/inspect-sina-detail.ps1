$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$taskId = Get-Content "$PSScriptRoot\..\logs\sina-detail-task.id" -Raw

Write-Host "=== DETAIL_ONLY 任务 $taskId 文章列表 ==="
$list = Invoke-RestMethod -Uri "$base/articles?task_id=$taskId&page=0&size=10"
Write-Host ("总数 totalElements=$($list.data.totalElements)")
$list.data.content | ForEach-Object {
  Write-Host ("---- article id=$($_.id) status=$($_.status)")
  Write-Host ("  url: $($_.url)")
  if ($_.customFields) {
    $_.customFields.PSObject.Properties | ForEach-Object {
      $v = $_.Value
      if ($v -and $v.Length -gt 70) { $v = $v.Substring(0,70) + '...' }
      Write-Host ("  $($_.Name) = $v")
    }
  }
}
