#!/bin/bash

# Daily Satori 测试运行脚本
# 用于在 Android 设备上运行集成测试

echo "🚀 Daily Satori 集成测试启动"
echo "=================================="

# 检查设备连接状态
echo "📱 检查设备连接..."
if ! flutter devices | grep -q "android"; then
    echo "❌ 没有找到 Android 设备，请确保设备已连接并开启调试模式"
    exit 1
fi

echo "✅ Android 设备已连接"

# 获取设备ID
DEVICE_ID=$(flutter devices | grep android | awk '{print $2}')
echo "📱 使用设备: $DEVICE_ID"

# 安装依赖
echo "📦 安装依赖..."
flutter pub get

# 运行不同类型的测试
echo ""
echo "🧪 开始运行测试..."
echo "=================================="

# 1. 快速测试（基础功能）
echo "1️⃣ 运行快速测试..."
if flutter test integration_test/quick_test.dart -d $DEVICE_ID --timeout=90000; then
    echo "✅ 快速测试通过"
else
    echo "❌ 快速测试失败"
fi

echo ""

# 2. 基础UI测试
echo "2️⃣ 运行基础UI测试..."
if flutter test integration_test/basic_test.dart -d $DEVICE_ID --name="Material Design 基础组件测试" --timeout=120000; then
    echo "✅ 基础UI测试通过"
else
    echo "❌ 基础UI测试失败"
fi

echo ""

# 3. 单元测试（如果存在）
echo "3️⃣ 运行单元测试..."
if flutter test test/unit/ --no-pub --machine 2>/dev/null; then
    echo "✅ 单元测试通过"
else
    echo "⚠️ 单元测试不存在或失败"
fi

echo ""
echo "🎉 测试运行完成！"
echo "=================================="

# 显示测试报告位置
echo "📊 测试报告:"
echo "   - 详细的测试日志保存在控制台输出中"
echo "   - 如需覆盖率报告，请运行: flutter test --coverage"

echo ""
echo "💡 其他测试命令:"
echo "   - 运行单个测试: flutter test integration_test/quick_test.dart -d $DEVICE_ID"
echo "   - 运行所有测试: flutter test -d $DEVICE_ID"
echo "   - 生成覆盖率: flutter test --coverage"
echo "   - 查看可用设备: flutter devices"