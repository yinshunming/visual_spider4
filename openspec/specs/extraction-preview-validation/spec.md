# extraction-preview-validation（抽取预览与校验）

## Purpose

在配置编辑阶段提供抽取结果的实时预览能力，以及爬取过程中的错误反馈和字段级部分成功语义。

## ADDED Requirements

### Requirement: 配置编辑时的实时抽取预览
在配置编辑期间，系统 SHALL 允许用户打开浏览器中的任意页面并应用当前定义的抽取模板，实时预览提取结果。

#### 场景：在当前页面预览
- **WHEN** 用户已打开浏览器页面并定义了抽取模板字段
- **THEN** 用户 MAY 触发"预览"，系统将模板应用于当前页面 DOM 并返回每个字段的提取值

### Requirement: 按选择器提取字段
用户 SHALL 能够对当前浏览器页面上的任意字段选择器触发字段提取，立即返回提取值。

#### 场景：在当前页面提取单个字段
- **WHEN** 用户对某字段的选择器触发提取
- **THEN** 系统将选择器应用于当前渲染后的 DOM 并返回提取值

### Requirement: 提取失败的错误反馈
当页面在爬取期间加载失败时，系统 SHALL 在受影响实体级别（list_page、list_item 或 article）记录错误，并附带描述性 error_message。

#### 场景：列表页加载失败
- **WHEN** 浏览器加载列表页 URL 失败
- **THEN** list_page 保存时状态为 FAILED，error_message 包含失败原因

#### 场景：详情页加载失败
- **WHEN** 浏览器加载详情页 URL 失败
- **THEN** article 保存时状态为 FAILED，error_message 包含失败原因；关联的 list_item.status 也设为 FAILED

### Requirement: 字段级部分成功
当部分字段提取失败但页面本身加载成功时，系统 SHALL 保存有效字段，失败的字段存储为 null。这不视为实体的失败状态。

#### 场景：部分字段提取为 null
- **WHEN** 详情页的 5 个字段中有 2 个无匹配
- **THEN** article 保存时状态为 CRAWLED；2 个无匹配字段在 custom_fields 中值为 null；另外 3 个字段有提取值
