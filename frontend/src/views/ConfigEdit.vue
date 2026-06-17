<template>
  <div class="config-edit">
    <div class="page-header">
      <h1>{{ isEdit ? '编辑配置' : '新建配置' }}</h1>
      <el-button v-if="isEdit" type="primary" plain @click="goPreview">打开预览</el-button>
    </div>

    <el-form :model="form" label-width="100px" v-loading="store.loading">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="输入配置名称" />
      </el-form-item>
      <el-form-item label="起始 URL" required>
        <el-input v-model="form.startUrl" placeholder="例如 https://example.com/list" />
      </el-form-item>
      <el-form-item label="页面类型" required>
        <el-select v-model="form.pageType" placeholder="选择页面类型" style="width: 100%">
          <el-option label="列表+详情 (LIST_DETAIL)" value="LIST_DETAIL" />
          <el-option label="仅详情 (DETAIL_ONLY)" value="DETAIL_ONLY" />
        </el-select>
      </el-form-item>
      <el-form-item label="选择器" required>
        <el-select v-model="form.selectorType" placeholder="选择选择器类型" style="width: 100%">
          <el-option label="CSS" value="CSS" />
          <el-option label="XPath" value="XPATH" />
        </el-select>
      </el-form-item>
    </el-form>

    <h2>字段配置</h2>
    <el-button @click="addFieldRow" type="primary" plain>添加字段</el-button>
    <el-table :data="form.fields" style="margin-top: 10px">
      <el-table-column label="名称" width="180">
        <template #default="{ row }">
          <el-input v-model="row.fieldName" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="140">
        <template #default="{ row }">
          <el-select v-model="row.fieldType" style="width: 100%">
            <el-option label="TEXT" value="TEXT" />
            <el-option label="NUMBER" value="NUMBER" />
            <el-option label="DATE" value="DATE" />
            <el-option label="URL" value="URL" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="页面" width="120">
        <template #default="{ row }">
          <el-select v-model="row.pageType" style="width: 100%">
            <el-option label="LIST" value="LIST" />
            <el-option label="DETAIL" value="DETAIL" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="选择器">
        <template #default="{ row }">
          <el-input v-model="row.selector" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ $index }">
          <el-button size="small" type="danger" @click="removeFieldRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="actions">
      <el-button @click="$router.push('/configs')">取消</el-button>
      <el-button type="primary" @click="onSave" :loading="store.loading">保存</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useConfigStore } from '../stores/configStore'

const route = useRoute()
const router = useRouter()
const store = useConfigStore()

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  name: '',
  startUrl: '',
  pageType: null,
  selectorType: null,
  fields: []
})

onMounted(async () => {
  if (isEdit.value) {
    await store.fetchConfigById(route.params.id)
    if (store.current) {
      form.name = store.current.name
      form.startUrl = store.current.startUrl || ''
      form.pageType = store.current.pageType
      form.selectorType = store.current.selectorType
      form.fields = (store.current.fields || []).map(f => ({ ...f }))
    }
  }
})

function addFieldRow() {
  form.fields.push({
    fieldName: '',
    fieldType: 'TEXT',
    pageType: 'LIST',
    selector: ''
  })
}

function removeFieldRow(idx) {
  form.fields.splice(idx, 1)
}

function goPreview() {
  if (route.params.id) router.push(`/configs/${route.params.id}/preview`)
}

async function onSave() {
  if (!form.name || !form.startUrl || !form.pageType || !form.selectorType) {
    ElMessage.warning('请填写名称、起始 URL、页面类型和选择器')
    return
  }
  const payload = {
    name: form.name,
    startUrl: form.startUrl,
    pageType: form.pageType,
    selectorType: form.selectorType,
    fields: form.fields.map(f => ({
      pageType: f.pageType,
      fieldName: f.fieldName,
      fieldType: f.fieldType,
      selector: f.selector
    }))
  }
  try {
    if (isEdit.value) {
      await store.updateConfig(route.params.id, payload)
      ElMessage.success('更新成功')
    } else {
      await store.createConfig(payload)
      ElMessage.success('创建成功')
    }
    router.push('/configs')
  } catch (e) {
    ElMessage.error('保存失败：' + e.message)
  }
}
</script>

<style scoped>
.config-edit {
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
.actions {
  margin-top: 20px;
  text-align: right;
}
</style>
