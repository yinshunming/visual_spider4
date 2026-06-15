import { createRouter, createWebHistory } from 'vue-router'
import ConfigList from '../views/ConfigList.vue'
import ConfigEdit from '../views/ConfigEdit.vue'
import PagePreview from '../views/PagePreview.vue'
import TaskList from '../views/TaskList.vue'
import TaskDetail from '../views/TaskDetail.vue'

const routes = [
  {
    path: '/',
    redirect: '/configs'
  },
  {
    path: '/configs',
    name: 'config-list',
    component: ConfigList
  },
  {
    path: '/configs/new',
    name: 'config-new',
    component: ConfigEdit
  },
  {
    path: '/configs/:id',
    name: 'config-edit',
    component: ConfigEdit,
    props: true
  },
  {
    path: '/configs/:id/preview',
    name: 'config-preview',
    component: PagePreview,
    props: true
  },
  {
    path: '/tasks',
    name: 'task-list',
    component: TaskList
  },
  {
    path: '/tasks/:id',
    name: 'task-detail',
    component: TaskDetail,
    props: true
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
