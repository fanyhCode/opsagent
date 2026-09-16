"""
模拟的 order-service（订单服务）

它不是一个真的业务服务，只做两件事：
1. 正常状态：每隔几秒打印一条"订单处理成功"的日志；
2. 故障状态：持续打印"数据库连接池耗尽 / 连接超时"的错误日志，并占满 CPU。

故障开关用"文件是否存在"来控制：
    /app/flag/fault 存在  -> 故障模式
    删掉这个文件          -> 恢复正常

为什么这么设计？因为演示时需要"一键注入故障、一键恢复"，
用文件做开关最简单，不需要重启容器，也不需要改动镜像。
（宿主机上把某个目录挂载到 /app/flag，就能在容器外控制它。）

日志格式刻意模仿 Logback 的样式，让日志看起来像真实的 Java 服务，
这样"用日志排查问题"的演示才真实可信。
"""

import datetime
import os
import random
import socket
import threading
import time

FAULT_FLAG = os.environ.get("FAULT_FLAG", "/app/flag/fault")
SERVICE_NAME = os.environ.get("SERVICE_NAME", "order-service")
POD_IP = socket.gethostbyname(socket.gethostname())

ERROR_MESSAGES = [
    "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
    "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.",
    "HikariPool-1 - Failed to validate connection com.mysql.cj.jdbc.ConnectionImpl@6f2b958e (No operations allowed after connection closed.)",
    "java.sql.SQLNonTransientConnectionException: Communications link failure during rollback(). Transaction resolution unknown.",
]


def log(level, message):
    """按 Logback 风格输出一行日志，并立即刷出（flush=True 很重要，否则 docker logs 看不到实时输出）。"""
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    thread = threading.current_thread().name.replace("Thread", "http-nio-8081-exec-")
    logger = "c.o.order.service.OrderService" if level == "INFO" else "com.zaxxer.hikari.pool.HikariPool"
    print(f"{timestamp} {level:5s} 1 --- [{SERVICE_NAME},{POD_IP}] [{thread}] {logger:45s} : {message}",
          flush=True)


def cpu_burn(stop_event):
    """
    烧 CPU 的线程：故障状态下启动若干条，让这个容器真的把 CPU 打高。
    这样监控里看到的"CPU 飙高"就是真实数据，而不是我们编出来的。
    """
    value = 0
    while not stop_event.is_set():
        for _ in range(200000):
            value += 1
        value = value % 1000000


def main():
    log("INFO", "Starting OrderServiceApplication v1.0.0 using Java 17")
    log("INFO", "Tomcat started on port 8081 (http)")
    log("INFO", "HikariPool-1 - Start completed. maximumPoolSize=10")

    stop_event = threading.Event()
    burners = []
    in_fault = False
    order_id = 100000

    while True:
        fault_now = os.path.exists(FAULT_FLAG)

        # 状态切换时的动作
        if fault_now and not in_fault:
            in_fault = True
            log("WARN", "检测到数据库响应变慢，连接池压力升高，开始重试...")
            # 启动 2 个烧 CPU 的线程（模拟线程阻塞/重试导致的 CPU 飙升）
            for index in range(2):
                thread = threading.Thread(target=cpu_burn, args=(stop_event,),
                                          name=f"Thread-burn-{index}", daemon=True)
                thread.start()
                burners.append(thread)
            log("ERROR", "HikariPool-1 - Connection is not available, request timed out after 30000ms.")

        if not fault_now and in_fault:
            in_fault = False
            stop_event.set()
            burners = []
            stop_event = threading.Event()
            log("INFO", "数据库连接恢复正常，连接池压力下降。")

        if fault_now:
            # 故障模式：高频刷错误日志
            log("ERROR", random.choice(ERROR_MESSAGES))
            time.sleep(0.35)
        else:
            # 正常模式：低频业务日志
            order_id += 1
            if random.random() < 0.25:
                log("INFO", f"订单查询成功 orderId={order_id}, userId={random.randint(1000, 9999)}")
            else:
                log("INFO", f"订单创建成功 orderId={order_id}, amount={random.randint(10, 500)}.00")
            time.sleep(3)


if __name__ == "__main__":
    main()
