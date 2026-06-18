$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'

function CountArticles($taskId) {
  $r = Invoke-RestMethod -Uri "$base/articles?task_id=$taskId&page=0&size=1"
  return $r.data.totalElements
}

# 测三个任务：6(LIST_DETAIL 20条) / 7(DETAIL_ONLY 3条) / 8(停止测试 0条)
foreach ($tid in @(6, 7, 8)) {
  Write-Host "================ 删除任务 $tid ================"
  $before = CountArticles $tid
  Write-Host "删除前 articles?task_id=$tid totalElements=$before"

  # 删除（HTTP 204 无 body）
  $del = Invoke-WebRequest -Method Delete -Uri "$base/tasks/$tid" -UseBasicParsing
  Write-Host "DELETE HTTP $($del.StatusCode)"

  # 删除后文章应清空
  $after = CountArticles $tid
  Write-Host "删除后 articles?task_id=$tid totalElements=$after"

  # 任务本身应 404
  try {
    Invoke-RestMethod -Uri "$base/tasks/$tid" | Out-Null
    Write-Host "  [警告] 任务 $tid 仍存在"
  } catch {
    $code = ($_.ErrorDetails.Message | ConvertFrom-Json).code
    Write-Host "  GET /tasks/$tid 返回 code=$code（期望 404）"
  }
  $ok = ($del.StatusCode -eq 204) -and ($after -eq 0)
  Write-Host ("  级联清理验证: $ok")
}

# 删除不存在的任务 → 期望 404
Write-Host ''
Write-Host '================ 删除不存在任务 9999（期望 404）================'
try {
  Invoke-WebRequest -Method Delete -Uri "$base/tasks/9999" -UseBasicParsing
} catch {
  $body = $_.ErrorDetails.Message
  $j = $body | ConvertFrom-Json
  Write-Host "  HTTP $([int]$_.Exception.Response.StatusCode) code=$($j.code) message=$($j.message)"
}
