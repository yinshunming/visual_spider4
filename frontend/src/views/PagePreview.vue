<template>
  <div class="page-preview">
    <div class="page-header">
      <h1>页面预览</h1>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-form :model="form" label-width="80px" inline>
      <el-form-item label="URL">
        <el-input
          v-model="form.url"
          placeholder="输入 http:// 或 https:// 开头的网址"
          style="width: 500px"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!isValidUrl || loading"
          @click="onLoad"
        >
          加载
        </el-button>
      </el-form-item>
    </el-form>

    <div v-if="browserStore.error" class="status-block">
      <el-alert type="error" :closable="false" :title="browserStore.error" />
    </div>

    <div v-if="browserStore.lastScreenshot" class="screenshot-block">
      <img
        :src="`data:image/png;base64,${browserStore.lastScreenshot}`"
        alt="page screenshot"
        class="screenshot"
        width="1280"
        @click="onImgClick"
      />
      <div class="hint">该元素位于 iframe / shadow DOM 内,本期不支持深入选择</div>
    </div>

    <div v-else class="status-block placeholder">
      尚未加载
    </div>

    <div v-if="browserStore.selectors" class="selector-block">
      <h3>候选选择器</h3>
      <el-radio-group v-model="selectedType">
        <el-radio label="css">CSS</el-radio>
        <el-radio label="xpath">XPath</el-radio>
      </el-radio-group>
      <div v-if="selectedCandidate" class="candidate">
        <div class="sel-text">{{ selectedCandidate.selector }}</div>
        <div class="sel-meta">匹配数: {{ selectedCandidate.matchCount }} 个</div>
        <ul class="samples">
          <li v-for="(s, i) in selectedCandidate.samples" :key="i">{{ s }}</li>
        </ul>
      </div>
      <el-button
        type="primary"
        :disabled="!selectedCandidate"
        @click="onPreview"
      >
        预览匹配
      </el-button>
    </div>

    <div v-if="browserStore.previewResult" class="preview-block">
      <el-alert
        :type="previewAlertType"
        :closable="false"
        :title="`匹配到 ${browserStore.previewResult.matchCount} 个元素`"
      />
    </div>

    <div v-if="selectedCandidate" class="field-form-block">
      <h3>字段保存</h3>
      <el-form :model="fieldForm" label-width="100px" inline>
        <el-form-item label="字段名">
          <el-input v-model="fieldForm.fieldName" style="width: 200px" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="fieldForm.fieldType" style="width: 120px">
            <el-option label="文本" value="TEXT" />
            <el-option label="数字" value="NUMBER" />
            <el-option label="日期" value="DATE" />
            <el-option label="URL" value="URL" />
          </el-select>
        </el-form-item>
        <el-form-item label="页面类型">
          <el-select v-model="fieldForm.pageType" style="width: 120px">
            <el-option label="列表" value="LIST" />
            <el-option label="详情" value="DETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!canSaveField" @click="onSaveField">
            保存
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="browserStore.saveFieldResult?.ok" class="ok-hint">已保存，字段 ID: {{ browserStore.saveFieldResult.fieldId }}</div>
      <div v-else-if="browserStore.saveFieldResult && !browserStore.saveFieldResult.ok" class="err-hint">
        {{ browserStore.saveFieldResult.message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useBrowserSessionStore } from '../stores/browserSessionStore'

const props = defineProps({
  id: { type: [String, Number], required: false }
})

const browserStore = useBrowserSessionStore()
const form = ref({ url: '' })
const selectedType = ref('css')
const fieldForm = ref({ fieldName: '', fieldType: 'TEXT', pageType: 'DETAIL' })

const isValidUrl = computed(() => {
  const v = form.value.url && form.value.url.trim()
  return !!v && /^https?:\/\//i.test(v)
})

const loading = computed(() => browserStore.status === 'loading' || browserStore.status === 'idle')

const selectedCandidate = computed(() => {
  if (!browserStore.selectors) return null
  return selectedType.value === 'css'
    ? browserStore.selectors.css
    : browserStore.selectors.xpath
})

const previewAlertType = computed(() => {
  const r = browserStore.previewResult
  if (!r) return 'info'
  if (r.matchCount === 0) return 'error'
  if (r.matchCount === 1) return 'success'
  return 'warning'
})

const canSaveField = computed(() =>
  fieldForm.value.fieldName.trim().length > 0
  && fieldForm.value.fieldType
  && fieldForm.value.pageType
  && selectedCandidate.value?.selector
)

async function onLoad() {
  if (!isValidUrl.value) return
  browserStore.loadUrl({ url: form.value.url.trim(), configId: Number(props.id) || null })
}

function onImgClick(evt) {
  if (!browserStore.lastScreenshot) return
  const x = evt.offsetX
  const y = evt.offsetY
  browserStore.click(x, y)
}

function onPreview() {
  if (!selectedCandidate.value) return
  browserStore.preview(selectedType.value, selectedCandidate.value.selector)
}

function onSaveField() {
  if (!canSaveField.value) return
  browserStore.saveField({
    pageType: fieldForm.value.pageType,
    fieldName: fieldForm.value.fieldName.trim(),
    fieldType: fieldForm.value.fieldType,
    selector: selectedCandidate.value.selector
  })
}

onMounted(async () => {
  await browserStore.connect()
  await browserStore.openSession()
})

onUnmounted(() => {
  browserStore.closeSession()
  browserStore.disconnect()
})
</script>

<style scoped>
.page-preview {
  max-width: 1400px;
  margin: 20px auto;
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.screenshot-block {
  margin-top: 20px;
}
.screenshot {
  display: block;
  border: 1px solid #ddd;
  cursor: crosshair;
}
.hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
.selector-block,
.preview-block,
.field-form-block {
  margin-top: 20px;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}
.candidate {
  margin: 12px 0;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 4px;
}
.sel-text {
  font-family: monospace;
  word-break: break-all;
  font-size: 14px;
  color: #303133;
}
.sel-meta {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.samples {
  margin-top: 8px;
  padding-left: 18px;
  color: #606266;
  font-size: 12px;
}
.ok-hint {
  margin-top: 8px;
  color: #67c23a;
}
.err-hint {
  margin-top: 8px;
  color: #f56c6c;
}
.placeholder {
  color: #909399;
  text-align: center;
  padding: 40px 0;
}
</style>
