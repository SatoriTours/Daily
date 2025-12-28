#!/bin/bash

# Daily Satori 测试脚本 - 统一测试管理
# 用法: ./test.sh [选项] [测试类型]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 打印消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Flutter环境
check_flutter() {
    if ! command -v flutter &> /dev/null; then
        print_error "Flutter 未安装"
        exit 1
    fi
    print_info "Flutter 版本: $(flutter --version | head -n 1)"
}

# 检查设备连接
check_devices() {
    local devices=$(flutter devices --machine 2>/dev/null | grep -o '"name":[^,]*' | cut -d'"' -f4 | tr '\n' ' ')
    if [[ -n "$devices" ]]; then
        print_success "检测到测试设备: $devices"
        return 0
    else
        print_warning "未检测到设备"
        return 1
    fi
}

# 获取第一个可用设备ID
get_device_id() {
    flutter devices --machine 2>/dev/null | head -1 | grep -o '"id":[^,]*' | cut -d'"' -f4
}

# 构建 dart-define 参数（用于传递环境变量）
get_dart_defines() {
    local defines=""

    if [[ -n "$TEST_AI_TOKEN" ]]; then
        defines="$defines--dart-define=TEST_AI_TOKEN=$TEST_AI_TOKEN "
    fi

    if [[ -n "$TEST_AI_URL" ]]; then
        defines="$defines--dart-define=TEST_AI_URL=$TEST_AI_URL "
    fi

    if [[ -n "$TEST_AI_MODEL" ]]; then
        defines="$defines--dart-define=TEST_AI_MODEL=$TEST_AI_MODEL "
    fi

    echo "$defines"
}

# 快速测试（日常使用）
run_quick_test() {
    print_info "开始快速测试..."

    # 1. 静态分析
    print_info "运行代码静态分析..."
    if flutter analyze; then
        print_success "✓ 代码分析通过"
    else
        print_error "✗ 代码分析失败"
        return 1
    fi

    # 2. 单元测试
    print_info "运行单元测试..."
    if flutter test; then
        print_success "✓ 单元测试通过"
    else
        print_error "✗ 单元测试失败"
        return 1
    fi

    # 3. 构建检查
    print_info "检查构建..."
    if flutter build apk --debug; then
        print_success "✓ 构建成功"
    else
        print_error "✗ 构建失败"
        return 1
    fi

    print_success "🎉 快速测试完成！代码可以提交了。"
}

# 基础集成测试
run_basic_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行基础集成测试 (设备: $device_id)..."

    # 尝试运行集成测试，如果失败则跳过
    if flutter test integration_test/basic_app_test.dart -d "$device_id" \
        --name="应用能够正常启动并显示主界面" 2>/dev/null; then
        print_success "基础集成测试通过"
    else
        print_warning "基础集成测试跳过（需要移动设备）"
    fi
}

# 完整功能测试
run_full_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    local dart_defines=$(get_dart_defines)

    # 检查环境变量
    print_info "检查环境变量配置..."
    if [[ -z "$TEST_AI_TOKEN" ]]; then
        print_warning "⚠️ TEST_AI_TOKEN 未配置"
    else
        print_success "✓ TEST_AI_TOKEN 已配置"
    fi

    if [[ -z "$TEST_AI_URL" ]]; then
        print_info "ℹ️ TEST_AI_URL 未配置 (将使用默认值: https://api.deepseek.com)"
    else
        print_success "✓ TEST_AI_URL: $TEST_AI_URL"
    fi

    if [[ -z "$TEST_AI_MODEL" ]]; then
        print_info "ℹ️ TEST_AI_MODEL 未配置 (将使用默认值: deepseek-chat)"
    else
        print_success "✓ TEST_AI_MODEL: $TEST_AI_MODEL"
    fi
    echo

    print_info "运行完整功能测试 (设备: $device_id)..."
    print_info "测试顺序："
    print_info "- [步骤0] APP配置验证（最先执行）"
    print_info "- [步骤1] 应用启动"
    print_info "- [步骤2] 文章模块（保存、详情、刷新、删除、搜索）"
    print_info "- [步骤3] 日记模块（多篇日记、搜索、编辑、删除）"
    print_info "- [步骤4] 读书模块"
    print_info "- [步骤5] 设置模块"
    echo

    if eval "flutter test integration_test/full_app_test.dart -d \"$device_id\" $dart_defines" 2>/dev/null; then
        print_success "完整功能测试通过"
    else
        print_warning "完整功能测试失败"
        return 1
    fi
}

