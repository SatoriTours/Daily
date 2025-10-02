#!/bin/bash

# Daily Satori 测试调试脚本
# 用于调试和验证测试环境

echo "🔧 Daily Satori 测试环境调试"
echo "=================================="

# 检查Flutter环境
echo "📋 检查Flutter环境..."
flutter doctor -v

# 检查设备
echo ""
echo "📱 检查可用设备..."
flutter devices

# 检查模拟器
echo ""
echo "🎮 检查可用模拟器..."
flutter emulators

# 清理项目
echo ""
echo "🧹 清理项目..."
flutter clean

# 获取依赖
echo ""
echo "📦 获取依赖..."
flutter pub get

# 构建应用（用于验证编译）
echo ""
echo "🔨 构建应用..."
flutter build apk --debug

# 运行快速测试
echo ""
echo "⚡ 运行快速测试验证..."
flutter test integration_test/quick_test.dart -d emulator-5554 --timeout=60000 --verbose

echo ""
echo "✅ 调试完成！"