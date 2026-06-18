$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'

Write-Host '========== 1. 导出 JSON（config_id=4，LIST_DETAIL 20 条）=========='
$resp = Invoke-WebRequest -Method Post -Uri "$base/articles/export?format=JSON&config_id=4" -UseBasicParsing
Write-Host ("HTTP $($resp.StatusCode)  Content-Type=$($resp.Headers['Content-Type'])")
Write-Host ("Content-Disposition=$($resp.Headers['Content-Disposition'])")
$rows = $resp.Content | ConvertFrom-Json
Write-Host ("导出条数: $($rows.Count)")
$rows | Select-Object -First 2 | ForEach-Object {
  Write-Host ('  - title=' + ($_.title))
  Write-Host ('    publish_time=' + $_.publish_time + '  source=' + $_.source)
}

Write-Host ''
Write-Host '========== 2. 导出 XLSX（应 501 未实现）=========='
try {
  $r2 = Invoke-WebRequest -Method Post -Uri "$base/articles/export?format=XLSX&config_id=4" -UseBasicParsing
  Write-Host ("HTTP $($r2.StatusCode)  $($r2.Content)")
} catch {
  $e = $_.Exception.Response
  Write-Host ("HTTP $([int]$e.StatusCode)  $($e.StatusCode)")
  $sr = New-Object System.IO.StreamReader($e.GetResponseStream())
  Write-Host ("body: $($sr.ReadToEnd())")
}

Write-Host ''
Write-Host '========== 3. 导出不支持格式（应 400）=========='
try {
  $r3 = Invoke-WebRequest -Method Post -Uri "$base/articles/export?format=CSV&config_id=4" -UseBasicParsing
  Write-Host ("HTTP $($r3.StatusCode)  $($r3.Content)")
} catch {
  $e = $_.Exception.Response
  Write-Host ("HTTP $([int]$e.StatusCode)")
  $sr = New-Object System.IO.StreamReader($e.GetResponseStream())
  Write-Host ("body: $($sr.ReadToEnd())")
}
