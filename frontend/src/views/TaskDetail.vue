<template>
  <div class="task-detail" v-loading="loading">
    <h2>任务 #{{ taskId }}</h2>
    <div v-if="taskStore.current" class="overview">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(taskStore.current.status)">{{ taskStore.current.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="进度">{{ progressText }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ taskStore.current.failedItems }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ formatTime(taskStore.current.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成">{{ formatTime(taskStore.current.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="错误">{{ taskStore.current.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <h3>爬取条目(article)</h3>
    <el-table :data="articles" empty-text="暂无数据">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="url" label="URL" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CRAWLED' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因">
        <template #default="{ row }">
          <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
            <span class="error-text">{{ row.errorMessage }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useTaskStore } from '@/stores/taskStore'

const route = useRoute()
const taskStore = useTaskStore()
const taskId = route.params.id
const articles = ref([])
const loading = ref(false)

const progressText = computed(() => {
  const c = taskStore.current
  if (!c || !c.totalItems) return '-'
  return `${c.crawledItems + c.failedItems} / ${c.totalItems}`
})

function statusTagType(status) {
  return { RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[status] || 'info'
}

function formatTime(s) {
  if (!s) return '-'
  return new Date(s).toLocaleString()
}

onMounted(() => {
  taskStore.startPolling(taskId)
})

onUnmounted(() => {
  taskStore.stopPolling()
})
</script>

<style scoped>
.task-detail { padding: 20px; }
.overview { margin-bottom: 20px; }
.error-text { color: #f56c6c; cursor: help; }
</style>