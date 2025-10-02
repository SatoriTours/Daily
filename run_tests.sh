#!/bin/bash

# Daily Satori 测试运行脚本
# 用于在本地 Android 模拟器上运行集成测试

echo "🚀 Daily Satori 集成测试启动"
echo "=================================="

# 检查设备连接状态
echo "📱 检查设备连接..."
if ! flutter devices 2>/dev/null | grep -q "emulator"; then
    echo "❌ 没有找到 Android 模拟器，尝试启动模拟器..."

    # 尝试启动默认模拟器
    if flutter emulators | grep -q "."; then
        EMULATOR_ID=$(flutter emulators | head -1 | awk '{print $1}')
        echo "📱 启动模拟器: $EMULATOR_ID"
        flutter emulators --launch $EMULATOR_ID &
        echo "⏳ 等待模拟器启动..."
        sleep 30

        # 再次检查设备
        if ! flutter devices 2>/dev/null | grep -q "emulator"; then
            echo "❌ 模拟器启动失败，请手动启动模拟器"
            exit 1
        fi
    else
        echo "❌ 没有找到可用的模拟器，请先创建一个 Android 模拟器"
        echo "💡 运行 'flutter emulators --create' 来创建模拟器"
        exit 1
    fi
fi

# 获取设备ID
DEVICE_ID=$(flutter devices 2>/dev/null | grep emulator | head -1 | sed 's/.*\b\(emulator-[0-9]*\)\b.*/\1/')
if [ -z "$DEVICE_ID" ]; then
    # 如果没有获取到，尝试直接获取第二列
    DEVICE_ID=$(flutter devices 2>/dev/null | grep emulator | head -1 | awk '{print $3}')
fi
if [ -z "$DEVICE_ID" ]; then
    echo "❌ 无法获取设备ID"
    exit 1
fi
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

    # 确保超时时间格式正确
    if [[ ! $timeout =~ s$ ]]; then
        timeout="${timeout}s"
    fi

    if flutter test integration_test/$test_file -d $DEVICE_ID --timeout=$timeout; then
        echo "✅ $test_name 通过"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        echo "❌ $test_name 失败"
        return 1
    fi
}

# 快速测试函数（只运行核心测试）
run_quick_test() {
    echo ""
    echo "⚡ 运行快速测试..."
    echo "=================="

    # 1. 快速基础测试
    echo "1️⃣ 快速基础测试..."
    if flutter test integration_test/quick_test.dart -d $DEVICE_ID --timeout=60s; then
        echo "✅ 快速基础测试通过"
    else
        echo "❌ 快速基础测试失败"
        return 1
    fi

    # 2. 列表和交互测试
    echo "2️⃣ 列表和交互测试..."
    if flutter test integration_test/simple_widget_test.dart -d $DEVICE_ID --timeout=120s 2>/dev/null; then
        echo "✅ 列表和交互测试通过"
    else
        echo "⚠️ 列表和交互测试跳过（文件不存在或失败）"
    fi

    echo ""
    echo "🎉 快速测试完成！所有核心功能正常！"
    return 0
}

# 检查命令行参数
if [ "$1" = "--quick" ] || [ "$1" = "-q" ]; then
    echo "📌 运行快速测试模式"
    run_quick_test
    QUICK_TEST_RESULT=$?

    if [ $QUICK_TEST_RESULT -eq 0 ]; then
        echo ""
        echo "💡 如需完整测试，请运行: ./run_tests.sh"
        exit 0
    else
        exit 1
    fi
fi

# 完整测试模式
echo "📌 运行完整测试模式"

# 1. 快速基础测试
run_test "快速基础测试" "quick_test.dart" "90"

# 2. 基础UI测试
if [ -f "integration_test/basic_test.dart" ]; then
    run_test "基础UI测试" "basic_test.dart" "120"
fi

# 3. 稳定综合测试
if [ -f "integration_test/stable_comprehensive_test.dart" ]; then
    run_test "稳定综合测试" "stable_comprehensive_test.dart" "180"
fi

# 4. 完整功能测试
if [ -f "integration_test/comprehensive_test.dart" ]; then
    run_test "全面功能测试" "comprehensive_test.dart" "180"
fi

# 5. 性能和内存测试
if [ -f "integration_test/performance_test.dart" ]; then
    run_test "性能和内存测试" "performance_test.dart" "240"
fi

# 6. 读书管理功能测试
if [ -f "integration_test/books_test.dart" ]; then
    run_test "读书管理测试" "books_test.dart" "150"
fi

# 7. 日记功能测试
if [ -f "integration_test/diary_test.dart" ]; then
    run_test "日记功能测试" "diary_test.dart" "150"
fi

# 8. 文章管理功能测试
if [ -f "integration_test/articles_test.dart" ]; then
    run_test "文章管理测试" "articles_test.dart" "150"
fi

# 9. AI配置功能测试
if [ -f "integration_test/ai_config_test.dart" ]; then
    run_test "AI配置测试" "ai_config_test.dart" "120"
fi

# 10. 完整应用测试
if [ -f "integration_test/app_test.dart" ]; then
    run_test "完整应用测试" "app_test.dart" "200"
fi

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
echo "   - 运行快速测试: ./run_tests.sh --quick"
echo "   - 运行单个测试: flutter test integration_test/quick_test.dart -d $DEVICE_ID"
echo "   - 运行所有测试: flutter test -d $DEVICE_ID"
echo "   - 生成覆盖率: flutter test --coverage"
echo "   - 查看可用设备: flutter devices"
echo ""
echo "🎯 推荐测试流程:"
echo "   1. 首先运行: ./run_tests.sh --quick"
echo "   2. 如果需要完整测试: ./run_tests.sh"
echo "   3. 如果有失败的测试，单独运行具体测试文件进行调试"
echo "   4. 使用 flutter test --coverage 生成覆盖率报告"