$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'
$r = Invoke-WebRequest -Method Post -Uri "$base/articles/export?format=JSON&config_id=4" -UseBasicParsing
Write-Host "HTTP $($r.StatusCode)"
Write-Host "Content-Disposition=[$($r.Headers['Content-Disposition'])]"
Write-Host "Body 长度=$($r.Content.Length)"
Write-Host "--- Body 前 800 字符 ---"
Write-Host $r.Content.Substring(0, [Math]::Min(800, $r.Content.Length))
