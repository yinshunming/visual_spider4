$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$taskId = Get-Content "$PSScriptRoot\..\logs\sina-task.id" -Raw

Write-Host "=== 任务 $taskId 文章列表（前 5 条摘要）==="
$list = Invoke-RestMethod -Uri "$base/articles?task_id=$taskId&page=0&size=5"
Write-Host ("总数 totalElements=$($list.data.totalElements)")
$list.data.content | ForEach-Object {
  Write-Host ("---- article id=$($_.id) status=$($_.status)")
  Write-Host ("  url: $($_.url)")
  if ($_.customFields) {
    $_.customFields.PSObject.Properties | ForEach-Object {
      $v = $_.Value
      if ($v -and $v.Length -gt 80) { $v = $v.Substring(0,80) + '...' }
      Write-Host ("  $($_.Name) = $v")
    }
  }
}

Write-Host ""
Write-Host "=== 第 1 篇文章详情（含 raw_html 长度）==="
$firstId = $list.data.content[0].id
$detail = Invoke-RestMethod -Uri "$base/articles/$firstId"
$d = $detail.data
Write-Host ("id=$($d.id) url=$($d.url)")
Write-Host ("rawHtml 长度: $($d.rawHtml.Length) 字符")
Write-Host ("customFields 字段数: $($d.customFields.PSObject.Properties.Count)")
