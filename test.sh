#!/bin/bash

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { printf "%b[INFO]%b %s\n" "$BLUE" "$NC" "$1"; }
print_success() { printf "%b[OK]%b %s\n" "$GREEN" "$NC" "$1"; }
print_error() { printf "%b[ERROR]%b %s\n" "$RED" "$NC" "$1"; }

compile() {
    print_info "编译检查..."
    ./gradlew :app:compileDebugKotlin --no-configuration-cache
    print_success "编译通过"
}

unit_test() {
    print_info "单元测试..."
    ./gradlew :app:testDebugUnitTest --no-configuration-cache
    print_success "单元测试通过"
}

assemble() {
    print_info "Debug 构建..."
    ./gradlew :app:assembleDebug --no-configuration-cache
    print_success "Debug 构建通过"
}

quick() {
    print_info "=== 快速检查 ==="
    compile
    unit_test
    print_success "快速检查完成"
}

full() {
    print_info "=== 完整检查 ==="
    quick
    assemble
    print_success "完整检查完成"
}

help() {
    cat <<'EOF'
Daily Satori 测试脚本

用法: ./test.sh [命令]

命令:
  quick    编译检查 + Android debug unit tests，默认
  full     quick + assembleDebug
  compile  只运行 compileDebugKotlin
  unit     只运行 app debug unit tests
  help     显示帮助
EOF
}

main() {
    case "${1:-quick}" in
        quick) quick ;;
        full) full ;;
        compile) compile ;;
        unit) unit_test ;;
        help|-h|--help) help ;;
        *) print_error "未知命令: $1"; help; exit 1 ;;
    esac
}

main "$@"
