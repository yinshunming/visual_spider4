$ErrorActionPreference = 'Continue'
# 1. 通过端口 5432 占用进程找 PG 安装目录
$conns = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conns) {
  $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
  if ($p) { Write-Host "PG 进程 pid=$($p.Id) path=$($p.Path)" }
}
# 2. 暴力搜 psql.exe
$hits = @(
  'C:\Program Files\PostgreSQL',
  'C:\Program Files (x86)\PostgreSQL',
  'C:\PostgreSQL',
  'C:\pgsql'
)
foreach ($h in $hits) {
  if (Test-Path $h) {
    Get-ChildItem -Path $h -Recurse -Filter 'psql.exe' -ErrorAction SilentlyContinue | Select-Object -First 3 -ExpandProperty FullName
  }
}
