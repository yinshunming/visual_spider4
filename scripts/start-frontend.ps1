$ErrorActionPreference = 'Continue'
cd D:\opencodeSpace\visual_spider4\frontend
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','npm run dev' -PassThru -NoNewWindow `
  -RedirectStandardOutput 'D:\opencodeSpace\visual_spider4\logs\frontend.out.log' `
  -RedirectStandardError 'D:\opencodeSpace\visual_spider4\logs\frontend.err.log'
Set-Content -Path 'D:\opencodeSpace\visual_spider4\logs\frontend.pid' -Value $proc.Id
Write-Host "Frontend PID=$($proc.Id)"
