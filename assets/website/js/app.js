/**
 * Daily Satori Admin Dashboard
 *
 * Vue.js 3 应用主入口
 *
 * Features:
 * - Dashboard with statistics
 * - Article management
 * - Diary management
 * - Book management
 * - Authentication
 */

const { createApp, ref, computed, onMounted } = Vue;

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * 格式化日期时间
 */
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-CN') + ' ' +
           date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

/**
 * 截断文本
 */
function truncate(str, len) {
    if (!str) return '';
    return str.length > len ? str.substring(0, len) + '...' : str;
}

/**
 * 解析内部链接格式 [[type:id:title]]
 */
function parseInternalLinks(html) {
    const linkPattern = /\[\[(diary|article|book):(\d+):([^\]]+)\]\]/g;
    return html.replaceAll(linkPattern, (match, type, id, title) => {
        const icons = { diary: 'bi-journal-text', article: 'bi-file-text', book: 'bi-book' };
        const icon = icons[type] || 'bi-link';
        return `<span class="internal-link" data-type="${type}" data-id="${id}" onclick="window.openInternalLink('${type}', ${id})"><i class="bi ${icon}"></i>${title}</span>`;
    });
}

/**
 * 格式化内容（Markdown + 内部链接）
 */
function formatContent(content) {
    if (!content) return '';
    const html = (typeof marked !== 'undefined')
        ? marked.parse(content)
        : content.replaceAll('\n', '<br>');
    return parseInternalLinks(html);
}

/**
 * 获取徽章样式类
 */
function getBadgeClass(type) {
    const classes = { article: 'badge-info', diary: 'badge-success', book: 'badge-warning' };
    return classes[type] || 'badge-info';
}

/**
 * 获取类型中文名
 */
function getTypeName(type) {
    const names = { article: '文章', diary: '日记', book: '书籍' };
    return names[type] || type;
}

/**
 * 格式化周报Tab标签
 */
function formatWeekTabLabel(start, end, index) {
    if (!start || !end) return '';
    if (index === 0) return '本周';
    if (index === 1) return '上周';
    const startDate = new Date(start);
    const startMonth = startDate.getMonth() + 1;
    const startDay = startDate.getDate();
    const endDay = new Date(end).getDate();
    return `${startMonth}/${startDay}-${endDay}`;
}

// ============================================================================
// API Client
// ============================================================================

/**
 * API 请求封装
 */
async function api(endpoint, options = {}, onUnauthorized) {
    const headers = { 'Content-Type': 'application/json' };
    const response = await fetch(`/api/v2${endpoint}`, {
        ...options,
        credentials: 'same-origin',
        headers: { ...headers, ...options.headers }
    });

    const data = await response.json();

    if (response.status === 401) {
        if (onUnauthorized) onUnauthorized();
        throw new Error('会话已过期，请重新登录');
    }

    if (data.code !== 0) {
        throw new Error(data.msg || '请求失败');
    }
    return data.data;
}

// ============================================================================
// Vue Application
// ============================================================================

