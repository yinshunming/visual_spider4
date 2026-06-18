$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'

# 1. 创建配置
$createBody = @{
  name = '新浪NBA新闻'
  startUrl = 'https://sports.sina.com.cn/nba/'
  pageType = 'LIST_DETAIL'
  selectorType = 'CSS'
} | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$base/configs" -ContentType 'application/json' -Body $createBody
$cfgId = $created.data.id
Write-Host "配置已创建 id=$cfgId"

# 2. PUT 全量字段
$fields = @{
  name = '新浪NBA新闻'
  startUrl = 'https://sports.sina.com.cn/nba/'
  pageType = 'LIST_DETAIL'
  selectorType = 'CSS'
  fields = @(
    @{ pageType='LIST';   fieldName='detail_url';   fieldType='URL';  selector='.feed-item-title' },
    @{ pageType='DETAIL'; fieldName='title';        fieldType='TEXT'; selector='h1.main-title' },
    @{ pageType='DETAIL'; fieldName='content';      fieldType='TEXT'; selector='#artibody' },
    @{ pageType='DETAIL'; fieldName='publish_time'; fieldType='TEXT'; selector='.date' },
    @{ pageType='DETAIL'; fieldName='source';       fieldType='TEXT'; selector='.source' }
  )
} | ConvertTo-Json -Depth 8
$updated = Invoke-RestMethod -Method Put -Uri "$base/configs/$cfgId" -ContentType 'application/json' -Body $fields
Write-Host "字段已写入，共 $($updated.data.fields.Count) 个字段："
$updated.data.fields | ForEach-Object { Write-Host ("  - [{0}] {1} ({2}) = {3}" -f $_.pageType, $_.fieldName, $_.fieldType, $_.selector) }

# 输出 configId 供后续脚本使用
Set-Content -Path "$PSScriptRoot\..\logs\sina-config.id" -Value $cfgId -NoNewline
Write-Host "configId=$cfgId 已写入 logs/sina-config.id"
