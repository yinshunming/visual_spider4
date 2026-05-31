<template>
  <div class="welcome-page">
    <h1>Visual Spider</h1>
    <p class="subtitle">可视化爬虫管理系统</p>

    <el-card class="status-card">
      <template #header>
        <span>系统状态</span>
      </template>
      <div class="status-item">
        <span class="label">后端服务：</span>
        <el-tag :type="backendStatus === 'UP' ? 'success' : 'danger'">
          {{ backendStatus || '检测中...' }}
        </el-tag>
      </div>
      <div class="status-item">
        <span class="label">数据库：</span>
        <el-tag :type="databaseStatus === 'UP' ? 'success' : 'danger'">
          {{ databaseStatus || '检测中...' }}
        </el-tag>
      </div>
      <div class="status-item">
        <span class="label">检查时间：</span>
        <span>{{ timestamp || '-' }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHealth } from '../api/health'

const backendStatus = ref('')
const databaseStatus = ref('')
const timestamp = ref('')

onMounted(async () => {
  try {
    const response = await getHealth()
    backendStatus.value = response.data.status
    databaseStatus.value = response.data.database
    timestamp.value = response.data.timestamp
  } catch (error) {
    backendStatus.value = 'DOWN'
    databaseStatus.value = 'DOWN'
    timestamp.value = '-'
  }
})
</script>

<style scoped>
.welcome-page {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

h1 {
  text-align: center;
  color: #409eff;
}

.subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 30px;
}

.status-card {
  margin-top: 20px;
}

.status-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.status-item:last-child {
  margin-bottom: 0;
}

.label {
  width: 100px;
  font-weight: bold;
}
</style>
