import { ref, watch } from 'vue'

/**
 * 数字滚动动画（组合式函数）。
 *
 * 传入一个响应式数字，返回值会从 0 平滑增长到目标值，
 * 用在统计卡片上，比直接显示数字更有"仪表盘"的感觉。
 *
 * 实现要点：用 requestAnimationFrame 而不是 setInterval，
 * 前者跟屏幕刷新同步，动画更顺滑，页面不可见时浏览器会自动降频。
 */
export function useCountUp(target, duration = 900) {
  const value = ref(0)
  let animationId = null

  function run(to) {
    cancelAnimationFrame(animationId)
    const from = value.value
    const startTime = performance.now()

    const step = (now) => {
      const progress = Math.min(1, (now - startTime) / duration)
      // 缓出曲线：开始快、结尾慢，看起来更自然
      const eased = 1 - Math.pow(1 - progress, 3)
      value.value = Math.round(from + (to - from) * eased)
      if (progress < 1) {
        animationId = requestAnimationFrame(step)
      }
    }

    animationId = requestAnimationFrame(step)
  }

  watch(
    target,
    (newValue) => run(Number(newValue) || 0),
    { immediate: true }
  )

  return value
}
