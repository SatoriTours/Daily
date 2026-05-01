const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

createApp({
    setup() {
        const isLoggedIn = ref(localStorage.getItem('ds_logged_in') === '1');
        const connected = ref(true);
        const dark = ref(initDark());
        const sidebarCollapsed = ref(localStorage.getItem('ds_sidebar') === '1');

        const loginToken = ref('');
        const loading = ref(false);
        const loginError = ref('');
        const toasts = ref([]);
        let connInterval = null;

        const page = ref('dashboard');
        const searchKeyword = ref('');
        const pageTitles = { dashboard: '仪表盘', articles: '文章管理', diary: '日记管理', books: '书籍管理' };
        const pageTitle = computed(() => pageTitles[page.value] || '');

        function initDark() {
            const saved = localStorage.getItem('ds_theme');
            if (saved) return saved === 'dark';
            return window.matchMedia('(prefers-color-scheme: dark)').matches;
        }

        function applyTheme() {
            if (dark.value) {
                document.documentElement.setAttribute('data-theme', 'dark');
            } else {
                document.documentElement.setAttribute('data-theme', 'light');
            }
        }
        applyTheme();

        const toggleTheme = () => {
            dark.value = !dark.value;
            applyTheme();
            localStorage.setItem('ds_theme', dark.value ? 'dark' : 'light');
        };

        function showToast(msg, type) {
            type = type || 'success';
            const id = Date.now();
            toasts.value.push({ id, msg, type });
            setTimeout(function() { toasts.value = toasts.value.filter(function(t) { return t.id !== id; }); }, 3000);
        }

        async function apiReq(endpoint, options) {
            options = options || {};
            const res = await fetch('/api/v2' + endpoint, Object.assign({}, options, {
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, options.headers || {})
            }));
            const data = await res.json();
            if (res.status === 401) {
                throw new Error('未登录，请重新登录');
            }
            if (data.code !== 0) throw new Error(data.msg || '请求失败');
            return data.data;
        }

        function formatDate(s) {
            if (!s) return '-';
            const d = new Date(s);
            return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
        }

        function truncate(s, n) { return !s ? '' : s.length > n ? s.substring(0, n) + '...' : s; }

        function formatContent(c) {
            if (!c) return '';
            return typeof marked !== 'undefined' ? marked.parse(c) : c.replaceAll('\n', '<br>');
        }

        async function checkConnection() {
            try {
                const r = await fetch('/ping');
                connected.value = r.ok && await r.text() === 'pong';
            } catch (e) { connected.value = false; }
        }

        const login = async function() {
            loading.value = true; loginError.value = '';
            try {
                const res = await fetch('/api/v2/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({ token: loginToken.value })
                });
                const text = await res.text();
                let data;
                try { data = JSON.parse(text); } catch (e) { loginError.value = text; return; }
                if (res.ok && data.code == 0) {
                    isLoggedIn.value = true;
                    localStorage.setItem('ds_logged_in', '1');
                    showToast('登录成功', 'success');
                    await nextTick();
                    checkConnection();
                    connInterval = setInterval(checkConnection, 10000);
                    navigate('dashboard');
                } else { loginError.value = data.msg || 'Token 错误'; }
            } catch (e) { loginError.value = e.message; }
            loading.value = false;
        };

        const logout = async function() {
            try { await fetch('/api/v2/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch (e) {}
            isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); loginToken.value = '';
            if (connInterval) { clearInterval(connInterval); connInterval = null; }
        };

        const navigate = function(p) { page.value = p; loadPage(p); };
        const loadPage = function(p) {
            if (p === 'dashboard') loadDashboard();
            else if (p === 'articles') loadArticles();
            else if (p === 'diary') loadDiaries();
            else if (p === 'books') loadBooks();
        };

        const doSearch = function() {
            if (page.value === 'articles') loadArticles(1, true);
            else if (page.value === 'diary') loadDiaries(1, true);
        };

        // Dashboard
        const statsCards = ref([]);
        const recentItems = ref([]);
        const recentLoading = ref(false);

        async function loadDashboard() {
            recentLoading.value = true;
            try {
                const overview = await apiReq('/stats/overview');
                const recent = await apiReq('/stats/recent');
                const t = overview.totals || {};
                statsCards.value = [
                    { icon: '☰', label: '文章', value: t.articles || 0, color: '#3b82f6' },
                    { icon: '📓', label: '日记', value: t.diaries || 0, color: '#22c55e' },
                    { icon: '📚', label: '书籍', value: t.books || 0, color: '#8b5cf6' },
                    { icon: '★', label: '收藏', value: t.favoriteArticles || 0, color: '#f59e0b' },
                ];
                recentItems.value = (recent.articles || []).concat(recent.diaries || [], recent.books || [])
                    .sort(function(a, b) { return new Date(b.createdAt) - new Date(a.createdAt); })
                    .slice(0, 10);
            } catch (e) {}
            recentLoading.value = false;
            nextTick(function() { drawTrendChart(); });
        }

        function drawTrendChart() {
            const el = document.querySelector('canvas[ref="trendChart"]');
            if (!el || typeof Chart === 'undefined') return;
            const ctx = el.getContext('2d');
            if (el._chart) el._chart.destroy();
            el._chart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: Array.from({ length: 7 }, function(_, i) {
                        const d = new Date(); d.setDate(d.getDate() - (6 - i));
                        return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
                    }),
                    datasets: [{
                        label: '文章', data: [0, 0, 0, 0, 0, 0, statsCards.value[0] ? statsCards.value[0].value : 0],
                        borderColor: '#3b82f6', tension: 0.3, fill: false
                    }, {
                        label: '日记', data: [0, 0, 0, 0, 0, 0, statsCards.value[1] ? statsCards.value[1].value : 0],
                        borderColor: '#22c55e', tension: 0.3, fill: false
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { position: 'bottom' } }
                }
            });
        }

        // Articles
        const articles = ref([]);
        const articlesLoading = ref(false);
        const pagination = ref({ page: 1, totalPages: 1, totalItems: 0 });
        const showArticleModal = ref(false);
        const articleUrl = ref('');
        const submitting = ref(false);
        const showDetailModal = ref(false);
        const detailItem = ref({});
        const detailLoading = ref(false);

        async function loadArticles(p, search) {
            p = p || 1;
            articlesLoading.value = true;
            try {
                const ep = search && searchKeyword.value
                    ? '/articles/search?q=' + encodeURIComponent(searchKeyword.value)
                    : '/articles?page=' + p;
                const data = await apiReq(ep);
                articles.value = data.items || [];
                pagination.value = data.pagination || { page: 1, totalPages: 1, totalItems: 0 };
            } catch (e) { showToast(e.message, 'error'); }
            articlesLoading.value = false;
        }

        const viewArticle = async function(a) {
            showDetailModal.value = true; detailLoading.value = true;
            try { detailItem.value = await apiReq('/articles/' + a.id); }
            catch (e) { showToast(e.message, 'error'); showDetailModal.value = false; }
            detailLoading.value = false;
        };

        const submitArticle = async function() {
            if (!articleUrl.value.trim()) return;
            submitting.value = true;
            try {
                await apiReq('/articles', { method: 'POST', body: JSON.stringify({ url: articleUrl.value.trim() }) });
                showToast('文章添加成功');
                showArticleModal.value = false; articleUrl.value = '';
                loadArticles();
            } catch (e) { showToast(e.message, 'error'); }
            submitting.value = false;
        };

        // Diaries
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0 });
        const showDiaryDetailModal = ref(false);
        const showDiaryEditorModal = ref(false);
        const editingDiaryId = ref(null);
        const diaryContent = ref('');
        const diaryTags = ref('');
        const savingDiary = ref(false);

        async function loadDiaries(p, search) {
            p = p || 1;
            diariesLoading.value = true;
            try {
                const ep = search && searchKeyword.value
                    ? '/diary/search?q=' + encodeURIComponent(searchKeyword.value)
                    : '/diary?page=' + p;
                const data = await apiReq(ep);
                diaries.value = data.items || [];
                diaryPagination.value = data.pagination || { page: 1, totalPages: 1, totalItems: 0 };
            } catch (e) { showToast(e.message, 'error'); }
            diariesLoading.value = false;
        }

        const viewDiary = async function(d) {
            showDiaryDetailModal.value = true; detailLoading.value = true;
            try { detailItem.value = await apiReq('/diary/' + d.id); }
            catch (e) { showToast(e.message, 'error'); showDiaryDetailModal.value = false; }
            detailLoading.value = false;
        };

        const editDiary = function(d) {
            editingDiaryId.value = d.id;
            diaryContent.value = d.content || '';
            diaryTags.value = d.tags || '';
            showDiaryDetailModal.value = false;
            showDiaryEditorModal.value = true;
        };

        const openDiaryEditor = function() {
            editingDiaryId.value = null;
            diaryContent.value = ''; diaryTags.value = '';
            showDiaryEditorModal.value = true;
        };

        const saveDiary = async function() {
            if (!diaryContent.value.trim()) return;
            savingDiary.value = true;
            const body = JSON.stringify({ content: diaryContent.value.trim(), tags: diaryTags.value.trim() || null });
            try {
                if (editingDiaryId.value) {
                    await apiReq('/diary/' + editingDiaryId.value, { method: 'PUT', body: body });
                } else {
                    await apiReq('/diary', { method: 'POST', body: body });
                }
                showToast(editingDiaryId.value ? '日记已更新' : '日记已添加');
                showDiaryEditorModal.value = false;
                loadDiaries();
            } catch (e) { showToast(e.message, 'error'); }
            savingDiary.value = false;
        };

        // Books
        const books = ref([]);
        const booksLoading = ref(false);
        const currentBookIndex = ref(0);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const showAddBookModal = ref(false);
        const newBookTitle = ref('');
        const addingBook = ref(false);
        const currentBook = computed(function() { return books.value[currentBookIndex.value] || null; });

        async function loadBooks() {
            booksLoading.value = true;
            try {
                const data = await apiReq('/books');
                books.value = data.items || [];
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadViewpoints(books.value[0].id);
                }
            } catch (e) { showToast(e.message, 'error'); }
            booksLoading.value = false;
        }

        const selectBook = async function(i) {
            currentBookIndex.value = i;
            if (books.value[i]) await loadViewpoints(books.value[i].id);
        };

        async function loadViewpoints(bookId) {
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            try {
                const data = await apiReq('/books/' + bookId + '/viewpoints');
                currentBookViewpoints.value = data.items || [];
            } catch (e) {}
            bookViewpointsLoading.value = false;
        }

        const submitNewBook = async function() {
            if (!newBookTitle.value.trim()) return;
            addingBook.value = true;
            try {
                await apiReq('/books', { method: 'POST', body: JSON.stringify({ title: newBookTitle.value.trim() }) });
                showToast('书籍已添加');
                showAddBookModal.value = false; newBookTitle.value = '';
                loadBooks();
            } catch (e) { showToast(e.message, 'error'); }
            addingBook.value = false;
        };

        // Delete
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });
        const confirmDelete = function(type, id) { deleteTarget.value = { type: type, id: id }; showDeleteModal.value = true; };

        const executeDelete = async function() {
            const t = deleteTarget.value;
            const map = { article: 'articles', diary: 'diary', book: 'books' };
            try {
                await apiReq('/' + (map[t.type] || 'articles') + '/' + t.id, { method: 'DELETE' });
                showToast('已删除');
                showDeleteModal.value = showDetailModal.value = showDiaryDetailModal.value = false;
                loadPage(page.value);
            } catch (e) { showToast(e.message, 'error'); }
        };

        // Image viewer
        const imageViewer = ref({ visible: false, src: '' });

        // Keyboard shortcuts
        function onKeydown(e) {
            if (e.ctrlKey && e.key === 'k') { e.preventDefault(); var el = document.querySelector('.search-input'); if (el) el.focus(); }
            if (e.key === 'Escape') {
                showDetailModal.value = showDiaryDetailModal.value = showDiaryEditorModal.value = false;
                showArticleModal.value = showAddBookModal.value = showDeleteModal.value = false;
                imageViewer.value.visible = false;
            }
            if (e.key === 'n' && e.ctrlKey && !e.target.closest('input,textarea')) {
                e.preventDefault();
                if (page.value === 'articles') showArticleModal.value = true;
                if (page.value === 'diary') openDiaryEditor();
                if (page.value === 'books') showAddBookModal.value = true;
            }
        }

        // Init
        onMounted(async function() {
            document.addEventListener('keydown', onKeydown);
            if (!isLoggedIn.value) return;
            try {
                const data = await apiReq('/auth/status');
                if (data.authenticated) {
                    checkConnection();
                    connInterval = setInterval(checkConnection, 10000);
                    loadDashboard();
                } else { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
            } catch (e) { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
        });

        watch(sidebarCollapsed, function(v) { localStorage.setItem('ds_sidebar', v ? '1' : '0'); });

        return {
            isLoggedIn, connected, dark, sidebarCollapsed, toggleTheme,
            loginToken, loading, loginError, toasts, login, logout,
            page, pageTitle, searchKeyword, doSearch, navigate,
            formatDate, truncate, formatContent,
            statsCards, recentItems, recentLoading,
            articles, articlesLoading, pagination, loadArticles, viewArticle,
            showArticleModal, articleUrl, submitting, submitArticle,
            showDetailModal, detailItem, detailLoading,
            diaries, diariesLoading, diaryPagination, loadDiaries, viewDiary,
            showDiaryDetailModal, showDiaryEditorModal, editingDiaryId,
            diaryContent, diaryTags, savingDiary, editDiary, openDiaryEditor, saveDiary,
            books, booksLoading, currentBookIndex, currentBookViewpoints, bookViewpointsLoading,
            currentBook, selectBook, loadBooks,
            showAddBookModal, newBookTitle, addingBook, submitNewBook,
            showDeleteModal, confirmDelete, executeDelete,
            imageViewer,
        };
    }
}).mount('#app');