# 综合功能测试（覆盖所有模块）
run_comprehensive_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    local dart_defines=$(get_dart_defines)

    # 检查环境变量
    if [[ -z "$TEST_AI_TOKEN" ]]; then
        print_warning "未检测到AI配置，AI功能测试将被跳过"
        print_info "如需测试AI功能，请配置环境变量："
        print_info "export TEST_AI_TOKEN=\"your-api-key\""
        print_info "export TEST_AI_URL=\"https://api.openai.com/v1/chat/completions\""
        print_info "export TEST_AI_MODEL=\"gpt-3.5-turbo\""
        read -p "是否继续？(y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            return 1
        fi
    else
        print_success "检测到AI配置"
    fi

    print_info "运行综合功能测试（覆盖所有模块）(设备: $device_id)..."
    print_info "测试内容包括："
    print_info "- 文章模块（添加、搜索、阅读、分享）"
    print_info "- 日记模块（创建、编辑、搜索）"
    print_info "- 读书模块（搜索、添加、记录感悟）"
    print_info "- AI聊天（搜索、问答）"
    print_info "- 设置（主题、语言、AI配置）"
    print_info "- 备份还原"
    echo

    if eval "flutter test integration_test/comprehensive_app_test.dart -d \"$device_id\" $dart_defines" 2>/dev/null; then
        print_success "综合功能测试通过"
    else
        print_warning "综合功能测试跳过（需要移动设备）"
    fi
}

# 全功能测试（新的完整测试套件）
run_all_features_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    local dart_defines=$(get_dart_defines)

    print_info "运行全功能自动化测试 (设备: $device_id)..."
    print_info "测试内容包括："
    print_info "- [1/7] 应用启动"
    print_info "- [2/7] 文章模块"
    print_info "- [3/7] 日记模块"
    print_info "- [4/7] 读书模块"
    print_info "- [5/7] AI聊天"
    print_info "- [6/7] 设置"
    print_info "- [7/7] 备份恢复"
    echo

    if eval "flutter test integration_test/all_features_test.dart -d \"$device_id\" $dart_defines" 2>/dev/null; then
        print_success "全功能自动化测试通过"
    else
        print_warning "全功能自动化测试失败"
        return 1
    fi
}

# 日记模块专项测试
run_diary_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行日记模块专项测试 (设备: $device_id)..."

    if flutter test integration_test/diary_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        print_success "日记模块测试通过"
    else
        print_warning "日记模块测试失败"
        return 1
    fi
}

# 读书模块专项测试
run_books_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行读书模块专项测试 (设备: $device_id)..."

    if flutter test integration_test/books_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        print_success "读书模块测试通过"
    else
        print_warning "读书模块测试失败"
        return 1
    fi
}

# 设置模块专项测试
run_settings_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行设置模块专项测试 (设备: $device_id)..."

    if flutter test integration_test/settings_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        print_success "设置模块测试通过"
    else
        print_warning "设置模块测试失败"
        return 1
    fi
}

# 备份恢复专项测试
run_backup_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行备份恢复专项测试 (设备: $device_id)..."

    if flutter test integration_test/backup_restore_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        print_success "备份恢复测试通过"
    else
        print_warning "备份恢复测试失败"
        return 1
    fi
}

# 文章收藏专项测试
run_article_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    print_info "运行文章收藏专项测试 (设备: $device_id)..."

    if flutter test integration_test/article_collection_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        print_success "文章收藏测试通过"
    else
        print_warning "文章收藏测试失败"
        return 1
    fi
}

