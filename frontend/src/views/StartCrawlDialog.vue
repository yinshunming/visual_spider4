<template>
  <el-dialog
    title="启动爬取 — DETAIL_ONLY"
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    width="600px"
  >
    <p>请输入要爬取的 URL,每行一个:</p>
    <el-input
      v-model="urlsText"
      type="textarea"
      :rows="8"
      placeholder="https://example.com/article/1&#10;https://example.com/article/2"
    />
    <template #footer>
      <el-button @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" @click="onSubmit">启动</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue', 'submit', 'cancel'])

const urlsText = ref('')

watch(() => props.modelValue, (v) => {
  if (v) urlsText.value = ''
})

function parseUrls() {
  return urlsText.value
    .split('\n')
    .map(s => s.trim())
    .filter(s => s.length > 0)
}

function onSubmit() {
  const urls = parseUrls()
  if (urls.length === 0) {
    return
  }
  emit('submit', urls)
}
</script>