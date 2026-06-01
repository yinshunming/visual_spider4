import { createRouter, createWebHistory } from 'vue-router'
import ConfigList from '../views/ConfigList.vue'
import ConfigEdit from '../views/ConfigEdit.vue'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
