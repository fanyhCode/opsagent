<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 粒子背景组件。
 *
 * 用原生 Canvas 画一层"科技感"的粒子网络：
 * - 粒子缓慢漂浮；
 * - 距离较近的粒子之间连一条半透明线（像服务之间的调用关系）；
 * - 鼠标移动时，附近粒子会被轻轻"推开"，产生互动感。
 *
 * 为什么不用第三方粒子库？因为几十行原生代码就够了，
 * 少一个依赖就少一份打包体积和安全风险——这也是工程上的取舍。
 */
const canvasRef = ref(null)

let ctx = null
let animationId = null
let particles = []
let width = 0
let height = 0
const mouse = { x: -9999, y: -9999 }

/** 根据屏幕大小决定粒子数量，避免小屏卡顿、大屏太空 */
function particleCount() {
  const area = width * height
  return Math.min(110, Math.max(40, Math.round(area / 22000)))
}

function createParticles() {
  particles = Array.from({ length: particleCount() }, () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    vx: (Math.random() - 0.5) * 0.35,
    vy: (Math.random() - 0.5) * 0.35,
    r: Math.random() * 1.8 + 0.6
  }))
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  width = canvas.clientWidth
  height = canvas.clientHeight
  canvas.width = width * dpr
  canvas.height = height * dpr
  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  createParticles()
}

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)

  // 先画连线：距离越近越明显
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x
      const dy = particles[i].y - particles[j].y
      const dist = Math.hypot(dx, dy)
      if (dist < 130) {
        ctx.strokeStyle = `rgba(34, 211, 238, ${0.16 * (1 - dist / 130)})`
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(particles[i].x, particles[i].y)
        ctx.lineTo(particles[j].x, particles[j].y)
        ctx.stroke()
      }
    }
  }

  // 再画粒子本身
  particles.forEach((p) => {
    // 鼠标靠近时产生排斥，制造轻微互动
    const dx = p.x - mouse.x
    const dy = p.y - mouse.y
    const dist = Math.hypot(dx, dy)
    if (dist < 120) {
      p.x += (dx / dist) * 0.6
      p.y += (dy / dist) * 0.6
    }

    p.x += p.vx
    p.y += p.vy

    // 撞到边缘就反弹，保证粒子始终在屏幕内
    if (p.x < 0 || p.x > width) p.vx *= -1
    if (p.y < 0 || p.y > height) p.vy *= -1

    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = 'rgba(125, 211, 252, 0.75)'
    ctx.fill()
  })

  animationId = requestAnimationFrame(draw)
}

function onMouseMove(e) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
}

onMounted(() => {
  resize()
  draw()
  window.addEventListener('resize', resize)
  window.addEventListener('mousemove', onMouseMove)
})

onUnmounted(() => {
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', resize)
  window.removeEventListener('mousemove', onMouseMove)
})
</script>

<template>
  <canvas ref="canvasRef" class="particle-canvas"></canvas>
</template>

<style scoped>
.particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}
</style>
