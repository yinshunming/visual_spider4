<template>
  <div class="config-list">
    <div class="page-header">
      <h1>配置管理</h1>
      <el-button type="primary" @click="goNew">新建配置</el-button>
    </div>

    <el-table :data="store.list.content || []" v-loading="store.loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="pageType" label="页面类型" width="120" />
      <el-table-column prop="selectorType" label="选择器" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="goEdit(row.id)">编辑</el-button>
          <el-button size="small" type="primary" plain @click="goPreview(row.id)">预览</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="store.list.totalElements">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="store.list.totalElements"
        layout="prev, pager, next, total"
        @current-change="loadConfigs"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useConfigStore } from '../stores/configStore'

const router = useRouter()
const store = useConfigStore()
const page = ref(0)
const size = ref(10)

onMounted(() => loadConfigs())

function loadConfigs() {
  store.fetchConfigs(page.value, size.value)
}

function goNew() {
  router.push('/configs/new')
}

function goEdit(id) {
  router.push(`/configs/${id}`)
}

function goPreview(id) {
  router.push(`/configs/${id}/preview`)
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除配置「${row.name}」吗？`, '确认删除', {
      type: 'warning'
    })
    await store.deleteConfig(row.id)
    ElMessage.success('删除成功')
    loadConfigs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.config-list {
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
.pagination {
  margin-top: 20px;
  text-align: right;
}
</style>
