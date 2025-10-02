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

# 记录测试开始时间
START_TIME=$(date +%s)
TOTAL_TESTS=0
PASSED_TESTS=0

# 测试函数
run_test() {
    local test_name="$1"
    local test_file="$2"
    local timeout="${3:-120000}"

    echo ""
    echo "🧪 运行 $test_name..."
    echo "文件: $test_file"
    echo "超时: ${timeout}ms"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if flutter test integration_test/$test_file -d $DEVICE_ID --timeout=$timeout; then
        echo "✅ $test_name 通过"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        echo "❌ $test_name 失败"
        return 1
    fi
}

# 1. 快速测试（基础功能）
run_test "快速测试" "quick_test.dart" "90000"

# 2. 基础UI测试
run_test "基础UI测试" "basic_test.dart" "120000"

# 3. 全面功能测试
run_test "全面功能测试" "comprehensive_test.dart" "180000"

# 4. 性能和内存测试
run_test "性能和内存测试" "performance_test.dart" "240000"

# 5. 读书管理功能测试
run_test "读书管理测试" "books_test.dart" "150000"

# 6. 日记功能测试
run_test "日记功能测试" "diary_test.dart" "150000"

# 7. 文章管理功能测试
run_test "文章管理测试" "articles_test.dart" "150000"

# 8. AI配置功能测试
run_test "AI配置测试" "ai_config_test.dart" "120000"

# 9. 完整应用测试
run_test "完整应用测试" "integration_test_all.dart" "200000"

# 10. 单元测试（如果存在）
echo ""
echo "🧪 运行单元测试..."
if flutter test test/unit/ --no-pub --machine 2>/dev/null; then
    echo "✅ 单元测试通过"
else
    echo "⚠️ 单元测试不存在或失败"
fi

# 计算测试结束时间
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "🎉 测试运行完成！"
echo "=================================="
echo "📊 测试结果统计:"
echo "   - 总测试数: $TOTAL_TESTS"
echo "   - 通过测试: $PASSED_TESTS"
echo "   - 失败测试: $((TOTAL_TESTS - PASSED_TESTS))"
echo "   - 成功率: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%"
echo "   - 总耗时: ${DURATION}秒"

# 显示测试报告位置
echo ""
echo "📊 测试报告:"
echo "   - 详细的测试日志保存在控制台输出中"
echo "   - 如需覆盖率报告，请运行: flutter test --coverage"

echo ""
echo "💡 其他测试命令:"
echo "   - 运行单个测试: flutter test integration_test/quick_test.dart -d $DEVICE_ID"
echo "   - 运行所有测试: flutter test -d $DEVICE_ID"
echo "   - 生成覆盖率: flutter test --coverage"
echo "   - 查看可用设备: flutter devices"
echo ""
echo "🎯 推荐测试流程:"
echo "   1. 首先运行: ./run_tests.sh"
echo "   2. 如果有失败的测试，单独运行具体测试文件进行调试"
echo "   3. 使用 flutter test --coverage 生成覆盖率报告"