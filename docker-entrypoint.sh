#!/bin/sh
set -e

# 按宿主实际透传的硬件决定是否需要（以及装什么）VA 驱动，避免在镜像里写死某个厂商。
# 注意：NVIDIA 环境应使用 `docker run --gpus all`（宿主安装 nvidia-container-toolkit），
# NVENC 会由 nvidia runtime 注入库，本脚本不处理、也不需要处理 NVIDIA 驱动。

AUTO_INSTALL_VA_DRIVERS=${AUTO_INSTALL_VA_DRIVERS:-true}

has_libva_driver() {
  find /usr/lib -name '*_drv_video.so' 2>/dev/null | grep -q .
}

if [ "$AUTO_INSTALL_VA_DRIVERS" = "true" ] && [ -d /dev/dri ] && [ -n "$(ls /dev/dri/renderD* 2>/dev/null || true)" ]; then
  if ! has_libva_driver; then
    echo "[entrypoint] 检测到 /dev/dri（Intel/AMD 核显透传），但容器内没有 libva 驱动，尝试自动安装"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update >/dev/null 2>&1 || true
    installed=""
    for pkg in intel-media-va-driver-non-free intel-media-va-driver i965-va-driver mesa-va-drivers; do
      if apt-get install -y --no-install-recommends "$pkg" >/dev/null 2>&1; then
        echo "[entrypoint] 已安装 VA 驱动包：$pkg"
        installed="$pkg"
        break
      fi
    done
    rm -rf /var/lib/apt/lists/*
    if [ -z "$installed" ] && ! has_libva_driver; then
      echo "[entrypoint] 未能自动安装 VA 驱动（可能无网络/无匹配包），将回退软件转码 libx264"
    fi
  fi
fi

exec java -Djava.security.egd=file:/dev/./urandom -jar /app.jar
