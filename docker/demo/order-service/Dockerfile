# 用轻量的 Python 镜像做模拟服务：
# 一个 Spring Boot 服务本身就要吃 300~500MB 内存，而我们要的只是"产生真实形态的日志"，
# 用 Python 能把资源占用压到几 MB，虚拟机跑起来更轻松。
FROM python:3.12-alpine

WORKDIR /app

COPY order_service.py .

# 故障开关目录，宿主机通过挂载卷来控制
VOLUME ["/app/flag"]

CMD ["python", "-u", "order_service.py"]
