#!/bin/bash

# Daily Satori 快速测试脚本
# 用于快速验证应用核心功能

echo "⚡ Daily Satori 快速测试"
echo "======================"

# 检查设备连接
echo "📱 检查设备连接..."
if ! flutter devices | grep -q "android"; then
    echo "❌ 没有找到 Android 设备"
    exit 1
fi

DEVICE_ID=$(flutter devices | grep android | head -1 | awk '{print $2}')
echo "✅ 使用设备: $DEVICE_ID"

# 安装依赖
echo "📦 安装依赖..."
flutter pub get

# 只运行关键测试
echo ""
echo "🧪 运行关键测试..."
echo "=================="

# 1. 快速基础测试
echo "1️⃣ 快速基础测试..."
if flutter test integration_test/quick_test.dart -d $DEVICE_ID --timeout=60000; then
    echo "✅ 快速基础测试通过"
else
    echo "❌ 快速基础测试失败"
    exit 1
fi

# 2. 全面功能测试（简化版）
echo "2️⃣ 核心功能测试..."
if flutter test integration_test/comprehensive_test.dart -d $DEVICE_ID --timeout=120000; then
    echo "✅ 核心功能测试通过"
else
    echo "❌ 核心功能测试失败"
    exit 1
fi

echo ""
echo "🎉 快速测试完成！所有核心功能正常！"
echo "=================================="
echo ""
echo "💡 如需完整测试，请运行: ./run_tests.sh"