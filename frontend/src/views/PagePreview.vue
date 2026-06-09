<template>
  <div class="page-preview">
    <div class="page-header">
      <h1>页面预览</h1>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-form :model="{ url }" label-width="80px" inline>
      <el-form-item label="URL">
        <el-input
          v-model="url"
          placeholder="输入 http:// 或 https:// 开头的网址"
          style="width: 500px"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :loading="store.status === 'loading'"
          :disabled="!isValidUrl || store.status === 'loading'"
          @click="onLoad"
        >
          加载
        </el-button>
      </el-form-item>
    </el-form>

    <div v-if="store.status === 'loading'" class="status-block">
      <el-alert type="info" :closable="false" title="加载中..." />
    </div>

    <div v-else-if="store.status === 'error'" class="status-block">
      <el-alert type="error" :closable="false" :title="store.lastError || '加载失败'" />
    </div>

    <div v-else-if="store.status === 'success' && store.lastResult" class="result-block">
      <el-alert type="success" :closable="false" title="加载成功" />
      <el-descriptions :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="标题">{{ store.lastResult.title || '(无 title)' }}</el-descriptions-item>
        <el-descriptions-item label="最终 URL">{{ store.lastResult.finalUrl }}</el-descriptions-item>
        <el-descriptions-item label="字节数">{{ store.lastResult.contentLength }}</el-descriptions-item>
        <el-descriptions-item label="抓取时间">{{ store.lastResult.fetchedAt }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-else class="status-block placeholder">
      尚未加载
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { usePageFetchStore } from '../stores/pageFetchStore'

const store = usePageFetchStore()
const url = ref('')

const isValidUrl = computed(() => {
  const v = url.value && url.value.trim()
  return !!v && /^https?:\/\//i.test(v)
})

async function onLoad() {
  if (!isValidUrl.value) return
  await store.fetch({ url: url.value.trim() })
}

defineExpose({ url, onLoad })
</script>

<style scoped>
.page-preview {
  max-width: 1100px;
  margin: 20px auto;
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.status-block {
  margin-top: 20px;
}
.result-block {
  margin-top: 20px;
}
.placeholder {
  color: #909399;
  text-align: center;
  padding: 40px 0;
}
</style>