createApp({
    setup() {
        // ====================================================================
        // Core State
        // ====================================================================
        const isLoggedIn = ref(false);
        const isConnected = ref(true);
        const loading = ref(false);
        const toasts = ref([]);

        // Toast notifications
        const showToast = (message, type = 'success') => {
            const id = Date.now();
            toasts.value.push({ id, message, type });
            setTimeout(() => {
                toasts.value = toasts.value.filter(t => t.id !== id);
            }, 3000);
        };

        // API wrapper with auth handling
        const apiRequest = (endpoint, options = {}) => {
            return api(endpoint, options, () => {
                isLoggedIn.value = false;
                localStorage.removeItem('isLoggedIn');
            });
        };

        // ====================================================================
        // Connection Check
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
        // Authentication
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
        // Navigation
        // ====================================================================
        const currentPage = ref('dashboard');
        const previousPage = ref('');
        const sidebarOpen = ref(false);

        const pageTitle = computed(() => {
            const titles = {
                dashboard: '仪表盘',
                articles: '文章管理',
                diary: '日记管理',
                books: '书籍管理',
                detail: '详情'
            };
            return titles[currentPage.value] || '管理后台';
        });

        const navigate = (page) => {
            previousPage.value = currentPage.value;
            currentPage.value = page;
            sidebarOpen.value = false;

            if (page === 'dashboard') loadDashboard();
            else if (page === 'articles') loadArticles();
            else if (page === 'diary') loadDiaries();
            else if (page === 'books') loadBooks();
        };

        const goBack = () => {
            currentPage.value = previousPage.value || 'dashboard';
        };

        // ====================================================================
        // Dashboard
        // ====================================================================
        const stats = ref({});
        const recentItems = ref([]);
        const recentLoading = ref(false);
        const recentActivityExpanded = ref(false);

        // Weekly Reports
        const weeklyReports = ref([]);
        const currentWeeklyReportIndex = ref(0);
        const weeklyReportLoading = ref(false);

        const currentWeeklyReport = computed(() => {
            return weeklyReports.value[currentWeeklyReportIndex.value] || null;
        });

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

        const loadDashboard = async () => {
            recentLoading.value = true;
            try {
                const [statsData, recent] = await Promise.all([
                    apiRequest('/stats/overview'),
                    apiRequest('/stats/recent')
                ]);
                stats.value = statsData;
                recentItems.value = [
                    ...recent.articles,
                    ...recent.diaries,
                    ...recent.books
                ].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 10);
            } catch (err) {
                console.error('加载仪表盘失败:', err.message);
            }
            recentLoading.value = false;
            loadWeeklyReport();
        };

        // ====================================================================
        // Articles
        // ====================================================================
        const articles = ref([]);
        const articlesLoading = ref(false);
        const searchQuery = ref('');
        const pagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        const loadArticles = async (page = 1) => {
            articlesLoading.value = true;
            try {
                const data = await apiRequest(`/articles?page=${page}`);
                articles.value = data.items;
                pagination.value = data.pagination;
            } catch (err) {
                showToast('加载文章失败: ' + err.message, 'error');
            }
            articlesLoading.value = false;
        };

        const searchArticles = async () => {
            if (!searchQuery.value.trim()) {
                loadArticles();
                return;
            }
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

        const changePage = (page) => {
            if (page < 1 || page > pagination.value.totalPages) return;
            loadArticles(page);
        };

        // ====================================================================
        // Diary
        // ====================================================================
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diarySearchQuery = ref('');
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        const loadDiaries = async (page = 1) => {
            diariesLoading.value = true;
            try {
                const data = await apiRequest(`/diary?page=${page}`);
                diaries.value = data.items;
                diaryPagination.value = data.pagination;
            } catch (err) {
                showToast('加载日记失败: ' + err.message, 'error');
            }
            diariesLoading.value = false;
        };

        const searchDiaries = async () => {
            if (!diarySearchQuery.value.trim()) {
                loadDiaries();
                return;
            }
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

        const changeDiaryPage = (page) => {
            if (page < 1 || page > diaryPagination.value.totalPages) return;
            loadDiaries(page);
        };

        const getDiaryTitle = (content) => {
            if (!content) return '无标题';
            const firstLine = content.split('\n')[0].trim();
            return firstLine.length > 30 ? firstLine.substring(0, 30) + '...' : firstLine;
        };

        // ====================================================================
        // Books
        // ====================================================================
        const books = ref([]);
        const booksLoading = ref(false);
        const bookSearchQuery = ref('');
        const bookPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        const currentBookIndex = ref(0);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const expandedViewpointIndex = ref(0);

        const toggleViewpoint = (index) => {
            expandedViewpointIndex.value = expandedViewpointIndex.value === index ? -1 : index;
        };

        const currentBook = computed(() => {
            return books.value[currentBookIndex.value] || null;
        });

        const loadBooks = async (page = 1) => {
            booksLoading.value = true;
            try {
                const data = await apiRequest(`/books?page=${page}`);
                books.value = data.items;
                bookPagination.value = data.pagination;
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadBookViewpoints(books.value[0].id);
                }
            } catch (err) {
                showToast('加载书籍失败: ' + err.message, 'error');
            }
            booksLoading.value = false;
        };

        const selectBook = async (index) => {
            if (currentBookIndex.value === index) return;
            currentBookIndex.value = index;
            const book = books.value[index];
            if (book) {
                await loadBookViewpoints(book.id);
            }
        };

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

        const searchBooks = async () => {
            if (!bookSearchQuery.value.trim()) {
                loadBooks();
                return;
            }
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

        const changeBookPage = (page) => {
            if (page < 1 || page > bookPagination.value.totalPages) return;
            loadBooks(page);
        };

        // Add Book
        const showAddBookModal = ref(false);
        const addingBook = ref(false);
        const newBook = ref({ title: '' });

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
        // Detail View & Modals
        // ====================================================================
        const detailItem = ref({});
        const detailType = ref('');
        const detailLoading = ref(false);
        const showDetailModal = ref(false);
        const showDiaryDetailModal = ref(false);
        const showBookDetailModal = ref(false);

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

        // Image Viewer
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
        // Delete & Submit Actions
        // ====================================================================
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });
        const showSubmitModal = ref(false);
        const submitUrl = ref('');
        const submitting = ref(false);

        const confirmDelete = (type, id) => {
            deleteTarget.value = { type, id };
            showDeleteModal.value = true;
        };

        const confirmDeleteFromDetail = () => confirmDelete(detailType.value, detailItem.value.id);
        const confirmDeleteFromDiaryDetail = () => confirmDelete('diary', detailItem.value.id);
        const confirmDeleteFromBookDetail = () => confirmDelete('book', detailItem.value.id);

        const executeDelete = async () => {
            const { type, id } = deleteTarget.value;
            const typeMap = { article: 'articles', diary: 'diary', book: 'books' };
            const endpoint = typeMap[type] || 'articles';
            try {
                await apiRequest(`/${endpoint}/${id}`, { method: 'DELETE' });
                showToast('删除成功', 'success');
                showDeleteModal.value = false;
                showDetailModal.value = false;
                showDiaryDetailModal.value = false;
                showBookDetailModal.value = false;

                if (type === 'article') loadArticles(pagination.value.page);
                else if (type === 'diary') loadDiaries(diaryPagination.value.page);
                else if (type === 'book') loadBooks(bookPagination.value.page);
            } catch (err) {
                showToast('删除失败: ' + err.message, 'error');
            }
        };

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
        // Initialization
        // ====================================================================

        // Global function for internal links
        const openInternalLink = async (type, id) => {
            if (type === 'diary') await viewDiary({ id });
            else if (type === 'article') await viewArticle({ id });
            else if (type === 'book') await viewBook({ id });
        };
        globalThis.openInternalLink = openInternalLink;

        onMounted(async () => {
            const savedLogin = localStorage.getItem('isLoggedIn');
            if (savedLogin === 'true') {
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
            }
        });

        // ====================================================================
        // Return
        // ====================================================================
        return {
            // State
            isLoggedIn, isConnected, password, loading, loginError,
            currentPage, sidebarOpen, pageTitle, toasts,

            // Dashboard
            stats, recentItems, recentLoading, recentActivityExpanded,
            weeklyReports, currentWeeklyReportIndex, currentWeeklyReport, weeklyReportLoading,

            // Articles
            articles, articlesLoading, searchQuery, pagination,

            // Diary
            diaries, diariesLoading, diarySearchQuery, diaryPagination,

            // Books
            books, booksLoading, bookSearchQuery, bookPagination,
            currentBookIndex, currentBook, currentBookViewpoints, bookViewpointsLoading,
            expandedViewpointIndex, toggleViewpoint,
            showAddBookModal, addingBook, newBook, submitNewBook,

            // Detail
            detailItem, detailType, detailLoading,
            showDetailModal, showDiaryDetailModal, showBookDetailModal,
            showImageViewer, currentViewImage,

            // Actions
            showDeleteModal, showSubmitModal, submitUrl, submitting,

            // Methods
            login, logout, navigate, goBack,
            loadArticles, searchArticles, changePage,
            loadDiaries, searchDiaries, changeDiaryPage,
            loadBooks, searchBooks, changeBookPage, selectBook,
            viewArticle, viewDiary, viewBook,
            confirmDelete, confirmDeleteFromDetail, executeDelete,
            confirmDeleteFromDiaryDetail, confirmDeleteFromBookDetail,
            openImageViewer, closeImageViewer,
            submitArticle,

            // Utilities
            getDiaryTitle, formatDate, truncate, formatContent,
            getBadgeClass, getTypeName, formatWeekTabLabel
        };
    }
}).mount('#app');
