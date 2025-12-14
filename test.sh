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
    local devices=$(flutter devices)
    if echo "$devices" | grep -q "android\|ios"; then
        print_success "检测到测试设备"
        return 0
    else
        print_warning "未检测到移动设备"
        return 1
    fi
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

    print_info "运行基础集成测试..."
    flutter test integration_test/basic_app_test.dart -d PKJ110 \
        --name="应用能够正常启动并显示主界面"
}

# 完整功能测试
run_full_test() {
    if ! check_devices; then
        print_error "需要连接设备才能运行集成测试"
        return 1
    fi

    # 检查环境变量
    if [[ -z "$TEST_AI_TOKEN" ]]; then
        print_warning "未检测到AI配置，运行前请先配置环境变量："
        print_info "export TEST_AI_TOKEN=\"your-api-key\""
        print_info "export TEST_AI_URL=\"https://api.openai.com/v1/chat/completions\""
        read -p "是否继续？(y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            return 1
        fi
    fi

    print_info "运行完整功能测试（包含数据初始化）..."
    flutter test integration_test/full_app_test.dart -d PKJ110
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
    echo "  full           完整功能测试（需要配置环境变量）"
    echo ""
    echo "示例:"
    echo "  $0                # 快速测试"
    echo "  $0 basic          # 基础集成测试"
    echo "  $0 full           # 完整功能测试"
    echo "  $0 --check        # 检查环境"
    echo ""
    echo "环境变量配置（完整功能测试需要）:"
    echo "  export TEST_AI_URL=\"https://api.openai.com/v1/chat/completions\""
    echo "  export TEST_AI_TOKEN=\"sk-your-openai-api-key-here\""
    echo "  export TEST_AI_MODEL=\"gpt-3.5-turbo\""
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
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"