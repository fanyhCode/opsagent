<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addDocument,
  deleteDocument,
  listChunks,
  listDocuments,
  reindex,
  searchKnowledge,
  testEmbedding
} from '../api/knowledge'

/**
 * 故障知识库页面。
 *
 * 把 RAG 的每个环节都做成可视化的：
 * 文档（知识源）→ 切片（chunk）→ 向量化（重建索引）→ 语义检索（测试框）。
 * 面试演示时，可以现场输入一个问题，展示"语义检索命中了哪篇案例"。
 */
const router = useRouter()

const loading = ref(false)
const reindexing = ref(false)
const testing = ref(false)
const searching = ref(false)

const documents = ref([])
const chunks = ref([])
const testResult = ref(null)
const reindexResult = ref(null)

const searchQuery = ref('')
const searchResults = ref([])

const addDialogVisible = ref(false)
const chunkDialogVisible = ref(false)
const viewingDocument = ref(null)
const form = reactive({ title: '', category: 'GENERAL', content: '' })

const CATEGORIES = ['LINUX', 'DOCKER', 'JVM', 'MYSQL', 'REDIS', 'GENERAL']

/** 每篇文档的切片数 */
const chunkCountByDocument = computed(() => {
  const map = {}
  chunks.value.forEach((chunk) => {
    map[chunk.documentId] = (map[chunk.documentId] || 0) + 1
  })
  return map
})

/** 已建立索引的切片数（有向量的） */
const indexedChunkCount = computed(() => chunks.value.filter((c) => c.embedding).length)

const viewingChunks = computed(() =>
  chunks.value.filter((c) => c.documentId === viewingDocument.value?.id)
)

