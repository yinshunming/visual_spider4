$ErrorActionPreference = 'Continue'
cd D:\opencodeSpace\visual_spider4\backend
$proc = Start-Process -FilePath 'java' `
  -ArgumentList '-jar','target\visual-spider-backend-0.0.1-SNAPSHOT.jar' `
  -RedirectStandardOutput 'D:\opencodeSpace\visual_spider4\logs\backend.out.log' `
  -RedirectStandardError 'D:\opencodeSpace\visual_spider4\logs\backend.err.log' `
  -PassThru -NoNewWindow
Set-Content -Path 'D:\opencodeSpace\visual_spider4\logs\backend.pid' -Value $proc.Id
Write-Host "Backend PID=$($proc.Id)"
