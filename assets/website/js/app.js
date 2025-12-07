/**
 * Daily Satori Admin Dashboard - Vue.js 3 应用
 *
 * 功能模块:
 * - 仪表盘统计展示
 * - 文章/日记/书籍管理 (CRUD)
 * - 用户认证
 *
 * 技术栈: Vue 3 Composition API + Marked.js
 */

const { createApp, ref, computed, onMounted } = Vue;

// ============================================================================
// 工具函数 - 格式化与解析
// ============================================================================

/** 格式化日期为本地时间字符串 */
const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return `${date.toLocaleDateString('zh-CN')} ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
};

/** 截断文本到指定长度 */
const truncate = (str, len) => {
    if (!str) return '';
    return str.length > len ? `${str.substring(0, len)}...` : str;
};

/** 解析内部链接格式 [[type:id:title]] 为可点击的 HTML */
const parseInternalLinks = (html) => {
    const iconMap = { diary: 'bi-journal-text', article: 'bi-file-text', book: 'bi-book' };
    return html.replaceAll(
        /\[\[(diary|article|book):(\d+):([^\]]+)\]\]/g,
        (_, type, id, title) => `<span class="internal-link" data-type="${type}" data-id="${id}" onclick="window.openInternalLink('${type}', ${id})"><i class="bi ${iconMap[type] || 'bi-link'}"></i>${title}</span>`
    );
};

/** 格式化 Markdown 内容并解析内部链接 */
const formatContent = (content) => {
    if (!content) return '';
    const html = typeof marked === 'undefined' ? content.replaceAll('\n', '<br>') : marked.parse(content);
    return parseInternalLinks(html);
};

/** 获取类型对应的徽章样式类 */
const getBadgeClass = (type) => ({ article: 'badge-info', diary: 'badge-success', book: 'badge-warning' })[type] || 'badge-info';

/** 获取类型的中文名称 */
const getTypeName = (type) => ({ article: '文章', diary: '日记', book: '书籍' })[type] || type;

/** 格式化周报标签文本 */
const formatWeekTabLabel = (start, end, index) => {
    if (!start || !end) return '';
    if (index === 0) return '本周';
    if (index === 1) return '上周';
    const startDate = new Date(start);
    return `${startDate.getMonth() + 1}/${startDate.getDate()}-${new Date(end).getDate()}`;
};

// ============================================================================
// API 客户端 - 封装请求与错误处理
// ============================================================================

/**
 * 发送 API 请求
 * @param {string} endpoint - API 端点
 * @param {Object} options - fetch 配置选项
 * @param {Function} onUnauthorized - 认证失败回调
 */
async function api(endpoint, options, onUnauthorized) {
    const config = options || {};
    const response = await fetch(`/api/v2${endpoint}`, {
        ...config,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', ...config.headers }
    });

    const data = await response.json();

    // 处理认证失败
    if (response.status === 401) {
        if (onUnauthorized) onUnauthorized();
        throw new Error('会话已过期，请重新登录');
    }

    if (data.code !== 0) throw new Error(data.msg || '请求失败');
    return data.data;
}

// ============================================================================
// Vue 应用 - 主入口
// ============================================================================

createApp({
    setup() {
        // ====================================================================
        // 核心状态 - 全局控制
        // ====================================================================
        const isLoggedIn = ref(false);      // 登录状态
        const isConnected = ref(true);       // 服务器连接状态
        const loading = ref(false);          // 全局加载状态
        const toasts = ref([]);              // Toast 通知队列

        /**
         * 显示 Toast 通知
         * @param {string} message - 通知内容
         * @param {'success'|'error'} type - 通知类型
         */
        const showToast = (message, type = 'success') => {
            const id = Date.now();
            toasts.value.push({ id, message, type });
            setTimeout(() => {
                toasts.value = toasts.value.filter(t => t.id !== id);
            }, 3000);
        };

        /** 封装 API 请求，自动处理认证失败 */
        const apiRequest = (endpoint, options) => {
            return api(endpoint, options, () => {
                isLoggedIn.value = false;
                localStorage.removeItem('isLoggedIn');
            });
        };

        // ====================================================================
        // 连接检测 - 定时检查服务器状态
        // ====================================================================
        let connectionCheckInterval = null;

        const checkConnection = async () => {
            try {
                const response = await fetch('/ping', { method: 'GET' });
                const text = await response.text();
                isConnected.value = response.ok && text === 'pong';
            } catch {
                isConnected.value = false;
            }
        };

        const startConnectionCheck = () => {
            checkConnection();
            connectionCheckInterval = setInterval(checkConnection, 10000);
        };

        // ====================================================================
        // 用户认证 - 登录/登出
        // ====================================================================
        const password = ref('');
        const loginError = ref('');

        const login = async () => {
            loading.value = true;
            loginError.value = '';
            try {
                const response = await fetch('/api/v2/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({ password: password.value })
                });
                const data = await response.json();

                if (response.ok && data.code === 0) {
                    isLoggedIn.value = true;
                    localStorage.setItem('isLoggedIn', 'true');
                    showToast('登录成功', 'success');
                    startConnectionCheck();
                    loadDashboard();
                } else {
                    loginError.value = data.msg || '密码错误';
                }
            } catch (err) {
                loginError.value = '登录失败: ' + err.message;
            }
            loading.value = false;
        };

        const logout = async () => {
            try {
                await fetch('/api/v2/auth/logout', { method: 'POST', credentials: 'same-origin' });
            } catch (err) {
                console.warn('登出请求失败:', err.message);
            }
            isLoggedIn.value = false;
            localStorage.removeItem('isLoggedIn');
            password.value = '';
            if (connectionCheckInterval) {
                clearInterval(connectionCheckInterval);
                connectionCheckInterval = null;
            }
        };

        // ====================================================================
        // 页面导航 - 路由控制
        // ====================================================================
        const currentPage = ref('dashboard');   // 当前页面
        const previousPage = ref('');           // 上一页面（用于返回）
        const sidebarOpen = ref(false);         // 移动端侧边栏状态

        /** 页面标题映射 */
        const pageTitles = { dashboard: '仪表盘', articles: '文章管理', diary: '日记管理', books: '书籍管理', detail: '详情' };
        const pageTitle = computed(() => pageTitles[currentPage.value] || '管理后台');

        /** 返回上一页 */
        const goBack = () => {
            currentPage.value = previousPage.value || 'dashboard';
        };

        // ====================================================================
        // 仪表盘 - 统计数据与周报
        // ====================================================================
        const stats = ref({});                      // 统计数据
        const recentItems = ref([]);                // 最近活动列表
        const recentLoading = ref(false);
        const recentActivityExpanded = ref(false);  // 最近活动折叠状态

        // 周报相关状态
        const weeklyReports = ref([]);
        const currentWeeklyReportIndex = ref(0);
        const weeklyReportLoading = ref(false);
        const currentWeeklyReport = computed(() => weeklyReports.value[currentWeeklyReportIndex.value] || null);

        /** 加载周报数据 */
        const loadWeeklyReport = async () => {
            weeklyReportLoading.value = true;
            try {
                const data = await apiRequest('/stats/weekly-report');
                weeklyReports.value = Array.isArray(data) ? data : [];
                currentWeeklyReportIndex.value = 0;
            } catch (err) {
                console.warn('加载周报失败:', err.message);
                weeklyReports.value = [];
            }
            weeklyReportLoading.value = false;
        };

        /** 加载仪表盘数据（统计 + 最近活动 + 周报） */
        async function loadDashboard() {
            recentLoading.value = true;
            try {
                // 并行请求统计和最近活动数据
                const [statsData, recent] = await Promise.all([
                    apiRequest('/stats/overview'),
                    apiRequest('/stats/recent')
                ]);
                stats.value = statsData;
                // 合并并按时间排序，取最近10条
                recentItems.value = [...recent.articles, ...recent.diaries, ...recent.books]
                    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
                    .slice(0, 10);
            } catch (err) {
                console.error('加载仪表盘失败:', err.message);
            }
            recentLoading.value = false;
            loadWeeklyReport();
        }

        // ====================================================================
        // 文章管理 - 列表/搜索/分页
        // ====================================================================
        const articles = ref([]);
        const articlesLoading = ref(false);
        const searchQuery = ref('');
        const pagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        /** 加载文章列表 */
        async function loadArticles(page = 1) {
            articlesLoading.value = true;
            try {
                const data = await apiRequest(`/articles?page=${page}`);
                articles.value = data.items;
                pagination.value = data.pagination;
            } catch (err) {
                showToast('加载文章失败: ' + err.message, 'error');
            }
            articlesLoading.value = false;
        }

        /** 搜索文章 */
        const searchArticles = async () => {
            if (!searchQuery.value.trim()) return loadArticles();
            articlesLoading.value = true;
            try {
                const data = await apiRequest(`/articles/search?q=${encodeURIComponent(searchQuery.value)}`);
                articles.value = data.items;
                pagination.value = data.pagination;
            } catch (err) {
                showToast('搜索失败: ' + err.message, 'error');
            }
            articlesLoading.value = false;
        };

        /** 切换文章分页 */
        const changePage = (page) => {
            if (page >= 1 && page <= pagination.value.totalPages) loadArticles(page);
        };

        // ====================================================================
        // 日记管理 - 列表/搜索/分页
        // ====================================================================
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diarySearchQuery = ref('');
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        /** 加载日记列表 */
        async function loadDiaries(page = 1) {
            diariesLoading.value = true;
            try {
                const data = await apiRequest(`/diary?page=${page}`);
                diaries.value = data.items;
                diaryPagination.value = data.pagination;
            } catch (err) {
                showToast('加载日记失败: ' + err.message, 'error');
            }
            diariesLoading.value = false;
        }

        /** 搜索日记 */
        const searchDiaries = async () => {
            if (!diarySearchQuery.value.trim()) return loadDiaries();
            diariesLoading.value = true;
            try {
                const data = await apiRequest(`/diary/search?q=${encodeURIComponent(diarySearchQuery.value)}`);
                diaries.value = data.items;
                diaryPagination.value = data.pagination;
            } catch (err) {
                showToast('搜索失败: ' + err.message, 'error');
            }
            diariesLoading.value = false;
        };

        /** 切换日记分页 */
        const changeDiaryPage = (page) => {
            if (page >= 1 && page <= diaryPagination.value.totalPages) loadDiaries(page);
        };

        /** 从日记内容提取标题（首行前30字符） */
        const getDiaryTitle = (content) => {
            if (!content) return '无标题';
            const firstLine = content.split('\n')[0].trim();
            return firstLine.length > 30 ? `${firstLine.substring(0, 30)}...` : firstLine;
        };

        // ====================================================================
        // 书籍管理 - 列表/观点/搜索
        // ====================================================================
        const books = ref([]);
        const booksLoading = ref(false);
        const bookSearchQuery = ref('');
        const bookPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        // 当前选中书籍及其观点
        const currentBookIndex = ref(0);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const expandedViewpointIndex = ref(0);
        const currentBook = computed(() => books.value[currentBookIndex.value] || null);

        /** 切换观点展开/折叠状态 */
        const toggleViewpoint = (index) => {
            expandedViewpointIndex.value = expandedViewpointIndex.value === index ? -1 : index;
        };

        /** 加载书籍列表 */
        async function loadBooks(page = 1) {
            booksLoading.value = true;
            try {
                const data = await apiRequest(`/books?page=${page}`);
                books.value = data.items;
                bookPagination.value = data.pagination;
                // 自动选中第一本书并加载其观点
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadBookViewpoints(books.value[0].id);
                }
            } catch (err) {
                showToast('加载书籍失败: ' + err.message, 'error');
            }
            booksLoading.value = false;
        }

        /** 选择书籍 */
        const selectBook = async (index) => {
            if (currentBookIndex.value === index) return;
            currentBookIndex.value = index;
            if (books.value[index]) await loadBookViewpoints(books.value[index].id);
        };

        /** 加载书籍观点列表 */
        const loadBookViewpoints = async (bookId) => {
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            expandedViewpointIndex.value = 0;
            try {
                const viewpoints = await apiRequest(`/books/${bookId}/viewpoints`);
                currentBookViewpoints.value = Array.isArray(viewpoints) ? viewpoints : [];
            } catch (err) {
                console.warn('加载观点失败:', err.message);
                currentBookViewpoints.value = [];
            }
            bookViewpointsLoading.value = false;
        };

        /** 搜索书籍 */
        const searchBooks = async () => {
            if (!bookSearchQuery.value.trim()) return loadBooks();
            booksLoading.value = true;
            try {
                const data = await apiRequest(`/books/search?q=${encodeURIComponent(bookSearchQuery.value)}`);
                books.value = data.items;
                bookPagination.value = data.pagination;
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadBookViewpoints(books.value[0].id);
                }
            } catch (err) {
                showToast('搜索失败: ' + err.message, 'error');
            }
            booksLoading.value = false;
        };

        /** 切换书籍分页 */
        const changeBookPage = (page) => {
            if (page >= 1 && page <= bookPagination.value.totalPages) loadBooks(page);
        };

        // 添加书籍弹窗状态
        const showAddBookModal = ref(false);
        const addingBook = ref(false);
        const newBook = ref({ title: '' });

        /** 提交新书籍 */
        const submitNewBook = async () => {
            if (!newBook.value.title.trim()) {
                showToast('请输入书名', 'error');
                return;
            }
            addingBook.value = true;
            try {
                await apiRequest('/books', {
                    method: 'POST',
                    body: JSON.stringify({ title: newBook.value.title.trim() })
                });
                showToast('书籍添加成功', 'success');
                showAddBookModal.value = false;
                newBook.value = { title: '' };
                await loadBooks();
            } catch (err) {
                showToast('添加失败: ' + err.message, 'error');
            }
            addingBook.value = false;
        };

        // ====================================================================
        // 页面导航函数 - 在所有加载函数定义后设置
        // ====================================================================

        /** 页面导航处理器映射 */
        const pageLoaders = { dashboard: loadDashboard, articles: loadArticles, diary: loadDiaries, books: loadBooks };

        /** 导航到指定页面 */
        const navigate = (page) => {
            previousPage.value = currentPage.value;
            currentPage.value = page;
            sidebarOpen.value = false;
            if (pageLoaders[page]) pageLoaders[page]();
        };

        // ====================================================================
        // 详情弹窗 - 文章/日记/书籍详情查看
        // ====================================================================
        const detailItem = ref({});              // 当前详情数据
        const detailType = ref('');              // 详情类型: article/diary/book
        const detailLoading = ref(false);
        const showDetailModal = ref(false);      // 文章详情弹窗
        const showDiaryDetailModal = ref(false); // 日记详情弹窗
        const showBookDetailModal = ref(false);  // 书籍详情弹窗

        /** 查看文章详情 */
        const viewArticle = async (article) => {
            showDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'article';
            try {
                detailItem.value = await apiRequest(`/articles/${article.id}`);
            } catch (err) {
                showToast('加载文章详情失败: ' + err.message, 'error');
                showDetailModal.value = false;
            }
            detailLoading.value = false;
        };

        /** 查看日记详情 */
        const viewDiary = async (diary) => {
            showDiaryDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'diary';
            try {
                detailItem.value = await apiRequest(`/diary/${diary.id}`);
            } catch (err) {
                showToast('加载日记详情失败: ' + err.message, 'error');
                showDiaryDetailModal.value = false;
            }
            detailLoading.value = false;
        };

        /** 查看书籍详情 */
        const viewBook = async (book) => {
            showBookDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'book';
            try {
                detailItem.value = await apiRequest(`/books/${book.id}`);
            } catch (err) {
                showToast('加载书籍详情失败: ' + err.message, 'error');
                showBookDetailModal.value = false;
            }
            detailLoading.value = false;
        };

        // ====================================================================
        // 图片查看器
        // ====================================================================
        const showImageViewer = ref(false);
        const currentViewImage = ref('');

        const openImageViewer = (imageUrl) => {
            currentViewImage.value = imageUrl;
            showImageViewer.value = true;
        };

        const closeImageViewer = () => {
            showImageViewer.value = false;
            currentViewImage.value = '';
        };

        // ====================================================================
        // 删除操作 - 统一处理
        // ====================================================================
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });

        const confirmDelete = (type, id) => {
            deleteTarget.value = { type, id };
            showDeleteModal.value = true;
        };

        // 从各详情弹窗触发删除确认
        const confirmDeleteFromDetail = () => confirmDelete(detailType.value, detailItem.value.id);
        const confirmDeleteFromDiaryDetail = () => confirmDelete('diary', detailItem.value.id);
        const confirmDeleteFromBookDetail = () => confirmDelete('book', detailItem.value.id);

        /** 执行删除操作 */
        const executeDelete = async () => {
            const { type, id } = deleteTarget.value;
            const endpointMap = { article: 'articles', diary: 'diary', book: 'books' };
            try {
                await apiRequest(`/${endpointMap[type] || 'articles'}/${id}`, { method: 'DELETE' });
                showToast('删除成功', 'success');
                // 关闭所有弹窗
                showDeleteModal.value = false;
                showDetailModal.value = false;
                showDiaryDetailModal.value = false;
                showBookDetailModal.value = false;
                // 刷新对应列表
                const refreshers = {
                    article: () => loadArticles(pagination.value.page),
                    diary: () => loadDiaries(diaryPagination.value.page),
                    book: () => loadBooks(bookPagination.value.page)
                };
                if (refreshers[type]) refreshers[type]();
            } catch (err) {
                showToast('删除失败: ' + err.message, 'error');
            }
        };

        // ====================================================================
        // 文章提交 - 通过 URL 添加文章
        // ====================================================================
        const showSubmitModal = ref(false);
        const submitUrl = ref('');
        const submitting = ref(false);

        /** 提交新文章 */
        const submitArticle = async () => {
            if (!submitUrl.value.trim()) {
                showToast('请输入文章URL', 'error');
                return;
            }
            submitting.value = true;
            try {
                await apiRequest('/articles', {
                    method: 'POST',
                    body: JSON.stringify({ url: submitUrl.value.trim() })
                });
                showToast('文章添加成功', 'success');
                submitUrl.value = '';
                showSubmitModal.value = false;
                if (currentPage.value === 'articles') loadArticles(1);
            } catch (err) {
                showToast('添加失败: ' + err.message, 'error');
            }
            submitting.value = false;
        };

        // ====================================================================
        // 应用初始化
        // ====================================================================

        /** 处理内部链接点击（周报中的 [[type:id:title]] 格式） */
        const openInternalLink = async (type, id) => {
            const handlers = { diary: viewDiary, article: viewArticle, book: viewBook };
            if (handlers[type]) await handlers[type]({ id });
        };
        globalThis.openInternalLink = openInternalLink;

        /** 挂载时检查登录状态 */
        onMounted(async () => {
            if (localStorage.getItem('isLoggedIn') !== 'true') return;
            try {
                const response = await fetch('/api/v2/auth/status', { credentials: 'same-origin' });
                const data = await response.json();
                if (data.code === 0 && data.data.authenticated) {
                    isLoggedIn.value = true;
                    startConnectionCheck();
                    loadDashboard();
                } else {
                    localStorage.removeItem('isLoggedIn');
                }
            } catch (err) {
                console.warn('会话验证失败:', err.message);
                localStorage.removeItem('isLoggedIn');
            }
        });

        // ====================================================================
        // 导出给模板使用的响应式数据和方法
        // ====================================================================
        return {
            // 核心状态
            isLoggedIn, isConnected, password, loading, loginError,
            currentPage, sidebarOpen, pageTitle, toasts,

            // 仪表盘
            stats, recentItems, recentLoading, recentActivityExpanded,
            weeklyReports, currentWeeklyReportIndex, currentWeeklyReport, weeklyReportLoading,

            // 文章管理
            articles, articlesLoading, searchQuery, pagination,
            loadArticles, searchArticles, changePage,

            // 日记管理
            diaries, diariesLoading, diarySearchQuery, diaryPagination,
            loadDiaries, searchDiaries, changeDiaryPage, getDiaryTitle,

            // 书籍管理
            books, booksLoading, bookSearchQuery, bookPagination,
            currentBookIndex, currentBook, currentBookViewpoints, bookViewpointsLoading,
            expandedViewpointIndex, toggleViewpoint, selectBook,
            loadBooks, searchBooks, changeBookPage,
            showAddBookModal, addingBook, newBook, submitNewBook,

            // 详情弹窗
            detailItem, detailType, detailLoading,
            showDetailModal, showDiaryDetailModal, showBookDetailModal,
            viewArticle, viewDiary, viewBook,

            // 图片查看器
            showImageViewer, currentViewImage, openImageViewer, closeImageViewer,

            // 删除与提交操作
            showDeleteModal, confirmDelete, confirmDeleteFromDetail, executeDelete,
            confirmDeleteFromDiaryDetail, confirmDeleteFromBookDetail,
            showSubmitModal, submitUrl, submitting, submitArticle,

            // 认证与导航
            login, logout, navigate, goBack,

            // 工具函数
            formatDate, truncate, formatContent, getBadgeClass, getTypeName, formatWeekTabLabel
        };
    }
}).mount('#app');