async function loadAll() {
  loading.value = true
  try {
    const [docs, allChunks] = await Promise.all([listDocuments(), listChunks()])
    documents.value = docs.data || []
    chunks.value = allChunks.data || []
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleTestEmbedding() {
  testing.value = true
  try {
    const res = await testEmbedding()
    testResult.value = res.data
    ElMessage.success('向量接口正常，维度 ' + res.data.dimension)
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    testing.value = false
  }
}

async function handleReindex() {
  if (documents.value.length === 0) {
    ElMessage.warning('知识库里还没有文档')
    return
  }
  reindexing.value = true
  try {
    const res = await reindex()
    reindexResult.value = res.data
    ElMessage.success(`索引重建完成：${res.data.documents} 篇文档 → ${res.data.chunks} 个切片`)
    loadAll()
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    reindexing.value = false
  }
}

async function handleSearch() {
  const query = searchQuery.value.trim()
  if (!query) {
    ElMessage.warning('请输入要检索的问题')
    return
  }
  searching.value = true
  try {
    const res = await searchKnowledge(query, 3)
    searchResults.value = res.data || []
    if (searchResults.value.length === 0) {
      ElMessage.warning('没有检索到内容，可能还没重建索引')
    }
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    searching.value = false
  }
}

async function handleAddDocument() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和正文都不能为空')
    return
  }
  try {
    await addDocument({ ...form })
    ElMessage.success('文档已添加，记得点"重建索引"后才会被检索到')
    addDialogVisible.value = false
    form.title = ''
    form.content = ''
    form.category = 'GENERAL'
    loadAll()
  } catch (e) {
    // 提示由拦截器统一处理
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除《${row.title}》吗？它的切片也会一并删除。`, '删除文档', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteDocument(row.id)
    ElMessage.success('文档已删除')
    loadAll()
  } catch (e) {
    // 提示由拦截器统一处理
  }
}

function viewChunks(row) {
  viewingDocument.value = row
  chunkDialogVisible.value = true
}

function scoreColor(score) {
  if (score >= 0.75) return '#34d399'
  if (score >= 0.6) return '#fbbf24'
  return '#94a3b8'
}

onMounted(loadAll)
</script>

<template>
  <div class="console">
    <div class="bg-grid"></div>
    <div class="bg-orb"></div>

    <header class="topbar glass-panel">
      <div class="brand">
        <el-button link type="primary" @click="router.push({ name: 'servers' })">← 返回列表</el-button>
        <span class="divider"></span>
        <span class="brand-name">故障知识库</span>
        <span class="brand-sub">RAG 检索增强 · 让 Agent 引用历史案例</span>
      </div>
      <div class="topbar-right">
        <el-button size="small" :loading="testing" @click="handleTestEmbedding">向量自检</el-button>
        <el-button size="small" type="primary" :loading="reindexing" @click="handleReindex">
          <span class="refresh-icon" :class="{ spinning: reindexing }">⟳</span>
          重建索引
        </el-button>
      </div>
    </header>

    <main class="content">
      <!-- 概览 -->
      <section class="stat-row">
        <div class="stat-card glass-panel fade-up">
          <span class="metric-label">知识文档</span>
          <div class="stat-value metric-value">{{ documents.length }}<span class="metric-unit">篇</span></div>
        </div>
        <div class="stat-card glass-panel fade-up delay-1">
          <span class="metric-label">文档切片</span>
          <div class="stat-value metric-value">{{ chunks.length }}<span class="metric-unit">段</span></div>
          <div class="stat-hint">已向量化 {{ indexedChunkCount }} 段</div>
        </div>
        <div class="stat-card glass-panel fade-up delay-2">
          <span class="metric-label">向量模型</span>
          <div class="stat-value small">{{ testResult?.model || reindexResult?.model || '点击右侧"向量自检"查看' }}</div>
          <div class="stat-hint">
            {{ testResult ? '维度 ' + testResult.dimension + ' ｜ 耗时 ' + testResult.elapsedMs + ' ms' : '尚未检测' }}
          </div>
        </div>
      </section>

      <!-- 检索测试 -->
      <section class="panel glass-panel fade-up delay-1">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>语义检索测试</div>
          <div class="panel-meta">用自然语言描述问题，看知识库命中了哪篇案例</div>
        </div>
        <div class="search-row">
          <el-input
            v-model="searchQuery"
            placeholder="例如：服务刚启动没多久就被系统杀掉了，日志里没有报错"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" :loading="searching" @click="handleSearch">检索</el-button>
        </div>

        <div v-if="searchResults.length" class="search-results">
          <div v-for="(item, index) in searchResults" :key="index" class="result-item">
            <div class="result-head">
              <span class="result-rank">#{{ index + 1 }}</span>
              <span class="result-title">{{ item.title }}</span>
              <el-tag size="small" effect="plain">{{ item.category }}</el-tag>
              <span class="result-score" :style="{ color: scoreColor(item.score) }">
                相似度 {{ item.score }}
              </span>
            </div>
            <div class="result-content">{{ item.content }}</div>
          </div>
        </div>
        <div v-else class="empty-tip">输入一个问题试试，比如"连接池耗尽怎么处理"</div>
      </section>

      <!-- 文档列表 -->
      <section class="panel glass-panel fade-up delay-2">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>知识文档</div>
          <el-button size="small" type="primary" plain @click="addDialogVisible = true">
            + 新增文档
          </el-button>
        </div>
        <el-table v-loading="loading" :data="documents" stripe class="ops-table">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字数" width="100">
            <template #default="{ row }">{{ row.content.length }}</template>
          </el-table-column>
          <el-table-column label="切片" width="100">
            <template #default="{ row }">
              {{ chunkCountByDocument[row.id] || 0 }} 段
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="viewChunks(row)">查看切片</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </main>

    <!-- 新增文档 -->
    <el-dialog v-model="addDialogVisible" title="新增知识文档" width="640px">
      <el-form label-width="70px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：Nginx 502 错误排查" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="建议按「现象 → 排查步骤 → 常见根因 → 处置建议」的结构来写，检索效果更好"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddDocument">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看切片 -->
    <el-dialog
      v-model="chunkDialogVisible"
      :title="`切片预览 · ${viewingDocument?.title || ''}`"
      width="760px"
    >
      <div v-if="viewingChunks.length === 0" class="empty-tip">这篇文档还没有切片，请点"重建索引"</div>
      <div v-for="chunk in viewingChunks" :key="chunk.id" class="chunk-item">
        <div class="chunk-head">
          第 {{ chunk.chunkIndex + 1 }} 段
          <span class="chunk-vector">
            {{ chunk.embedding ? '已向量化（' + JSON.parse(chunk.embedding).length + ' 维）' : '未向量化' }}
          </span>
        </div>
        <div class="chunk-content">{{ chunk.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.console {
  position: relative;
  min-height: 100vh;
  padding: 18px 22px 30px;
  overflow: hidden;
  background: radial-gradient(circle at 15% 0%, #0f2233 0%, #070c15 45%, #04070d 100%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(34, 211, 238, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: 42px 42px;
  pointer-events: none;
}

.bg-orb {
  position: absolute;
  width: 460px;
  height: 460px;
  right: -160px;
  top: -180px;
  border-radius: 50%;
  background: #22d3ee;
  opacity: 0.14;
  filter: blur(100px);
  pointer-events: none;
}

.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  margin-bottom: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.divider {
  width: 1px;
  height: 18px;
  background: rgba(148, 163, 184, 0.22);
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.6px;
  background: linear-gradient(90deg, #e2e8f0, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-sub {
  font-size: 12.5px;
  color: #7d90a8;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.refresh-icon {
  display: inline-block;
  margin-right: 4px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

.content {
  position: relative;
  z-index: 2;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  padding: 18px 20px 16px;
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.35s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 22px 46px rgba(2, 6, 23, 0.6), 0 0 0 1px rgba(34, 211, 238, 0.22);
}

.stat-value {
  margin-top: 8px;
  font-size: 30px;
  color: #e2e8f0;
}

.stat-value.small {
  font-size: 17px;
  font-weight: 600;
  word-break: break-all;
}

.stat-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #7d90a8;
}

.panel {
  padding: 18px 20px;
  margin-bottom: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.title-mark {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #22d3ee, #6366f1);
}

.panel-meta {
  font-size: 12.5px;
  color: #7d90a8;
}

.search-row {
  display: flex;
  gap: 12px;
}

.search-results {
  margin-top: 16px;
}

.result-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.14);
  transition: all 0.3s ease;
}

.result-item:hover {
  border-color: rgba(34, 211, 238, 0.35);
  background: rgba(34, 211, 238, 0.06);
}

.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.result-rank {
  font-size: 12px;
  color: #7d90a8;
  font-weight: 700;
}

.result-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.result-score {
  margin-left: auto;
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.result-content {
  font-size: 12.5px;
  line-height: 1.7;
  color: #93a7bd;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-tip {
  padding: 34px 0;
  text-align: center;
  font-size: 13px;
  color: #7d90a8;
}

.chunk-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.chunk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12.5px;
  color: #7dd3fc;
}

.chunk-vector {
  color: #7d90a8;
  font-size: 12px;
}

.chunk-content {
  font-size: 13px;
  line-height: 1.75;
  color: #cbd5e1;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .console {
    padding: 12px 12px 24px;
  }
  .brand-sub {
    display: none;
  }
  .search-row {
    flex-direction: column;
  }
}
</style>
