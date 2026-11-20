# Limbo ANIPL Plus

基于 Limbo Emulator 深度定制的 Android QEMU 前端，支持多架构虚拟机模拟、Inferno iOS 模拟实验、VirtIO 设备、Gunyah/GZVM 加速等功能。

A deeply customized Android QEMU frontend based on Limbo Emulator, supporting multi-architecture VM emulation, experimental Inferno iOS simulation, VirtIO devices, and Gunyah/GZVM acceleration.

---

## 📖 语言选择 / Language Selection

- [中文](#中文)
- [English](#english)

---

# 中文

## ✨ 功能特性

### 🖥️ 多架构支持

- **x86_64** — 传统 PC 模拟（Windows / Linux）
- **AArch64** — ARM64 Linux 模拟
- **PPC** — PowerPC 模拟
- **SPARC** — SPARC 模拟
- **Inferno AArch64** — 基于 Inferno 的 iOS 模拟实验分支

每台虚拟机独立保存架构配置，新建时可选择，列表中显示架构标识。

### 🎮 图形与加速

- **VirtIO GPU** — `virtio-gpu-pci` / `virtio-gpu-gl-pci` / `virtio-vga-gl`
- **Zink 模式** — `venus=on,blob=on,hostmem=1G`（Vulkan 直通）
- **GLES 后端切换** — 系统 GLES / ANGLE (Vulkan)
- **KVM** — x86 / ARM 硬件加速
- **Gunyah / GZVM** — aarch64 专用加速器（基于 ALS）
- **TCG** — 单线程 / MTTCG 多线程

四种加速（KVM / TCG / Gunyah / GZVM）互斥，勾选一个自动取消其他。

### 💾 存储与设备

- IDE / SCSI / VirtIO / NVMe 磁盘接口
- VirtIO Balloon（内存气球）
- VirtIO Serial / RNG / 9P 共享文件夹 / Vsock
- USB 控制器：EHCI / xHCI / OHCI / UHCI
- 可移动设备：CD-ROM / 软盘 / SD 卡

### 🎨 界面

- **Material DayNight** — 跟随系统 / 浅色 / 深色手动切换
- **底部导航** — 虚拟机 / 线上 / 设置 / 关于
- **虚拟机列表** — 显示名称与架构，支持启动、配置、删除
- **引导流程** — 首次启动：虚拟化检测 → 许可证 → 存储权限 → BIOS 安装
- **圆角 UI** — 卡片、对话框、下拉框、弹出菜单统一圆角
- **Vector 图标** — 全部使用 vector drawable，适配深浅模式

### 🌐 线上资源

- VirtIO-Win 驱动下载
- Linux ISO 镜像下载（Debian / Ubuntu / Alpine）
- BIOS / UEFI 固件下载
- Limbo 版本更新检查
- 官方 Wiki / 社区交流

### 🛠️ 高级功能

- QEMU Monitor（Unix socket）
- QMP 远程控制
- 快照（savevm / loadvm / delvm）
- 虚拟机导入 / 导出
- 自定义 QEMU 额外参数
- 按键映射器
- VNC 服务器

---

## 📥 下载

从 [Releases](https://github.com/ANIPLXUAN/limbo_aniplus/releases) 页面下载最新 APK。

### 系统要求

- Android 8.0 及以上
- 推荐 Android 12.0 及以上
- ARM64 设备（aarch64 加速功能需要内核支持 Gunyah/GZVM）

---

## 🚀 快速开始

### 1. 新建虚拟机

1. 打开 App，进入 **虚拟机** 标签
2. 点击右下角 **新建** 按钮
3. 选择模拟架构（x86_64 / ppc / aarch64 / inferno_aarch64 / sparc）
4. 输入虚拟机名称
5. 进入配置界面，设置 CPU、内存、磁盘、网络等

### 2. 配置磁盘

1. 在配置界面找到 **磁盘** 区域
2. 点击磁盘选项，选择或创建镜像文件
3. 支持 `.img`（raw）、`.qcow2`、`.iso` 等格式

### 3. 启动虚拟机

- 在列表中点击虚拟机名称或 ▶ 图标直接启动
- 点击右侧 ⋮ 图标进入配置界面

### 4. 切换主题

**设置 → 界面 → 主题**，可选：

- 跟随系统
- 浅色
- 深色

---

## 🔧 从源码构建

### 环境要求

| 工具 | 版本 |
|---|---|
| Android Studio | Hedgehog 或更高 |
| JDK | 17 |
| Android SDK | API 29 |
| Android NDK | r25c 或更高 |
| Gradle | 8.2 |

### 构建步骤

```bash
git clone https://github.com/ANIPLXUAN/limbo_qemu.git
cd limbo_qemu

# 编译 x86 版本
./gradlew :limbo-android-x86:assembleDebug

# APK 输出路径
# limbo-android-x86/build/outputs/apk/debug/limbo-android-x86-debug.apk
安装到设备
bash
adb install -r limbo-android-x86/build/outputs/apk/debug/limbo-android-x86-debug.apk
原生库准备
QEMU 二进制和依赖库需要单独编译或从 Releases 获取，放入：

text
limbo-android-lib/src/main/jniLibs/arm64/
 ├─ libqemu-system-x86_64-11.1.1.so
 ├─ libqemu-system-aarch64.so
 ├─ libqemu-system-aarch64-v2.so      # Inferno
 ├─ libqemu-system-ppc.so
 ├─ libqemu-system-sparc.so
 ├─ libSDL2.so
 ├─ liblimbo.so
 ├─ libepoxy.so
 ├─ libEGL_angle.so
 ├─ libGLESv2_angle.so
 └─ ...
📂 项目结构
text
limbo_qemu/
├── limbo-android-lib/          # 核心库（Java + JNI + 资源）
│   ├── src/main/java/          # Java 源码
│   │   └── com/max2idea/android/limbo/
│   │       ├── main/           # 主界面、SDL 界面
│   │       ├── machine/        # 虚拟机数据模型、控制器
│   │       ├── jni/            # QEMU JNI 封装
│   │       ├── monitor/        # QEMU Monitor 客户端
│   │       ├── qmp/            # QMP 客户端
│   │       ├── files/          # 文件管理
│   │       └── ...
│   ├── src/main/jni/           # C 源码
│   │   ├── vm-executor-jni.c   # JNI 桥接层
│   │   └── ...
│   ├── src/main/res/           # 资源
│   │   ├── layout/             # 布局
│   │   ├── drawable/           # Vector 图标
│   │   ├── values/             # 浅色主题资源
│   │   ├── values-night/       # 深色主题资源
│   │   ├── raw/                # 架构设备列表
│   │   └── xml/                # 设置界面
│   └── src/main/assets/        # 许可证、固件
│       ├── LICENSE             # GPL v2 全文
│       ├── LICENSE_GPLv3       # GPL v3 全文
│       └── QEMU_VERSION
├── limbo-android-x86/          # x86 构建入口
├── limbo-android-arm/          # ARM 构建入口
├── limbo-android-ppc/          # PPC 构建入口
├── limbo-android-sparc/        # SPARC 构建入口
└── README.md
🧩 相关项目
Limbo Emulator — 原始项目，Max Kastanas 开发
https://github.com/limboemu/limbo

QEMU — 通用机器模拟器
https://www.qemu.org

ALS (AnyLaySys) — Gunyah / GZVM 加速
https://github.com/AnyLaySys/als

SDL2 — 跨平台多媒体库
https://www.libsdl.org

virglrenderer — VirtIO GPU 渲染器
https://gitlab.freedesktop.org/virgl/virglrenderer

🤝 贡献
欢迎提交 Issue 和 Pull Request。

Fork 本仓库

创建特性分支 (git checkout -b feature/xxx)

提交改动 (git commit -m 'feat: add xxx')

推送分支 (git push origin feature/xxx)

提交 Pull Request

📜 许可证
本项目整体遵循 GNU General Public License v3.0 发布。

原始 Limbo 代码：Copyright (C) Max Kastanas 2012，GPL-2.0-or-later

本项目修改：Copyright (C) 2024-2026 ANIPLXUAN，GPL-3.0-or-later

完整许可证文本：

COPYING — GPL v3.0 全文

COPYING.GPLv2 — GPL v2.0 全文（原始 Limbo）

第三方组件许可
组件	许可证
Limbo (original)	GPL-2.0-or-later
QEMU	GPL-2.0-or-later
SDL2	zlib License
virglrenderer	MIT License
ALS (Gunyah-qemu / GZVM-qemu)	参见 ALS LICENSE
🙏 致谢
Limboemu — Limbo Emulator 原作者

AnyLaySys — Gunyah / GZVM 加速实现

QEMU 团队 — 强大的机器模拟器

所有 Limbo 社区贡献者

📮 联系方式
GitHub Issues: 提交问题

GitHub Discussions: 参与讨论

QQ 群: 点击加入

<p align="center"> <b>Limbo ANIPL Plus</b><br> Made with ❤️ by ANIPLXUAN </p>
English
✨ Features
🖥️ Multi-Architecture Support
x86_64 — Traditional PC emulation (Windows / Linux)

AArch64 — ARM64 Linux emulation

PPC — PowerPC emulation

SPARC — SPARC emulation

Inferno AArch64 — Experimental iOS emulation branch based on Inferno

Each VM stores its own architecture configuration. Architecture can be selected at creation time and is displayed in the VM list.

🎮 Graphics & Acceleration
VirtIO GPU — virtio-gpu-pci / virtio-gpu-gl-pci / virtio-vga-gl

Zink Mode — venus=on,blob=on,hostmem=1G (Vulkan passthrough)

GLES Backend Switching — System GLES / ANGLE (Vulkan)

KVM — x86 / ARM hardware acceleration

Gunyah / GZVM — aarch64 dedicated accelerators (based on ALS)

TCG — Single-threaded / MTTCG multi-threaded

The four accelerators (KVM / TCG / Gunyah / GZVM) are mutually exclusive; selecting one automatically deselects the others.

💾 Storage & Devices
IDE / SCSI / VirtIO / NVMe disk interfaces

VirtIO Balloon (memory ballooning)

VirtIO Serial / RNG / 9P shared folders / Vsock

USB controllers: EHCI / xHCI / OHCI / UHCI

Removable devices: CD-ROM / floppy / SD card

🎨 User Interface
Material DayNight — Follow system / Light / Dark manual switching

Bottom Navigation — VM / Online / Settings / About

VM List — Displays name and architecture, supports launch, configure, delete

Onboarding Flow — First launch: virtualization check → license → storage permission → BIOS install

Rounded UI — Cards, dialogs, spinners, popup menus with unified rounded corners

Vector Icons — All vector drawables, adapted for light/dark themes

🌐 Online Resources
VirtIO-Win driver download

Linux ISO downloads (Debian / Ubuntu / Alpine)

BIOS / UEFI firmware downloads

Limbo update checker

Official Wiki / Community

🛠️ Advanced Features
QEMU Monitor (Unix socket)

QMP remote control

Snapshots (savevm / loadvm / delvm)

VM import / export

Custom QEMU extra parameters

Key mapper

VNC server

📥 Download
Download the latest APK from the Releases page.

Requirements
Android 8.0 or higher

Android 12.0 or higher recommended

ARM64 device (aarch64 acceleration features require kernel support for Gunyah/GZVM)

🚀 Quick Start
1. Create a VM
Open the app and go to the VM tab

Tap the New button in the bottom-right corner

Select the emulation architecture (x86_64 / ppc / aarch64 / inferno_aarch64 / sparc)

Enter the VM name

Configure CPU, memory, disks, and network in the configuration screen

2. Configure Disks
Locate the Disks section in the configuration screen

Tap a disk option to select or create an image file

Supported formats: .img (raw), .qcow2, .iso

3. Launch a VM
Tap the VM name or the ▶ icon in the list to launch directly

Tap the ⋮ icon to enter the configuration screen

4. Switch Theme
Settings → Interface → Theme, choose from:

Follow system

Light

Dark

🔧 Building from Source
Requirements
Tool	Version
Android Studio	Hedgehog or later
JDK	17
Android SDK	API 29
Android NDK	r25c or later
Gradle	8.2
Build Steps
bash
git clone https://github.com/ANIPLXUAN/limbo_qemu.git
cd limbo_qemu

# Build x86 variant
./gradlew :limbo-android-x86:assembleDebug

# APK output path
# limbo-android-x86/build/outputs/apk/debug/limbo-android-x86-debug.apk
Install to Device
bash
adb install -r limbo-android-x86/build/outputs/apk/debug/limbo-android-x86-debug.apk
Native Libraries
QEMU binaries and dependencies must be compiled separately or obtained from Releases, and placed in:

text
limbo-android-lib/src/main/jniLibs/arm64/
 ├─ libqemu-system-x86_64-11.1.1.so
 ├─ libqemu-system-aarch64.so
 ├─ libqemu-system-aarch64-v2.so      # Inferno
 ├─ libqemu-system-ppc.so
 ├─ libqemu-system-sparc.so
 ├─ libSDL2.so
 ├─ liblimbo.so
 ├─ libepoxy.so
 ├─ libEGL_angle.so
 ├─ libGLESv2_angle.so
 └─ ...
📂 Project Structure
text
limbo_qemu/
├── limbo-android-lib/          # Core library (Java + JNI + resources)
│   ├── src/main/java/          # Java sources
│   │   └── com/max2idea/android/limbo/
│   │       ├── main/           # Main UI, SDL UI
│   │       ├── machine/        # VM data model, controller
│   │       ├── jni/            # QEMU JNI wrapper
│   │       ├── monitor/        # QEMU Monitor client
│   │       ├── qmp/            # QMP client
│   │       ├── files/          # File management
│   │       └── ...
│   ├── src/main/jni/           # C sources
│   │   ├── vm-executor-jni.c   # JNI bridge
│   │   └── ...
│   ├── src/main/res/           # Resources
│   │   ├── layout/             # Layouts
│   │   ├── drawable/           # Vector icons
│   │   ├── values/             # Light theme resources
│   │   ├── values-night/       # Dark theme resources
│   │   ├── raw/                # Architecture device lists
│   │   └── xml/                # Settings UI
│   └── src/main/assets/        # Licenses, firmware
│       ├── LICENSE             # GPL v2 full text
│       ├── LICENSE_GPLv3       # GPL v3 full text
│       └── QEMU_VERSION
├── limbo-android-x86/          # x86 build entry
├── limbo-android-arm/          # ARM build entry
├── limbo-android-ppc/          # PPC build entry
├── limbo-android-sparc/        # SPARC build entry
└── README.md
🧩 Related Projects
Limbo Emulator — Original project by Max Kastanas
https://github.com/limboemu/limbo

QEMU — Generic machine emulator
https://www.qemu.org

ALS (AnyLaySys) — Gunyah / GZVM acceleration
https://github.com/AnyLaySys/als

SDL2 — Cross-platform multimedia library
https://www.libsdl.org

virglrenderer — VirtIO GPU renderer
https://gitlab.freedesktop.org/virgl/virglrenderer

🤝 Contributing
Issues and Pull Requests are welcome.

Fork this repository

Create a feature branch (git checkout -b feature/xxx)

Commit your changes (git commit -m 'feat: add xxx')

Push to the branch (git push origin feature/xxx)

Open a Pull Request

📜 License
This project is distributed under the GNU General Public License v3.0.

Original Limbo code: Copyright (C) Max Kastanas 2012, GPL-2.0-or-later

Project modifications: Copyright (C) 2024-2026 ANIPLXUAN, GPL-3.0-or-later

Full license texts:

COPYING — GPL v3.0 full text

COPYING.GPLv2 — GPL v2.0 full text (original Limbo)

Third-Party Components
Component	License
Limbo (original)	GPL-2.0-or-later
QEMU	GPL-2.0-or-later
SDL2	zlib License
virglrenderer	MIT License
ALS (Gunyah-QEMU / GZVM-QEMU)	See ALS LICENSE
🙏 Acknowledgements
Limboemu — Original author of Limbo Emulator

AnyLaySys — Gunyah / GZVM acceleration implementation

QEMU Team — Powerful machine emulator

All Limbo community contributors

📮 Contact
GitHub Issues: Open an issue

GitHub Discussions: Join the discussion

QQ Group: Click to join

<p align="center"> <b>Limbo ANIPL Plus</b><br> Made with ❤️ by ANIPLXUAN </p> ```