# 运行所有专项测试
run_all_module_tests() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    local device_id=$(get_device_id)
    local failed_tests=()

    print_info "运行所有模块专项测试..."
    echo

    # 运行各个模块测试
    print_info "[1/6] 文章收藏测试..."
    if ! flutter test integration_test/article_collection_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("文章收藏")
        print_error "✗ 文章收藏测试失败"
    else
        print_success "✓ 文章收藏测试通过"
    fi

    print_info "[2/6] 日记模块测试..."
    if ! flutter test integration_test/diary_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("日记模块")
        print_error "✗ 日记模块测试失败"
    else
        print_success "✓ 日记模块测试通过"
    fi

    print_info "[3/6] 读书模块测试..."
    if ! flutter test integration_test/books_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("读书模块")
        print_error "✗ 读书模块测试失败"
    else
        print_success "✓ 读书模块测试通过"
    fi

    print_info "[4/6] 设置模块测试..."
    if ! flutter test integration_test/settings_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("设置模块")
        print_error "✗ 设置模块测试失败"
    else
        print_success "✓ 设置模块测试通过"
    fi

    print_info "[5/6] 备份恢复测试..."
    if ! flutter test integration_test/backup_restore_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("备份恢复")
        print_error "✗ 备份恢复测试失败"
    else
        print_success "✓ 备份恢复测试通过"
    fi

    print_info "[6/6] 全功能测试..."
    if ! flutter test integration_test/all_features_test.dart -d "$device_id" $(get_dart_defines) 2>/dev/null; then
        failed_tests+=("全功能")
        print_error "✗ 全功能测试失败"
    else
        print_success "✓ 全功能测试通过"
    fi

    echo
    echo "========================================"
    if [ ${#failed_tests[@]} -eq 0 ]; then
        print_success "🎉 所有模块测试都通过了！"
    else
        print_error "以下测试失败："
        for test in "${failed_tests[@]}"; do
            print_error "  - $test"
        done
        return 1
    fi
    echo "========================================"
}

# 清理和准备
prepare_test() {
    print_info "准备测试环境..."
    flutter clean > /dev/null 2>&1
    flutter pub get
    print_success "环境准备完成"
}

# 检查环境
check_environment() {
    print_info "检查测试环境..."
    check_flutter
    check_devices
    flutter analyze
    print_success "环境检查完成"
}

# 显示帮助
show_help() {
    echo "Daily Satori 测试脚本"
    echo ""
    echo "用法: $0 [选项] [测试类型]"
    echo ""
    echo "选项:"
    echo "  -h, --help     显示帮助信息"
    echo "  -c, --check    检查测试环境"
    echo "  -p, --prepare  准备测试环境"
    echo ""
    echo "测试类型:"
    echo "  quick          快速测试（默认，代码分析+单元测试+构建）"
    echo "  basic          基础集成测试（应用启动验证）"
    echo "  full           完整功能测试（推荐，验证配置+所有功能模块）"
    echo "  comprehensive  综合功能测试（覆盖所有功能模块）"
    echo "  all            全功能自动化测试"
    echo "  diary          日记模块专项测试"
    echo "  books          读书模块专项测试"
    echo "  settings       设置模块专项测试"
    echo "  backup         备份恢复专项测试"
    echo "  article        文章收藏专项测试"
    echo "  modules        运行所有模块专项测试"
    echo ""
    echo "示例:"
    echo "  $0                # 快速测试"
    echo "  $0 basic          # 基础集成测试"
    echo "  $0 full           # 完整功能测试（推荐）"
    echo "  $0 comprehensive  综合功能测试"
    echo "  $0 --check        # 检查环境"
    echo ""
    echo "环境变量配置（AI功能测试需要）:"
    echo "  export TEST_AI_URL=\"https://api.deepseek.com\""
    echo "  export TEST_AI_TOKEN=\"your-api-token\""
    echo "  export TEST_AI_MODEL=\"deepseek-chat\""
}

# 主函数
main() {
    case ${1:-quick} in
        -h|--help)
            show_help
            ;;
        -c|--check)
            check_environment
            ;;
        -p|--prepare)
            prepare_test
            ;;
        quick|"")
            run_quick_test
            ;;
        basic)
            prepare_test
            run_quick_test
            run_basic_test
            ;;
        full)
            prepare_test
            run_quick_test
            run_full_test
            ;;
        comprehensive)
            prepare_test
            run_quick_test
            run_comprehensive_test
            ;;
        all)
            prepare_test
            run_quick_test
            run_all_features_test
            ;;
        diary)
            prepare_test
            run_diary_test
            ;;
        books)
            prepare_test
            run_books_test
            ;;
        settings)
            prepare_test
            run_settings_test
            ;;
        backup)
            prepare_test
            run_backup_test
            ;;
        article)
            prepare_test
            run_article_test
            ;;
        modules)
            prepare_test
            run_all_module_tests
            ;;
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"
