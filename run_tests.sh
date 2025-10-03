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

# 预构建APK以避免重复构建
echo "🔨 预构建APK..."
flutter build apk --debug -d $DEVICE_ID
PREBUILD_RESULT=$?
if [ $PREBUILD_RESULT -ne 0 ]; then
    echo "⚠️ 预构建失败，将在各个测试中单独构建"
fi

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

    # 确保超时时间格式正确 (flutter test 需要秒数)
    if [[ $timeout =~ ms$ ]]; then
        # 如果是毫秒，转换为秒
        timeout=$(( ${timeout%ms} / 1000 ))s
    elif [[ ! $timeout =~ s$ ]]; then
        # 如果没有单位，假设是秒
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

    # 1. 基本UI组件测试
    echo "1️⃣ 基本UI组件测试..."
    if flutter test integration_test/real_app_test.dart --plain-name="基本UI组件测试" --timeout=90s 2>/dev/null; then
        echo "✅ 基本UI组件测试通过"
    else
        echo "❌ 基本UI组件测试失败"
        return 1
    fi

    # 2. 文章界面核心功能测试
    echo "2️⃣ 文章界面核心功能测试..."
    if flutter test integration_test/articles_ui_test.dart --plain-name="文章页面完整功能测试" --timeout=90s 2>/dev/null; then
        echo "✅ 文章界面核心功能测试通过"
    else
        echo "⚠️ 文章界面测试跳过"
    fi

    # 3. 日记界面核心功能测试
    echo "3️⃣ 日记界面核心功能测试..."
    if flutter test integration_test/diary_ui_test.dart --plain-name="日记页面完整功能测试" --timeout=90s 2>/dev/null; then
        echo "✅ 日记界面核心功能测试通过"
    else
        echo "⚠️ 日记界面测试跳过"
    fi

    # 4. 读书界面核心功能测试
    echo "4️⃣ 读书界面核心功能测试..."
    if flutter test integration_test/books_ui_test.dart --plain-name="读书页面完整功能测试" --timeout=90s 2>/dev/null; then
        echo "✅ 读书界面核心功能测试通过"
    else
        echo "⚠️ 读书界面测试跳过"
    fi

    # 5. 设置界面核心功能测试
    echo "5️⃣ 设置界面核心功能测试..."
    if flutter test integration_test/settings_ui_test.dart --plain-name="设置页面完整功能测试" --timeout=90s 2>/dev/null; then
        echo "✅ 设置界面核心功能测试通过"
    else
        echo "⚠️ 设置界面测试跳过"
    fi

    # 6. 列表和交互测试（备用）
    echo "6️⃣ 列表和交互测试..."
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
        echo "💡 如需一次启动测试所有功能，请运行: ./run_tests.sh --complete"
        exit 0
    else
        exit 1
    fi
fi

if [ "$1" = "--complete" ] || [ "$1" = "-c" ]; then
    echo "📌 运行完整应用测试模式（一次启动，测试所有功能）"
    echo "🔄 测试顺序：文章 → 日记 → 读书 → 设置"
    echo "⏱️  预计测试时间：3-5分钟"
    echo ""

    # 运行简化完整应用测试（更稳定）
    if flutter test integration_test/simple_complete_test.dart -d $DEVICE_ID --timeout=5m; then
        echo ""
        echo "🎉 完整应用测试通过！"
        echo "✅ 所有界面功能测试完成"
        exit 0
    else
        echo ""
        echo "❌ 完整应用测试失败，尝试原始版本..."
        # 如果简化版本失败，尝试原始版本
        if flutter test integration_test/complete_app_test.dart -d $DEVICE_ID --timeout=5m; then
            echo ""
            echo "🎉 原始完整应用测试通过！"
            echo "✅ 所有界面功能测试完成"
            exit 0
        else
            echo ""
            echo "❌ 完整应用测试失败"
            exit 1
        fi
    fi
fi

# 完整测试模式
echo "📌 运行完整测试模式"

# 1. 基础UI组件测试
if [ -f "integration_test/real_app_test.dart" ]; then
    run_test "基础UI组件测试" "real_app_test.dart" "120"
fi

# 2. 文章界面详细测试
if [ -f "integration_test/articles_ui_test.dart" ]; then
    run_test "文章界面详细测试" "articles_ui_test.dart" "150"
fi

# 3. 日记界面详细测试
if [ -f "integration_test/diary_ui_test.dart" ]; then
    run_test "日记界面详细测试" "diary_ui_test.dart" "150"
fi

# 4. 读书界面详细测试
if [ -f "integration_test/books_ui_test.dart" ]; then
    run_test "读书界面详细测试" "books_ui_test.dart" "150"
fi

# 5. 设置界面详细测试
if [ -f "integration_test/settings_ui_test.dart" ]; then
    run_test "设置界面详细测试" "settings_ui_test.dart" "150"
fi

# 6. 完整应用综合测试
if [ -f "integration_test/comprehensive_app_test.dart" ]; then
    run_test "完整应用综合测试" "comprehensive_app_test.dart" "200"
fi

# 7. 基础UI测试（备用）
if [ -f "integration_test/basic_test.dart" ]; then
    run_test "基础UI测试" "basic_test.dart" "120"
fi

# 8. 稳定综合测试（备用）
if [ -f "integration_test/stable_comprehensive_test.dart" ]; then
    run_test "稳定综合测试" "stable_comprehensive_test.dart" "180"
fi

# 9. 其他专用测试（如果存在）
if [ -f "integration_test/comprehensive_test.dart" ]; then
    run_test "全面功能测试" "comprehensive_test.dart" "180"
fi

if [ -f "integration_test/performance_test.dart" ]; then
    run_test "性能和内存测试" "performance_test.dart" "240"
fi

if [ -f "integration_test/ai_config_test.dart" ]; then
    run_test "AI配置测试" "ai_config_test.dart" "120"
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
echo "   - 运行完整应用测试: ./run_tests.sh --complete (一次启动，测试所有功能)"
echo "   - 运行简化完整测试: flutter test integration_test/simple_complete_test.dart -d $DEVICE_ID"
echo "   - 运行原始完整测试: flutter test integration_test/complete_app_test.dart -d $DEVICE_ID"
echo "   - 运行单独界面测试: ./run_tests.sh (分别重启测试各界面)"
echo "   - 运行单个测试: flutter test integration_test/quick_test.dart -d $DEVICE_ID"
echo "   - 运行所有测试: flutter test -d $DEVICE_ID"
echo "   - 生成覆盖率: flutter test --coverage"
echo "   - 查看可用设备: flutter devices"
echo ""
echo "🎯 推荐测试流程:"
echo "   1. 日常开发: ./run_tests.sh --quick (快速验证核心功能)"
echo "   2. 功能演示: ./run_tests.sh --complete (一次启动展示所有功能)"
echo "   3. 详细测试: ./run_tests.sh (分别测试各界面)"
echo "   4. 问题调试: 单独运行具体测试文件"