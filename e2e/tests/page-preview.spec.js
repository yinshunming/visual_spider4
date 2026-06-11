// e2e/tests/page-preview.spec.js
// 真 Chromium 跑 PagePreview.vue 全链路：load → click → preview → saveField
// 前置：PG 已起 + Chromium 已装（npx playwright install chromium）
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const TARGET_URL = 'https://example.com'
const FIELD_NAME = 'e2e_title'

test.describe('PagePreview 全链路', () => {
  let configId
  let fieldCountBefore

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const list = await api.get('/api/v1/configs', { params: { size: 100 } })
    expect(list.ok()).toBeTruthy()
    const body = await list.json()
    const items = (body.data && body.data.content) || []
    const existing = items.find((c) => c.name === 'E2E 临时')
    if (existing) {
      configId = existing.id
    } else {
      const create = await api.post('/api/v1/configs', {
        data: {
          name: 'E2E 临时',
          pageType: 'LIST_DETAIL',
          selectorType: 'CSS',
          status: 'STOPPED'
        },
        headers: { 'Content-Type': 'application/json' }
      })
      console.log('[e2e] create status:', create.status(), 'body:', await create.text())
      expect(create.ok()).toBeTruthy()
      const created = await create.json()
      configId = created.data.id
    }
    const fields = await api.get(`/api/v1/configs/${configId}/fields`)
    const fieldsBody = await fields.json()
    fieldCountBefore = (fieldsBody.data || []).length
    await api.dispose()
  })

  test('load → click → preview → saveField 全链路', async ({ page }) => {
    test.setTimeout(90_000)
    page.on('console', (msg) => console.log(`[browser:${msg.type()}]`, msg.text()))

    await page.goto(`/configs/${configId}/preview`)
    await expect(page.locator('h1')).toContainText('页面预览')

    await page.locator('input[placeholder*="http"]').fill(TARGET_URL)
    await page.getByRole('button', { name: '加载' }).click()

    const screenshot = page.locator('img.screenshot')
    await expect(screenshot).toBeVisible({ timeout: 30_000 })
    await page.screenshot({ path: 'test-results/01-loaded.png', fullPage: true })

    const box = await screenshot.boundingBox()
    expect(box).not.toBeNull()
    const naturalWidth = await screenshot.evaluate((el) => el.naturalWidth)
    const renderedWidth = box.width
    const scale = renderedWidth / naturalWidth
    console.log('[e2e] screenshot box:', box, 'natural width:', naturalWidth, 'scale:', scale)
    const clickX = box.x + 320 * scale
    const clickY = box.y + 220 * scale
    console.log('[e2e] click at', clickX, clickY)
    await page.mouse.click(clickX, clickY)
    await page.waitForTimeout(2000)
    await page.screenshot({ path: 'test-results/02-after-click.png', fullPage: true })

    const cssSelectorText = page.locator('.sel-text').first()
    await expect(cssSelectorText).toBeVisible({ timeout: 15_000 })
    const cssSelector = (await cssSelectorText.textContent())?.trim()
    expect(cssSelector, 'CSS selector should be non-empty').toBeTruthy()
    console.log('[e2e] CSS selector =', cssSelector)

    await page.getByRole('button', { name: '预览匹配' }).click()
    await page.waitForTimeout(3000)
    await page.screenshot({ path: 'test-results/03-preview.png', fullPage: true })

    const alert = page.locator('.preview-block .el-alert').first()
    await expect(alert).toBeVisible({ timeout: 10_000 })

    const fieldNameInput = page.locator('.field-form-block input').first()
    await fieldNameInput.waitFor({ state: 'visible', timeout: 10_000 })
    await fieldNameInput.fill(FIELD_NAME)
    const saveBtn = page.locator('.field-form-block button:has-text("保存")')
    await saveBtn.waitFor({ state: 'visible', timeout: 10_000 })
    await saveBtn.click()
    await page.waitForTimeout(3000)
    await page.screenshot({ path: 'test-results/04-saved.png', fullPage: true })

    const okHint = page.locator('.ok-hint')
    await expect(okHint).toBeVisible({ timeout: 10_000 })
    await expect(okHint).toContainText('已保存')

    const api = await request.newContext({ baseURL: BACKEND })
    const fields = await api.get(`/api/v1/configs/${configId}/fields`)
    const fieldsBody = await fields.json()
    const list = (fieldsBody.data && fieldsBody.data.content) || fieldsBody.data || []
    expect(Array.isArray(list)).toBeTruthy()
    expect(list.length).toBe(fieldCountBefore + 1)
    const created = list.find((f) => f.fieldName === FIELD_NAME)
    expect(created, `field ${FIELD_NAME} should exist`).toBeTruthy()
    expect(created.pageType).toBe('DETAIL')
    await api.dispose()
  })
})
