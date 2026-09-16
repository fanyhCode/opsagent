# ============================================================
# 前端镜像：多阶段构建
# 第一阶段用 Node 打包出静态文件，第二阶段用 Nginx 托管。
# 同时 Nginx 还承担反向代理职责：把 /api 请求转发给后端容器。
# ============================================================

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build

WORKDIR /app

# 国内网络下用 npmmirror 镜像装依赖，速度快很多
RUN npm config set registry https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
