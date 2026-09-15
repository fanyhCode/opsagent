<script setup>
import { useTokenKeepAlive } from './composables/useTokenKeepAlive'

/**
 * 根组件。
 *
 * 这里只做一件事：启动"令牌保活"。
 * 放在根组件是为了保证整个应用生命周期内只启动一次，
 * 且用户在任何页面停留都能生效（不用每个页面各写一遍）。
 */
useTokenKeepAlive()
</script>

<template>
  <!-- 整个应用只有一个出口：显示哪个页面由路由决定 -->
  <router-view />
</template>
