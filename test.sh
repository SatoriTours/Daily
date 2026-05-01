#!/bin/bash

# Daily Satori 测试脚本
# 用法: ./test.sh [命令]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[OK]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查设备
check_device() {
    local device=$(flutter devices --machine 2>/dev/null | grep -o '"id":[^,]*' | head -1 | cut -d'"' -f4)
    if [[ -z "$device" ]]; then
        print_error "未检测到设备"
        return 1
    fi
    echo "$device"
}

# 静态分析
run_analyze() {
    print_info "代码分析..."
    flutter analyze
    print_success "分析通过"
}

# 单元测试
run_unit_test() {
    print_info "单元测试..."
    flutter test test/unit_test/
    print_success "单元测试通过"
}

# 集成测试
run_integration_test() {
    local device=$1
    local test_file=$2
    local name=$3
    print_info "$name..."
    flutter test "$test_file" -d "$device"
    print_success "$name 通过"
}

# 快速测试（默认）
quick() {
    print_info "=== 快速测试 ==="
    run_analyze
    run_unit_test
    print_success "快速测试完成"
}

# 完整测试
full() {
    print_info "=== 完整测试 ==="

    local device=$(check_device) || exit 1
    print_info "设备: $device"

    run_analyze
    run_unit_test

    # 集成测试
    run_integration_test "$device" "test/integration_test/integration_test.dart" "集成测试"

    print_success "完整测试完成"
}

# 帮助
help() {
    echo "Daily Satori 测试脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  quick    快速测试（分析+单元测试，默认）"
    echo "  full     完整测试（需要连接设备）"
    echo "  help     显示帮助"
    echo ""
    echo "环境变量:"
    echo "  TEST_AI_TOKEN    AI API Token"
    echo "  TEST_AI_URL      AI API URL"
    echo "  TEST_AI_MODEL    AI 模型名称"
}

main() {
    case ${1:-quick} in
        quick) quick ;;
        full) full ;;
        help|-h|--help) help ;;
        *) print_error "未知命令: $1"; help; exit 1 ;;
    esac
}

main "$@"
