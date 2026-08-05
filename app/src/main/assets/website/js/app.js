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
        const pageTitles = { dashboard: '今日', articles: '文章', diary: '日记', books: '阅读', ai: 'AI 助手', tasks: '任务中心' };
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
            var headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
            const res = await fetch('/api/v2' + endpoint, Object.assign({}, options, {
                credentials: 'same-origin',
                headers: headers
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
            const html = typeof marked !== 'undefined' ? marked.parse(c) : c.replaceAll('\n', '<br>');
            return html.replace(/<img\s/gi, '<img loading="lazy" referrerpolicy="no-referrer" ');
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
                    localStorage.removeItem('ds_token');
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
            isLoggedIn.value = false;
            localStorage.removeItem('ds_logged_in'); localStorage.removeItem('ds_token'); loginToken.value = '';
            if (connInterval) { clearInterval(connInterval); connInterval = null; }
        };

        const navigate = function(p) { page.value = p; loadPage(p); };
        const loadPage = function(p) {
            if (p === 'dashboard') loadDashboard();
            else if (p === 'articles') loadArticles();
            else if (p === 'diary') loadDiaries();
            else if (p === 'books') loadBooks();
            else if (p === 'ai') loadAiSessions();
            else if (p === 'tasks') loadTasks();
        };

        const doSearch = function() {
            if (page.value === 'articles') loadArticles(1, true);
            else if (page.value === 'diary') loadDiaries(1, true);
            else if (page.value === 'books') loadBooks(true);
        };

        // Dashboard
        const statsCards = ref([]);
        const recentItems = ref([]);
        const recentLoading = ref(false);
        const newsSummary = ref({});
        const summaryLoading = ref(false);
        const summaryRefreshing = ref(false);

        async function loadDashboard() {
            recentLoading.value = true;
            summaryLoading.value = true;
            try {
                const results = await Promise.all([
                    apiReq('/stats/overview'),
                    apiReq('/stats/recent'),
                    apiReq('/news/summary')
                ]);
                const overview = results[0];
                const recent = results[1];
                newsSummary.value = results[2] || {};
                const t = overview.totals || {};
                statsCards.value = [
                    { label: '文章', value: t.articles || 0, page: 'articles' },
                    { label: '日记', value: t.diaries || 0, page: 'diary' },
                    { label: '书籍', value: t.books || 0, page: 'books' },
                    { label: '收藏', value: t.favoriteArticles || 0, page: 'articles' },
                ];
                recentItems.value = (recent.articles || []).concat(recent.diaries || [], recent.books || [])
                    .sort(function(a, b) { return new Date(b.createdAt) - new Date(a.createdAt); })
                    .slice(0, 10);
            } catch (e) {}
            recentLoading.value = false;
            summaryLoading.value = false;
        }

        async function refreshNewsSummary() {
            if (summaryRefreshing.value) return;
            summaryRefreshing.value = true;
            try {
                await apiReq('/news/summary/refresh', { method: 'POST' });
                newsSummary.value = await apiReq('/news/summary') || {};
                showToast('今日汇总已更新');
            } catch (e) { showToast(e.message, 'error'); }
            summaryRefreshing.value = false;
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
        const articleActionLoading = ref(false);
        const articleTab = ref('summary');

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
            showDetailModal.value = true; detailLoading.value = true; articleTab.value = 'summary';
            try { detailItem.value = await apiReq('/articles/' + a.id); }
            catch (e) { showToast(e.message, 'error'); showDetailModal.value = false; }
            detailLoading.value = false;
        };

        const submitArticle = async function() {
            if (!articleUrl.value.trim()) return;
            submitting.value = true;
            try {
                const result = await apiReq('/articles', { method: 'POST', body: JSON.stringify({ url: articleUrl.value.trim() }) });
                showToast(result && result.existing ? '文章已存在' : '已加入文章处理队列');
                showArticleModal.value = false; articleUrl.value = '';
                loadArticles();
            } catch (e) { showToast(e.message, 'error'); }
            submitting.value = false;
        };

        const toggleArticleFavorite = async function() {
            if (!detailItem.value.id || articleActionLoading.value) return;
            articleActionLoading.value = true;
            try {
                const result = await apiReq('/articles/' + detailItem.value.id + '/favorite', { method: 'POST' });
                detailItem.value.isFavorite = result.isFavorite;
                showToast(result.isFavorite ? '已收藏' : '已取消收藏');
                loadArticles(pagination.value.page);
            } catch (e) { showToast(e.message, 'error'); }
            articleActionLoading.value = false;
        };

        const reprocessArticle = async function() {
            if (!detailItem.value.id || articleActionLoading.value) return;
            articleActionLoading.value = true;
            try {
                await apiReq('/articles/' + detailItem.value.id + '/reprocess', { method: 'POST' });
                detailItem.value.status = 'pending';
                showToast('已重新开始处理文章');
            } catch (e) { showToast(e.message, 'error'); }
            articleActionLoading.value = false;
        };

        // Diaries
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0 });
        const selectedDiary = ref(null);
        const selectedDiaryMonth = ref('all');
        const diaryDetailLoading = ref(false);
        const showDiaryDetailModal = ref(false);
        const showDiaryEditorModal = ref(false);
        const editingDiaryId = ref(null);
        const diaryContent = ref('');
        const diaryTags = ref('');
        const diaryMood = ref('');
        const savingDiary = ref(false);
        const showDiaryPreview = ref(false);
        const diaryEditorInput = ref(null);
        const diaryInitialSnapshot = ref('');
        const diarySourceUpdatedAt = ref(0);
        const diaryDraftStatus = ref('');
        const diaryInspirationPrompts = [
            '此刻，最想诚实面对的是什么？',
            '今天有什么微小瞬间值得被记住？',
            '如果把今天写成一页书，它的标题是什么？',
            '有什么念头反复回来找你？',
            '今天哪一刻让你感到自己正在生活？'
        ];
        let diaryDraftTimer = null;
        const diaryDateLabel = computed(function() {
            return new Intl.DateTimeFormat('zh-CN', {
                year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
            }).format(new Date());
        });
        const diaryCreativePrompt = computed(function() {
            return diaryInspirationPrompts[new Date().getDate() % diaryInspirationPrompts.length];
        });
        const diaryCharacterCount = computed(function() { return diaryContent.value.length; });
        const diaryReadingMinutes = computed(function() {
            return diaryContent.value.trim() ? Math.max(1, Math.ceil(diaryContent.value.trim().length / 400)) : 0;
        });
        const diaryHasUnsavedChanges = computed(function() { return diarySnapshot() !== diaryInitialSnapshot.value; });
        const diaryMonths = computed(function() {
            const months = new Map();
            diaries.value.forEach(function(diary) {
                const date = new Date(diary.createdAt);
                const key = date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
                const item = months.get(key) || { key: key, label: new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(date), count: 0 };
                item.count += 1;
                months.set(key, item);
            });
            return Array.from(months.values());
        });
        const filteredDiaries = computed(function() {
            if (selectedDiaryMonth.value === 'all') return diaries.value;
            return diaries.value.filter(function(diary) {
                const date = new Date(diary.createdAt);
                return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') === selectedDiaryMonth.value;
            });
        });

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
                selectedDiary.value = null;
                if (selectedDiaryMonth.value !== 'all' && !diaryMonths.value.some(function(month) { return month.key === selectedDiaryMonth.value; })) selectedDiaryMonth.value = 'all';
            } catch (e) { showToast(e.message, 'error'); }
            diariesLoading.value = false;
        }

        const viewDiary = async function(d) {
            if (!d) return;
            diaryDetailLoading.value = true;
            try { selectedDiary.value = await apiReq('/diary/' + d.id); }
            catch (e) { showToast(e.message, 'error'); }
            diaryDetailLoading.value = false;
        };

        function closeDiaryReader() { selectedDiary.value = null; }

        function formatDiaryDay(value, full) {
            if (!value) return '';
            const date = new Date(value);
            return new Intl.DateTimeFormat('zh-CN', full
                ? { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }
                : { month: 'short', day: 'numeric' }).format(date);
        }

        function diaryExcerpt(content) {
            const text = (content || '').replace(/[#>*_`\[\]-]/g, ' ').replace(/\s+/g, ' ').trim();
            return text.length > 64 ? text.slice(0, 64) + '…' : (text || '空白日记');
        }

        function diaryTagList(tags) {
            return (tags || '').split(',').map(function(tag) { return tag.trim(); }).filter(Boolean);
        }

        function diaryDayNumber(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value)); }
        function diaryWeekday(value) { return new Intl.DateTimeFormat('zh-CN', { weekday: 'short' }).format(new Date(value)); }

        const editDiary = function(d) {
            editingDiaryId.value = d.id;
            diaryContent.value = d.content || '';
            diaryTags.value = d.tags || '';
            diaryMood.value = d.mood || '';
            diarySourceUpdatedAt.value = d.updatedAt || 0;
            diaryInitialSnapshot.value = diarySnapshot();
            showDiaryDetailModal.value = false;
            showDiaryEditorModal.value = true;
            restoreDiaryDraft();
            focusDiaryEditor();
        };

        const openDiaryEditor = function() {
            editingDiaryId.value = null;
            diaryContent.value = ''; diaryTags.value = ''; diaryMood.value = '';
            diarySourceUpdatedAt.value = 0;
            diaryInitialSnapshot.value = diarySnapshot();
            showDiaryEditorModal.value = true;
            restoreDiaryDraft();
            focusDiaryEditor();
        };

        function diarySnapshot() {
            return JSON.stringify({ content: diaryContent.value, tags: diaryTags.value, mood: diaryMood.value });
        }

        function diaryDraftKey() {
            return editingDiaryId.value ? 'ds_diary_draft_' + editingDiaryId.value : 'ds_diary_draft_new';
        }

        function restoreDiaryDraft() {
            try {
                const saved = JSON.parse(localStorage.getItem(diaryDraftKey()) || 'null');
                if (!saved || !saved.snapshot) return;
                if (editingDiaryId.value && saved.sourceUpdatedAt !== diarySourceUpdatedAt.value) return;
                const draft = JSON.parse(saved.snapshot);
                diaryContent.value = draft.content || '';
                diaryTags.value = draft.tags || '';
                diaryMood.value = draft.mood || '';
                diaryDraftStatus.value = '已恢复未保存草稿';
                showToast('已恢复上次未完成的日记草稿');
            } catch (e) { localStorage.removeItem(diaryDraftKey()); }
        }

        function scheduleDiaryDraft() {
            if (!showDiaryEditorModal.value) return;
            if (diaryDraftTimer) clearTimeout(diaryDraftTimer);
            diaryDraftStatus.value = '正在保存草稿…';
            diaryDraftTimer = setTimeout(function() {
                try {
                    localStorage.setItem(diaryDraftKey(), JSON.stringify({
                        snapshot: diarySnapshot(), sourceUpdatedAt: diarySourceUpdatedAt.value, savedAt: Date.now()
                    }));
                    diaryDraftStatus.value = '草稿已保存';
                } catch (e) { diaryDraftStatus.value = '草稿保存失败'; }
            }, 650);
        }

        function clearDiaryDraft() {
            if (diaryDraftTimer) clearTimeout(diaryDraftTimer);
            diaryDraftTimer = null;
            localStorage.removeItem(diaryDraftKey());
            diaryDraftStatus.value = '';
        }

        function closeDiaryEditor() {
            if (diaryHasUnsavedChanges.value && !window.confirm('当前修改尚未发布，草稿会保留在这台电脑上。确定关闭编辑器吗？')) return;
            showDiaryEditorModal.value = false;
        }

        function focusDiaryEditor() {
            nextTick(function() { if (diaryEditorInput.value) diaryEditorInput.value.focus(); });
        }

        function applyDiaryPrompt(prompt) {
            const prefix = diaryContent.value.trim() ? '\n\n## ' + prompt + '\n\n' : prompt + '\n\n';
            diaryContent.value += prefix;
            focusDiaryEditor();
        }

        function insertDiaryText(before, after) {
            const input = diaryEditorInput.value;
            if (!input) return;
            const start = input.selectionStart;
            const end = input.selectionEnd;
            const selected = diaryContent.value.slice(start, end);
            diaryContent.value = diaryContent.value.slice(0, start) + before + selected + after + diaryContent.value.slice(end);
            nextTick(function() {
                input.focus();
                input.setSelectionRange(start + before.length, start + before.length + selected.length);
            });
        }

        function insertDiaryLinePrefix(prefix) {
            const input = diaryEditorInput.value;
            if (!input) return;
            const start = input.selectionStart;
            const end = input.selectionEnd;
            const lineStart = diaryContent.value.lastIndexOf('\n', start - 1) + 1;
            const selected = diaryContent.value.slice(lineStart, end);
            const replaced = selected.split('\n').map(function(line) { return prefix + line; }).join('\n');
            diaryContent.value = diaryContent.value.slice(0, lineStart) + replaced + diaryContent.value.slice(end);
            nextTick(function() { input.focus(); input.setSelectionRange(lineStart + prefix.length, lineStart + replaced.length); });
        }

        watch([diaryContent, diaryTags, diaryMood], scheduleDiaryDraft);

        const saveDiary = async function() {
            if (!diaryContent.value.trim()) return;
            savingDiary.value = true;
            const body = JSON.stringify({
                content: diaryContent.value.trim(),
                tags: diaryTags.value.trim() || null,
                mood: diaryMood.value.trim() || null
            });
            try {
                if (editingDiaryId.value) {
                    await apiReq('/diary/' + editingDiaryId.value, { method: 'PUT', body: body });
                } else {
                    await apiReq('/diary', { method: 'POST', body: body });
                }
                showToast(editingDiaryId.value ? '日记已更新' : '日记已添加');
                clearDiaryDraft();
                diaryInitialSnapshot.value = diarySnapshot();
                showDiaryEditorModal.value = false;
                loadDiaries();
            } catch (e) { showToast(e.message, 'error'); }
            savingDiary.value = false;
        };

        // Books
        const books = ref([]);
        const booksLoading = ref(false);
        const currentBookIndex = ref(-1);
        const showBookDetail = ref(false);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const showAddBookModal = ref(false);
        const editingBookId = ref(null);
        const newBookTitle = ref('');
        const newBookAuthor = ref('');
        const newBookCategory = ref('');
        const newBookCover = ref('');
        const newBookIntroduction = ref('');
        const addingBook = ref(false);
        const showViewpointModal = ref(false);
        const editingViewpointId = ref(null);
        const viewpointTitle = ref('');
        const viewpointContent = ref('');
        const viewpointExample = ref('');
        const savingViewpoint = ref(false);
        const currentBook = computed(function() { return books.value[currentBookIndex.value] || null; });

        async function loadBooks(search) {
            booksLoading.value = true;
            try {
                const endpoint = search && searchKeyword.value
                    ? '/books/search?q=' + encodeURIComponent(searchKeyword.value)
                    : '/books';
                const data = await apiReq(endpoint);
                const selectedBookId = currentBook.value && currentBook.value.id;
                books.value = data.items || [];
                if (books.value.length > 0 && showBookDetail.value) {
                    const preservedIndex = books.value.findIndex(function(book) { return book.id === selectedBookId; });
                    currentBookIndex.value = preservedIndex >= 0 ? preservedIndex : 0;
                    await loadViewpoints(books.value[currentBookIndex.value].id);
                } else if (!showBookDetail.value || books.value.length === 0) currentBookIndex.value = -1;
            } catch (e) { showToast(e.message, 'error'); }
            booksLoading.value = false;
        }

        const selectBook = async function(i) {
            currentBookIndex.value = i;
            showBookDetail.value = true;
            if (books.value[i]) await loadViewpoints(books.value[i].id);
        };

        function closeBookDetail() {
            showBookDetail.value = false;
            currentBookIndex.value = -1;
            currentBookViewpoints.value = [];
        }

        async function loadViewpoints(bookId) {
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            try {
                const data = await apiReq('/books/' + bookId + '/viewpoints');
                currentBookViewpoints.value = data.items || [];
            } catch (e) {}
            bookViewpointsLoading.value = false;
        }

        const openBookEditor = function(book) {
            editingBookId.value = book ? book.id : null;
            newBookTitle.value = book ? book.title : '';
            newBookAuthor.value = book ? book.author : '';
            newBookCategory.value = book ? book.category : '';
            newBookCover.value = book ? book.coverImage : '';
            newBookIntroduction.value = book ? book.introduction : '';
            showAddBookModal.value = true;
        };

        const submitNewBook = async function() {
            if (!newBookTitle.value.trim()) return;
            addingBook.value = true;
            try {
                const body = JSON.stringify({
                    title: newBookTitle.value.trim(), author: newBookAuthor.value.trim(),
                    category: newBookCategory.value.trim(), coverImage: newBookCover.value.trim(),
                    introduction: newBookIntroduction.value.trim()
                });
                const endpoint = editingBookId.value ? '/books/' + editingBookId.value : '/books';
                await apiReq(endpoint, { method: editingBookId.value ? 'PUT' : 'POST', body: body });
                showToast(editingBookId.value ? '书籍已更新' : '书籍已添加');
                showAddBookModal.value = false;
                loadBooks();
            } catch (e) { showToast(e.message, 'error'); }
            addingBook.value = false;
        };

        const openViewpointEditor = function(viewpoint) {
            editingViewpointId.value = viewpoint ? viewpoint.id : null;
            viewpointTitle.value = viewpoint ? viewpoint.title : '';
            viewpointContent.value = viewpoint ? viewpoint.content : '';
            viewpointExample.value = viewpoint ? viewpoint.example : '';
            showViewpointModal.value = true;
        };

        const saveViewpoint = async function() {
            if (!currentBook.value || !viewpointTitle.value.trim()) return;
            savingViewpoint.value = true;
            try {
                const body = JSON.stringify({ title: viewpointTitle.value.trim(), content: viewpointContent.value.trim(), example: viewpointExample.value.trim() });
                const endpoint = editingViewpointId.value
                    ? '/books/viewpoints/' + editingViewpointId.value
                    : '/books/' + currentBook.value.id + '/viewpoints';
                await apiReq(endpoint, { method: editingViewpointId.value ? 'PUT' : 'POST', body: body });
                showToast(editingViewpointId.value ? '感悟已更新' : '感悟已添加');
                showViewpointModal.value = false;
                loadViewpoints(currentBook.value.id);
            } catch (e) { showToast(e.message, 'error'); }
            savingViewpoint.value = false;
        };

        // AI assistant
        const aiSessions = ref([]);
        const aiSessionsLoading = ref(false);
        const activeAiSessionId = ref('');
        const aiMessages = ref([]);
        const aiMessagesLoading = ref(false);
        const aiInput = ref('');
        const aiSending = ref(false);

        async function loadAiSessions() {
            aiSessionsLoading.value = true;
            try {
                const data = await apiReq('/ai/sessions');
                aiSessions.value = data.items || [];
                if (!activeAiSessionId.value && aiSessions.value.length) {
                    await selectAiSession(aiSessions.value[0].id);
                }
            } catch (e) { showToast(e.message, 'error'); }
            aiSessionsLoading.value = false;
        }

        async function selectAiSession(sessionId) {
            activeAiSessionId.value = sessionId;
            aiMessagesLoading.value = true;
            try {
                const data = await apiReq('/ai/sessions/' + encodeURIComponent(sessionId));
                aiMessages.value = data.items || [];
                scrollAiToBottom();
            } catch (e) { showToast(e.message, 'error'); }
            aiMessagesLoading.value = false;
        }

        function newAiSession() {
            activeAiSessionId.value = 'web_' + Date.now();
            aiMessages.value = [];
            aiInput.value = '';
        }

        async function deleteAiSession(sessionId) {
            try {
                await apiReq('/ai/sessions/' + encodeURIComponent(sessionId), { method: 'DELETE' });
                aiSessions.value = aiSessions.value.filter(function(session) { return session.id !== sessionId; });
                if (activeAiSessionId.value === sessionId) newAiSession();
                showToast('对话已删除');
            } catch (e) { showToast(e.message, 'error'); }
        }

        async function sendAiMessage() {
            const query = aiInput.value.trim();
            if (!query || aiSending.value) return;
            if (!activeAiSessionId.value) newAiSession();
            aiMessages.value.push({ role: 'user', content: query, createdAt: Date.now(), references: [] });
            aiInput.value = '';
            aiSending.value = true;
            scrollAiToBottom();
            try {
                const data = await apiReq('/ai/chat', {
                    method: 'POST',
                    body: JSON.stringify({ sessionId: activeAiSessionId.value, query: query })
                });
                activeAiSessionId.value = data.sessionId;
                aiMessages.value.push(data.message);
                await loadAiSessions();
                scrollAiToBottom();
            } catch (e) { showToast(e.message, 'error'); }
            aiSending.value = false;
        }

        function scrollAiToBottom() {
            nextTick(function() {
                var el = document.querySelector('.ai-messages');
                if (el) el.scrollTop = el.scrollHeight;
            });
        }

        // Task center
        const tasks = ref([]);
        const tasksLoading = ref(false);
        const showFinishedTasks = ref(true);
        async function loadTasks() {
            tasksLoading.value = true;
            try {
                const data = await apiReq('/tasks?showTerminal=' + showFinishedTasks.value);
                tasks.value = data.items || [];
            } catch (e) { showToast(e.message, 'error'); }
            tasksLoading.value = false;
        }
        async function cancelTask(id) {
            try { await apiReq('/tasks/' + id + '/cancel', { method: 'POST' }); showToast('任务已取消'); loadTasks(); }
            catch (e) { showToast(e.message, 'error'); }
        }
        function taskTypeName(type) {
            return ({ save_article:'保存文章', remote_article_sync:'同步远程文章', remote_news_fetch:'获取远程新闻', external_favorite_sync:'外部收藏同步', book_viewpoint_generate:'生成书籍观点', diary_attachment_transcribe:'附件转写', diary_knowledge_extract:'日记知识提取' })[type] || type;
        }
        function taskStatusName(status) {
            return ({ queued:'等待中', running:'执行中', retrying:'等待重试', succeeded:'已完成', failed:'已失败', cancelled:'已取消' })[status] || status;
        }

        // Delete
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });
        const confirmDelete = function(type, id) { deleteTarget.value = { type: type, id: id }; showDeleteModal.value = true; };

        const executeDelete = async function() {
            const t = deleteTarget.value;
            const map = { article: 'articles', diary: 'diary', book: 'books', viewpoint: 'books/viewpoints' };
            try {
                await apiReq('/' + (map[t.type] || 'articles') + '/' + t.id, { method: 'DELETE' });
                showToast('已删除');
                showDeleteModal.value = showDetailModal.value = showDiaryDetailModal.value = false;
                if (t.type === 'viewpoint' && currentBook.value) loadViewpoints(currentBook.value.id);
                else loadPage(page.value);
            } catch (e) { showToast(e.message, 'error'); }
        };

        // Image viewer
        const imageViewer = ref({ visible: false, src: '' });

        // Keyboard shortcuts
        function onKeydown(e) {
            if (showDiaryEditorModal.value && (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
                e.preventDefault(); saveDiary(); return;
            }
            if (e.ctrlKey && e.key === 'k') { e.preventDefault(); var el = document.querySelector('.search-input'); if (el) el.focus(); }
            if (e.key === 'Escape') {
                if (selectedDiary.value) selectedDiary.value = null;
                else if (page.value === 'books' && showBookDetail.value) closeBookDetail();
                showDetailModal.value = showDiaryDetailModal.value = false;
                if (showDiaryEditorModal.value) closeDiaryEditor();
                showArticleModal.value = showAddBookModal.value = showDeleteModal.value = false;
                showViewpointModal.value = false;
                imageViewer.value.visible = false;
            }
            if (e.key === 'n' && e.ctrlKey && !e.target.closest('input,textarea')) {
                e.preventDefault();
                if (page.value === 'articles') showArticleModal.value = true;
                if (page.value === 'diary') openDiaryEditor();
                if (page.value === 'books') openBookEditor(null);
                if (page.value === 'ai') newAiSession();
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
            statsCards, recentItems, recentLoading, newsSummary, summaryLoading, summaryRefreshing, refreshNewsSummary,
            articles, articlesLoading, pagination, loadArticles, viewArticle,
            showArticleModal, articleUrl, submitting, submitArticle,
            showDetailModal, detailItem, detailLoading, articleActionLoading, articleTab,
            toggleArticleFavorite, reprocessArticle,
            diaries, diariesLoading, diaryPagination, selectedDiary, selectedDiaryMonth, diaryMonths, filteredDiaries,
            diaryDetailLoading, loadDiaries, viewDiary, closeDiaryReader,
            formatDiaryDay, diaryExcerpt, diaryTagList, diaryDayNumber, diaryWeekday,
            showDiaryDetailModal, showDiaryEditorModal, editingDiaryId,
            diaryContent, diaryTags, diaryMood, savingDiary, editDiary, openDiaryEditor, saveDiary,
            showDiaryPreview, diaryEditorInput, diaryDraftStatus, diaryCharacterCount, diaryReadingMinutes,
            diaryHasUnsavedChanges, diaryDateLabel, diaryCreativePrompt, diaryInspirationPrompts,
            closeDiaryEditor, applyDiaryPrompt, insertDiaryText, insertDiaryLinePrefix,
            books, booksLoading, currentBookIndex, currentBookViewpoints, bookViewpointsLoading,
            currentBook, showBookDetail, selectBook, closeBookDetail, loadBooks,
            showAddBookModal, editingBookId, newBookTitle, newBookAuthor, newBookCategory,
            newBookCover, newBookIntroduction, addingBook, openBookEditor, submitNewBook,
            showViewpointModal, editingViewpointId, viewpointTitle, viewpointContent, viewpointExample,
            savingViewpoint, openViewpointEditor, saveViewpoint,
            aiSessions, aiSessionsLoading, activeAiSessionId, aiMessages, aiMessagesLoading,
            aiInput, aiSending, loadAiSessions, selectAiSession, newAiSession, deleteAiSession, sendAiMessage,
            tasks, tasksLoading, showFinishedTasks, loadTasks, cancelTask, taskTypeName, taskStatusName,
            showDeleteModal, confirmDelete, executeDelete,
            imageViewer,
        };
    }
}).mount('#app');
