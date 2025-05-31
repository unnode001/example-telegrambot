#!/bin/bash

# --- 配置部分 ---
IMAGE_NAME="unnode001/example-telegrambot"
IMAGE_TAG="latest"
CONTAINER_NAME="example-telegrambot-container"
HOST_PORT="8080"      # 宿主机端口 (在 Cloud Shell 中这通常是容器的公开端口)
CONTAINER_PORT="8080" # 容器内部端口 (请根据你的应用实际端口修改，Spring Boot 默认是 8080)
# --- 配置结束 ---

echo "--- 开始部署 Docker 镜像 ---"

# 1. 停止并移除已存在的同名容器（如果存在）
echo "尝试停止并移除旧的容器 '${CONTAINER_NAME}' (如果存在)..."
# 使用 || true 是为了防止 docker stop/rm 失败时脚本中断
docker stop "${CONTAINER_NAME}" &>/dev/null || true
docker rm "${CONTAINER_NAME}" &>/dev/null || true
echo "旧容器 '${CONTAINER_NAME}' 已清理。"

# 2. 拉取最新的 Docker 镜像
FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
echo "正在拉取 Docker 镜像：${FULL_IMAGE_NAME}..."
if ! docker pull "${FULL_IMAGE_NAME}"; then
    echo "错误：无法拉取 Docker 镜像 '${FULL_IMAGE_NAME}'。请检查网络连接或镜像名称。"
    exit 1
fi
echo "镜像 '${FULL_IMAGE_NAME}' 拉取成功。"

# 3. 运行 Docker 容器
echo "正在运行 Docker 容器 '${CONTAINER_NAME}'..."
# -d: 后台运行容器
# -p: 端口映射 (宿主机端口:容器内部端口)。在 Cloud Shell 中，这里的主机端口通常就是容器的暴露端口。
# --name: 指定容器名称
# -e: 传递环境变量给容器 (非常重要，用于你的机器人 Token 和用户名)
if ! docker run -d -p "${HOST_PORT}:${CONTAINER_PORT}" \
                -e "TELEGRAM_BOT_NAME=${TELEGRAM_BOT_NAME}" \
                -e "TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}" \
                -e "TELEGRAM_REVIEW_GROUP_ID=${TELEGRAM_REVIEW_GROUP_ID}" \
                -e "TELEGRAM_PUBLIC_GROUP_ID=${TELEGRAM_PUBLIC_GROUP_ID}" \
                --name "${CONTAINER_NAME}" "${FULL_IMAGE_NAME}"; then
    echo "错误：无法运行 Docker 容器 '${CONTAINER_NAME}'。可能端口 '${HOST_PORT}' 已被占用或容器内部应用配置问题。"
    exit 1
fi
echo "容器 '${CONTAINER_NAME}' 已成功启动。"

# 4. 显示容器状态
echo "当前运行的 Docker 容器状态："
docker ps | grep "${CONTAINER_NAME}"

echo "--- 部署完成！---"
echo "如果你的机器人是Webhook模式，请确保你已经设置了正确的Webhook URL指向Cloud Shell的公共IP和端口。"
echo "对于长轮询模式，机器人应该已经开始工作。"
echo "你也可以使用 'docker logs ${CONTAINER_NAME}' 查看容器日志。"