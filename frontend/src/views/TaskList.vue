<template>
  <div class="task-list">
    <h2>爬取任务</h2>
    <div class="toolbar">
      <label>
        配置:
        <input v-model.number="configIdFilter" type="number" placeholder="全部" min="1" />
      </label>
      <el-button @click="refresh" size="small">刷新</el-button>
    </div>
    <el-table :data="taskStore.list" v-loading="taskStore.isLoading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="配置">
        <template #default="{ row }">
          {{ row.configId }}
        </template>
      </el-table-column>
      <el-table-column prop="pageType" label="模式" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="progressOf(row)"
            :status="progressStatusOf(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="failedItems" label="失败数" width="80" />
      <el-table-column prop="startedAt" label="开始时间" width="180">
        <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button
            size="mini"
            type="danger"
            :disabled="row.status !== 'RUNNING'"
            @click="onStop(row)"
          >停止</el-button>
          <el-button size="mini" @click="onView(row)">详情</el-button>
          <el-button size="mini" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="taskStore.total"
      layout="prev, pager, next"
      @current-change="refresh"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTaskStore } from '../stores/taskStore'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const taskStore = useTaskStore()
const configIdFilter = ref(null)
const page = ref(0)
const size = ref(20)

function statusTagType(status) {
  return { RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[status] || 'info'
}

function progressOf(row) {
  if (!row.totalItems) {
    // 无条目:已完成显示满进度,其余显示 0
    return row.status === 'COMPLETED' ? 100 : 0
  }
  return Math.round(((row.crawledItems + row.failedItems) / row.totalItems) * 100)
}

function progressStatusOf(row) {
  if (row.status === 'FAILED') return 'exception'
  if (row.status === 'COMPLETED') return 'success'
  return ''
}

function formatTime(s) {
  if (!s) return '-'
  return new Date(s).toLocaleString()
}

async function refresh() {
  await taskStore.fetchList(configIdFilter.value, page.value, size.value)
}

async function onStop(row) {
  await taskStore.stopTask(row.id)
  ElMessage.success(`已发送停止信号给任务 ${row.id}`)
  refresh()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除任务 ${row.id}?`, '删除确认')
  await taskStore.deleteTask(row.id)
  ElMessage.success('已删除')
  refresh()
}

function onView(row) {
  router.push(`/tasks/${row.id}`)
}

onMounted(refresh)
</script>

<style scoped>
.task-list { padding: 20px; }
.toolbar { margin-bottom: 12px; }
.toolbar label { margin-right: 12px; }
</style>