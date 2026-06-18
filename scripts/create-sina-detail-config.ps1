$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api/v1'

# 1. 创建 DETAIL_ONLY 配置（只需 DETAIL 字段）
$createBody = @{
  name = '新浪NBA详情页(DETAIL_ONLY)'
  startUrl = 'https://sports.sina.com.cn/nba/'
  pageType = 'DETAIL_ONLY'
  selectorType = 'CSS'
} | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$base/configs" -ContentType 'application/json' -Body $createBody
$cfgId = $created.data.id
Write-Host "DETAIL_ONLY 配置已创建 id=$cfgId"

# 2. PUT 全量字段（仅 DETAIL）
$fields = @{
  name = '新浪NBA详情页(DETAIL_ONLY)'
  startUrl = 'https://sports.sina.com.cn/nba/'
  pageType = 'DETAIL_ONLY'
  selectorType = 'CSS'
  fields = @(
    @{ pageType='DETAIL'; fieldName='title';        fieldType='TEXT'; selector='h1.main-title' },
    @{ pageType='DETAIL'; fieldName='content';      fieldType='TEXT'; selector='#artibody' },
    @{ pageType='DETAIL'; fieldName='publish_time'; fieldType='TEXT'; selector='.date' },
    @{ pageType='DETAIL'; fieldName='source';       fieldType='TEXT'; selector='.source' }
  )
} | ConvertTo-Json -Depth 8
$updated = Invoke-RestMethod -Method Put -Uri "$base/configs/$cfgId" -ContentType 'application/json' -Body $fields
Write-Host "字段已写入，共 $($updated.data.fields.Count) 个字段"
Set-Content -Path "$PSScriptRoot\..\logs\sina-detail-config.id" -Value $cfgId -NoNewline
Write-Host "configId=$cfgId 已写入 logs/sina-detail-config.id"
