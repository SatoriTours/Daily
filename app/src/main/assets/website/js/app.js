/**
 * Daily Satori Admin Dashboard
 * Vue 3 Composition API + Marked.js
 */

const { createApp, ref, computed, onMounted, watch } = Vue;

// ============================================================================
// 工具函数
// ============================================================================

const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    return `${d.toLocaleDateString('zh-CN')} ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
};

const truncate = (str, len) => !str ? '' : str.length > len ? `${str.substring(0, len)}...` : str;

const parseInternalLinks = (html) => {
    const icons = { diary: 'bi-journal-text', article: 'bi-file-text', book: 'bi-book' };
    return html.replaceAll(
        /\[\[(diary|article|book):(\d+):([^\]]+)\]\]/g,
        (_, type, id, title) => `<span class="internal-link" onclick="window.openInternalLink('${type}', ${id})"><i class="bi ${icons[type] || 'bi-link'}"></i>${title}</span>`
    );
};

const formatContent = (content) => {
    if (!content) return '';
    const html = typeof marked === 'undefined' ? content.replaceAll('\n', '<br>') : marked.parse(content);
    return parseInternalLinks(html);
};

const getBadgeClass = (type) => ({ article: 'badge-info', diary: 'badge-success', book: 'badge-warning' })[type] || 'badge-info';
const getTypeName = (type) => ({ article: '文章', diary: '日记', book: '书籍' })[type] || type;

const formatWeekTabLabel = (start, end, index) => {
    if (!start || !end) return '';
    if (index === 0) return '本周';
    if (index === 1) return '上周';
    const d = new Date(start);
    return `${d.getMonth() + 1}/${d.getDate()}-${new Date(end).getDate()}`;
};

// 主题管理
const getSystemTheme = () => window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
const getSavedTheme = () => localStorage.getItem('theme');
const saveTheme = (theme) => localStorage.setItem('theme', theme);

const applyTheme = (theme) => {
    if (theme === 'system') {
        document.documentElement.removeAttribute('data-theme');
    } else {
        document.documentElement.setAttribute('data-theme', theme);
    }
};

const initTheme = () => {
    const saved = getSavedTheme();
    if (saved && saved !== 'system') {
        applyTheme(saved);
        return saved === 'dark';
    }
    return getSystemTheme() === 'dark';
};

// 弹窗滚动控制
const lockBodyScroll = () => document.body.classList.add('modal-open');
const unlockBodyScroll = () => document.body.classList.remove('modal-open');

// ============================================================================
// API 客户端
// ============================================================================

async function api(endpoint, options, onUnauthorized) {
    const config = options || {};
    const response = await fetch(`/api/v2${endpoint}`, {
        ...config,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', ...config.headers }
    });
    const data = await response.json();
    if (response.status === 401) {
        onUnauthorized?.();
        throw new Error('会话已过期，请重新登录');
    }
    if (data.code !== 0) throw new Error(data.msg || '请求失败');
    return data.data;
}

// ============================================================================
// Vue 应用
// ============================================================================

createApp({
    setup() {
        // 核心状态
        const isLoggedIn = ref(false);
        const isConnected = ref(true);
        const loading = ref(false);
        const toasts = ref([]);
        const isDarkMode = ref(initTheme());
        let connectionCheckInterval = null;

        const toggleTheme = () => {
            isDarkMode.value = !isDarkMode.value;
            const newTheme = isDarkMode.value ? 'dark' : 'light';
            applyTheme(newTheme);
            saveTheme(newTheme);
        };

        const showToast = (message, type = 'success') => {
            const id = Date.now();
            toasts.value.push({ id, message, type });
            setTimeout(() => { toasts.value = toasts.value.filter(t => t.id !== id); }, 3000);
        };

        const apiRequest = (endpoint, options) => api(endpoint, options, () => {
            isLoggedIn.value = false;
            localStorage.removeItem('isLoggedIn');
        });

        // 连接检测
        const checkConnection = async () => {
            try {
                const response = await fetch('/ping');
                isConnected.value = response.ok && (await response.text()) === 'pong';
            } catch { isConnected.value = false; }
        };

        const startConnectionCheck = () => {
            checkConnection();
            connectionCheckInterval = setInterval(checkConnection, 10000);
        };

        // 认证
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
            try { await fetch('/api/v2/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch {}
            isLoggedIn.value = false;
            localStorage.removeItem('isLoggedIn');
            password.value = '';
            if (connectionCheckInterval) { clearInterval(connectionCheckInterval); connectionCheckInterval = null; }
        };

        // 页面导航
        const currentPage = ref('dashboard');
        const previousPage = ref('');
        const sidebarOpen = ref(false);
        const pageTitles = { dashboard: '仪表盘', articles: '文章管理', diary: '日记管理', books: '书籍管理' };
        const pageTitle = computed(() => pageTitles[currentPage.value] || '管理后台');
        const goBack = () => { currentPage.value = previousPage.value || 'dashboard'; };

        // 仪表盘
        const stats = ref({});
        const recentItems = ref([]);
        const recentLoading = ref(false);
        const recentActivityExpanded = ref(false);
        const weeklyReports = ref([]);
        const currentWeeklyReportIndex = ref(0);
        const weeklyReportLoading = ref(false);
        const currentWeeklyReport = computed(() => weeklyReports.value[currentWeeklyReportIndex.value] || null);

        const loadWeeklyReport = async () => {
            weeklyReportLoading.value = true;
            try {
                const data = await apiRequest('/stats/weekly-report');
                weeklyReports.value = Array.isArray(data) ? data : [];
                currentWeeklyReportIndex.value = 0;
            } catch { weeklyReports.value = []; }
            weeklyReportLoading.value = false;
        };

        async function loadDashboard() {
            recentLoading.value = true;
            try {
                const [statsData, recent] = await Promise.all([
                    apiRequest('/stats/overview'),
                    apiRequest('/stats/recent')
                ]);
                stats.value = statsData;
                recentItems.value = [...recent.articles, ...recent.diaries, ...recent.books]
                    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
                    .slice(0, 10);
            } catch {}
            recentLoading.value = false;
            loadWeeklyReport();
        }

        // 文章管理
        const articles = ref([]);
        const articlesLoading = ref(false);
        const searchQuery = ref('');
        const pagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        async function loadArticles(page = 1) {
            articlesLoading.value = true;
            try {
                const data = await apiRequest(`/articles?page=${page}`);
                articles.value = data.items;
                pagination.value = data.pagination;
            } catch (err) { showToast('加载文章失败: ' + err.message, 'error'); }
            articlesLoading.value = false;
        }

        const searchArticles = async () => {
            if (!searchQuery.value.trim()) return loadArticles();
            articlesLoading.value = true;
            try {
                const data = await apiRequest(`/articles/search?q=${encodeURIComponent(searchQuery.value)}`);
                articles.value = data.items;
                pagination.value = data.pagination;
            } catch (err) { showToast('搜索失败: ' + err.message, 'error'); }
            articlesLoading.value = false;
        };

        const changePage = (page) => {
            if (page >= 1 && page <= pagination.value.totalPages) loadArticles(page);
        };

        // 日记管理
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diarySearchQuery = ref('');
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });

        async function loadDiaries(page = 1) {
            diariesLoading.value = true;
            try {
                const data = await apiRequest(`/diary?page=${page}`);
                diaries.value = data.items;
                diaryPagination.value = data.pagination;
            } catch (err) { showToast('加载日记失败: ' + err.message, 'error'); }
            diariesLoading.value = false;
        }

        const searchDiaries = async () => {
            if (!diarySearchQuery.value.trim()) return loadDiaries();
            diariesLoading.value = true;
            try {
                const data = await apiRequest(`/diary/search?q=${encodeURIComponent(diarySearchQuery.value)}`);
                diaries.value = data.items;
                diaryPagination.value = data.pagination;
            } catch (err) { showToast('搜索失败: ' + err.message, 'error'); }
            diariesLoading.value = false;
        };

        const changeDiaryPage = (page) => {
            if (page >= 1 && page <= diaryPagination.value.totalPages) loadDiaries(page);
        };

        const getDiaryTitle = (content) => {
            if (!content) return '无标题';
            const line = content.split('\n')[0].trim();
            return line.length > 30 ? `${line.substring(0, 30)}...` : line;
        };

        // 日记编辑
        const showDiaryEditorModal = ref(false);
        const editingDiaryId = ref(null);
        const diaryContent = ref('');
        const diaryTags = ref('');
        const diaryMood = ref('');
        const savingDiary = ref(false);

        const openDiaryEditor = () => {
            editingDiaryId.value = null;
            diaryContent.value = diaryTags.value = diaryMood.value = '';
            showDiaryEditorModal.value = true;
        };

        const editDiaryFromDetail = () => {
            editingDiaryId.value = detailItem.value.id;
            diaryContent.value = detailItem.value.content || '';
            diaryTags.value = detailItem.value.tags || '';
            diaryMood.value = detailItem.value.mood || '';
            showDiaryDetailModal.value = false;
            showDiaryEditorModal.value = true;
        };

        const closeDiaryEditor = () => {
            showDiaryEditorModal.value = false;
            editingDiaryId.value = null;
            diaryContent.value = diaryTags.value = diaryMood.value = '';
        };

        const saveDiary = async () => {
            if (!diaryContent.value.trim()) return showToast('请输入日记内容', 'error');
            savingDiary.value = true;
            const isEdit = !!editingDiaryId.value;
            try {
                const body = JSON.stringify({
                    content: diaryContent.value.trim(),
                    tags: diaryTags.value.trim() || null,
                    mood: diaryMood.value.trim() || null
                });
                if (isEdit) {
                    await apiRequest(`/diary/${editingDiaryId.value}`, { method: 'PUT', body });
                } else {
                    await apiRequest('/diary', { method: 'POST', body });
                }
                showToast(isEdit ? '日记更新成功' : '日记添加成功', 'success');
                closeDiaryEditor();
                loadDiaries(diaryPagination.value.page);
            } catch (err) { showToast((isEdit ? '更新' : '添加') + '失败: ' + err.message, 'error'); }
            savingDiary.value = false;
        };

        // 书籍管理
        const books = ref([]);
        const booksLoading = ref(false);
        const bookSearchQuery = ref('');
        const bookPagination = ref({ page: 1, totalPages: 1, totalItems: 0, pageSize: 20 });
        const currentBookIndex = ref(0);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const expandedViewpointIndex = ref(0);
        const currentBook = computed(() => books.value[currentBookIndex.value] || null);

        const toggleViewpoint = (index) => {
            expandedViewpointIndex.value = expandedViewpointIndex.value === index ? -1 : index;
        };

        async function loadBooks(page = 1) {
            booksLoading.value = true;
            try {
                const data = await apiRequest(`/books?page=${page}`);
                books.value = data.items;
                bookPagination.value = data.pagination;
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadBookViewpoints(books.value[0].id);
                }
            } catch (err) { showToast('加载书籍失败: ' + err.message, 'error'); }
            booksLoading.value = false;
        }

        const selectBook = async (index) => {
            if (currentBookIndex.value === index) return;
            currentBookIndex.value = index;
            if (books.value[index]) await loadBookViewpoints(books.value[index].id);
        };

        const loadBookViewpoints = async (bookId) => {
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            expandedViewpointIndex.value = 0;
            try {
                const data = await apiRequest(`/books/${bookId}/viewpoints`);
                currentBookViewpoints.value = Array.isArray(data) ? data : [];
            } catch { currentBookViewpoints.value = []; }
            bookViewpointsLoading.value = false;
        };

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
            } catch (err) { showToast('搜索失败: ' + err.message, 'error'); }
            booksLoading.value = false;
        };

        const changeBookPage = (page) => {
            if (page >= 1 && page <= bookPagination.value.totalPages) loadBooks(page);
        };

        const showAddBookModal = ref(false);
        const addingBook = ref(false);
        const newBook = ref({ title: '' });

        const submitNewBook = async () => {
            if (!newBook.value.title.trim()) return showToast('请输入书名', 'error');
            addingBook.value = true;
            try {
                await apiRequest('/books', { method: 'POST', body: JSON.stringify({ title: newBook.value.title.trim() }) });
                showToast('书籍添加成功', 'success');
                showAddBookModal.value = false;
                newBook.value = { title: '' };
                await loadBooks();
            } catch (err) { showToast('添加失败: ' + err.message, 'error'); }
            addingBook.value = false;
        };

        // 页面导航
        const pageLoaders = { dashboard: loadDashboard, articles: loadArticles, diary: loadDiaries, books: loadBooks };
        const navigate = (page) => {
            previousPage.value = currentPage.value;
            currentPage.value = page;
            sidebarOpen.value = false;
            pageLoaders[page]?.();
        };

        // 详情弹窗
        const detailItem = ref({});
        const detailType = ref('');
        const detailLoading = ref(false);
        const showDetailModal = ref(false);
        const showDiaryDetailModal = ref(false);
        const showBookDetailModal = ref(false);

        // 监听弹窗状态控制滚动
        watch([showDetailModal, showDiaryDetailModal, showBookDetailModal, () => showDiaryEditorModal.value, () => showSubmitModal.value, () => showDeleteModal.value, () => showAddBookModal.value], () => {
            if (showDetailModal.value || showDiaryDetailModal.value || showBookDetailModal.value || showDiaryEditorModal.value || showSubmitModal.value || showDeleteModal.value || showAddBookModal.value) {
                lockBodyScroll();
            } else {
                unlockBodyScroll();
            }
        });

        const viewArticle = async (article) => {
            showDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'article';
            try { detailItem.value = await apiRequest(`/articles/${article.id}`); }
            catch (err) { showToast('加载文章详情失败: ' + err.message, 'error'); showDetailModal.value = false; }
            detailLoading.value = false;
        };

        const viewDiary = async (diary) => {
            showDiaryDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'diary';
            try { detailItem.value = await apiRequest(`/diary/${diary.id}`); }
            catch (err) { showToast('加载日记详情失败: ' + err.message, 'error'); showDiaryDetailModal.value = false; }
            detailLoading.value = false;
        };

        const viewBook = async (book) => {
            showBookDetailModal.value = true;
            detailLoading.value = true;
            detailType.value = 'book';
            try { detailItem.value = await apiRequest(`/books/${book.id}`); }
            catch (err) { showToast('加载书籍详情失败: ' + err.message, 'error'); showBookDetailModal.value = false; }
            detailLoading.value = false;
        };

        // 图片查看器
        const showImageViewer = ref(false);
        const currentViewImage = ref('');
        const openImageViewer = (url) => { currentViewImage.value = url; showImageViewer.value = true; };
        const closeImageViewer = () => { showImageViewer.value = false; currentViewImage.value = ''; };

        // 删除操作
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });
        const confirmDelete = (type, id) => { deleteTarget.value = { type, id }; showDeleteModal.value = true; };
        const confirmDeleteFromDetail = () => confirmDelete(detailType.value, detailItem.value.id);
        const confirmDeleteFromDiaryDetail = () => confirmDelete('diary', detailItem.value.id);
        const confirmDeleteFromBookDetail = () => confirmDelete('book', detailItem.value.id);

        const executeDelete = async () => {
            const { type, id } = deleteTarget.value;
            const endpoints = { article: 'articles', diary: 'diary', book: 'books' };
            try {
                await apiRequest(`/${endpoints[type] || 'articles'}/${id}`, { method: 'DELETE' });
                showToast('删除成功', 'success');
                showDeleteModal.value = showDetailModal.value = showDiaryDetailModal.value = showBookDetailModal.value = false;
                const refreshers = { article: () => loadArticles(pagination.value.page), diary: () => loadDiaries(diaryPagination.value.page), book: () => loadBooks(bookPagination.value.page) };
                refreshers[type]?.();
            } catch (err) { showToast('删除失败: ' + err.message, 'error'); }
        };

        // 文章提交
        const showSubmitModal = ref(false);
        const submitUrl = ref('');
        const submitting = ref(false);

        const submitArticle = async () => {
            if (!submitUrl.value.trim()) return showToast('请输入文章URL', 'error');
            submitting.value = true;
            try {
                await apiRequest('/articles', { method: 'POST', body: JSON.stringify({ url: submitUrl.value.trim() }) });
                showToast('文章添加成功', 'success');
                submitUrl.value = '';
                showSubmitModal.value = false;
                if (currentPage.value === 'articles') loadArticles(1);
            } catch (err) { showToast('添加失败: ' + err.message, 'error'); }
            submitting.value = false;
        };

        // 初始化
        const openInternalLink = async (type, id) => {
            const handlers = { diary: viewDiary, article: viewArticle, book: viewBook };
            handlers[type]?.({ id });
        };
        globalThis.openInternalLink = openInternalLink;

        onMounted(async () => {
            if (localStorage.getItem('isLoggedIn') !== 'true') return;
            try {
                const response = await fetch('/api/v2/auth/status', { credentials: 'same-origin' });
                const data = await response.json();
                if (data.code === 0 && data.data.authenticated) {
                    isLoggedIn.value = true;
                    startConnectionCheck();
                    loadDashboard();
                } else { localStorage.removeItem('isLoggedIn'); }
            } catch { localStorage.removeItem('isLoggedIn'); }
        });

        // 导出
        return {
            isLoggedIn, isConnected, password, loading, loginError, isDarkMode, toggleTheme,
            currentPage, sidebarOpen, pageTitle, toasts,
            stats, recentItems, recentLoading, recentActivityExpanded,
            weeklyReports, currentWeeklyReportIndex, currentWeeklyReport, weeklyReportLoading,
            articles, articlesLoading, searchQuery, pagination, loadArticles, searchArticles, changePage,
            diaries, diariesLoading, diarySearchQuery, diaryPagination, loadDiaries, searchDiaries, changeDiaryPage, getDiaryTitle,
            showDiaryEditorModal, editingDiaryId, diaryContent, diaryTags, diaryMood, savingDiary,
            openDiaryEditor, editDiaryFromDetail, closeDiaryEditor, saveDiary,
            books, booksLoading, bookSearchQuery, bookPagination,
            currentBookIndex, currentBook, currentBookViewpoints, bookViewpointsLoading,
            expandedViewpointIndex, toggleViewpoint, selectBook, loadBooks, searchBooks, changeBookPage,
            showAddBookModal, addingBook, newBook, submitNewBook,
            detailItem, detailType, detailLoading, showDetailModal, showDiaryDetailModal, showBookDetailModal,
            viewArticle, viewDiary, viewBook,
            showImageViewer, currentViewImage, openImageViewer, closeImageViewer,
            showDeleteModal, confirmDelete, confirmDeleteFromDetail, executeDelete,
            confirmDeleteFromDiaryDetail, confirmDeleteFromBookDetail,
            showSubmitModal, submitUrl, submitting, submitArticle,
            login, logout, navigate, goBack,
            formatDate, truncate, formatContent, getBadgeClass, getTypeName, formatWeekTabLabel
        };
    }
}).mount('#app');
