const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

createApp({
    setup() {
        const isLoggedIn = ref(localStorage.getItem('ds_logged_in') === '1');
        const connected = ref(false);
        const dark = ref(initDark());
        const sidebarCollapsed = ref(localStorage.getItem('ds_sidebar') === '1');

        const loginToken = ref('');
        const loading = ref(false);
        const loginError = ref('');
        const toasts = ref([]);
        let connInterval = null;

        const page = ref('articles');
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
            const controller = new AbortController();
            const timeout = setTimeout(function() { controller.abort(); }, 20000);
            try {
                const res = await fetch('/api/v2' + endpoint, Object.assign({}, options || {}, {
                    credentials: 'same-origin', signal: controller.signal,
                    headers: Object.assign({ 'Content-Type': 'application/json' }, (options || {}).headers || {})
                }));
                if (res.status === 401) {
                    isLoggedIn.value = false;
                    localStorage.removeItem('ds_logged_in');
                    throw Object.assign(new Error('登录已失效，请重新输入手机上的访问令牌；编辑内容仍保留'), { status: 401 });
                }
                const data = await res.json();
                connected.value = true;
                if (!res.ok || data.code !== 0) {
                    if (res.status === 409) editConflict.value = true;
                    throw Object.assign(new Error(data.msg || '请求失败'), { status: res.status });
                }
                return data.data;
            } catch (e) {
                if (!e.status) {
                    connected.value = false;
                    throw new Error('连接中断或超时。修改已保留，请检查同一 Wi-Fi 和手机 Web 服务；重试前确认是否已保存。');
                }
                throw e;
            } finally { clearTimeout(timeout); }
        }

        function formatDate(s) {
            if (!s) return '-';
            const d = new Date(s);
            return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
        }

        function truncate(s, n) { return !s ? '' : s.length > n ? s.substring(0, n) + '...' : s; }

        function safeUrl(value, image) {
            if(!value)return '';
            try {
                const url = new URL(value, location.origin);
                return ['http:', 'https:'].includes(url.protocol) || (!image && url.protocol === 'mailto:') ? url.href : '';
            } catch (e) { return ''; }
        }

        function formatContent(content) {
            if (!content) return '';
            const template = document.createElement('template');
            template.innerHTML = typeof marked !== 'undefined' ? marked.parse(content) : content.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('\n', '<br>');
            const allowed = new Set(['P','BR','H1','H2','H3','H4','H5','H6','STRONG','EM','DEL','S','BLOCKQUOTE','UL','OL','LI','PRE','CODE','HR','A','IMG','TABLE','THEAD','TBODY','TR','TH','TD']);
            Array.from(template.content.querySelectorAll('*')).forEach(function(el) {
                if (!allowed.has(el.tagName)) { el.replaceWith(document.createTextNode(el.textContent)); return; }
                const href = el.getAttribute('href'), src = el.getAttribute('src'), alt = el.getAttribute('alt');
                Array.from(el.attributes).forEach(function(a) { el.removeAttribute(a.name); });
                if (el.tagName === 'A' && safeUrl(href, false)) {
                    el.setAttribute('href', safeUrl(href, false)); el.setAttribute('target', '_blank'); el.setAttribute('rel', 'noopener noreferrer');
                }
                if (el.tagName === 'IMG' && safeUrl(src, true)) {
                    el.setAttribute('src', safeUrl(src, true)); el.setAttribute('alt', alt || '');
                    el.setAttribute('loading', 'lazy'); el.setAttribute('referrerpolicy', 'no-referrer');
                }
            });
            // Images use referrerpolicy="no-referrer" and all event/style attributes are removed.
            return template.innerHTML;
        }

        async function checkConnection() {
            try {
                const r = await fetch('/ping', { signal: AbortSignal.timeout(5000) });
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
                    if (connInterval) clearInterval(connInterval);
                    connInterval = setInterval(checkConnection, 10000);
                    loginToken.value = '';
                    if (!editorOpen.value) loadPage(page.value);
                } else { loginError.value = data.msg || 'Token 错误'; }
            } catch (e) { loginError.value = e.message; }
            loading.value = false;
        };

        const logout = async function() {
            if (!leaveEditor()) return;
            closeEditors();showArticleModal.value=false;showDeleteModal.value=false;imageViewer.value.visible=false;
            try { await fetch('/api/v2/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch (e) {}
            isLoggedIn.value = false;
            localStorage.removeItem('ds_logged_in'); localStorage.removeItem('ds_token'); loginToken.value = '';
            if (connInterval) { clearInterval(connInterval); connInterval = null; }
        };

        const navigate = function(p) { if (!leaveEditor()) return; closeEditors(); articleRequest++;diaryRequest++;bookRequest++; page.value = p; searchKeyword.value = ''; mobileDetail.value = false; loadPage(p); };
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


        const isCollectionPage = computed(function() { return ['articles','diary','books'].includes(page.value); });
        const mobileDetail = ref(false);
        const listError = ref('');
        const editConflict = ref(false);
        const editorDraftStatus = ref('');
        const editorInitial = ref('');
        const editorVersion = ref('');
        const articleFilter = ref('all');
        const showArticleEditor = ref(false);
        const articleEditTitle = ref('');
        const articleEditComment = ref('');
        const savingArticle = ref(false);
        const reloadingEditor = ref(false);
        let articleRequest = 0, diaryRequest = 0, bookRequest = 0, detailRequest = 0;
        const editorOpen = computed(function() { return showArticleEditor.value || showDiaryEditorModal.value || showAddBookModal.value || showViewpointModal.value; });
        const editorBusy = computed(function() { return reloadingEditor.value || savingArticle.value || savingDiary.value || addingBook.value || savingViewpoint.value; });
        const editorDirty = computed(function() { return showDiaryEditorModal.value ? diaryHasUnsavedChanges.value : editorOpen.value && editorSnapshot() !== editorInitial.value; });

        function editorSnapshot() {
            if (showArticleEditor.value) return JSON.stringify({title:articleEditTitle.value,comment:articleEditComment.value});
            if (showViewpointModal.value) return JSON.stringify({title:viewpointTitle.value,content:viewpointContent.value,example:viewpointExample.value});
            if (showAddBookModal.value) return JSON.stringify({title:newBookTitle.value,author:newBookAuthor.value,category:newBookCategory.value,cover:newBookCover.value,introduction:newBookIntroduction.value});
            return '';
        }
        function editorDraftKey() {
            if (showArticleEditor.value) return 'ds_article_draft_' + detailItem.value.id;
            if (showViewpointModal.value) return 'ds_viewpoint_draft_' + (editingViewpointId.value || ('new_' + (currentBook.value || {}).id));
            return 'ds_book_draft_' + (editingBookId.value || 'new');
        }
        function saveEditorDraft() {
            if (!editorOpen.value || showDiaryEditorModal.value || !editorDirty.value) return;
            try { localStorage.setItem(editorDraftKey(), JSON.stringify({snapshot:editorSnapshot(),sourceVersion:editorVersion.value})); editorDraftStatus.value = '草稿已保存到本机'; }
            catch (e) { editorDraftStatus.value = '草稿保存失败，请勿关闭页面'; }
        }
        function beginEditor(version) {
            editorVersion.value = version || ''; editConflict.value = false;
            editorInitial.value = editorSnapshot(); editorDraftStatus.value = '';
            try {
                const saved = JSON.parse(localStorage.getItem(editorDraftKey()) || 'null');
                if (!saved || !saved.snapshot) return;
                const values = JSON.parse(saved.snapshot);
                if (showArticleEditor.value) { articleEditTitle.value=values.title||''; articleEditComment.value=values.comment||''; }
                else if (showViewpointModal.value) { viewpointTitle.value=values.title||'';viewpointContent.value=values.content||'';viewpointExample.value=values.example||''; }
                else { newBookTitle.value=values.title||'';newBookAuthor.value=values.author||'';newBookCategory.value=values.category||'';newBookCover.value=values.cover||'';newBookIntroduction.value=values.introduction||''; }
                editConflict.value = saved.sourceVersion !== editorVersion.value;
                if (editConflict.value) editorVersion.value = saved.sourceVersion || '';
                editorDraftStatus.value = '已恢复本机草稿';
            } catch (e) { editorDraftStatus.value = '草稿读取失败，请保留原草稿'; }
        }
        function leaveEditor() {
            if (editorBusy.value) { showToast('正在保存，请稍候'); return false; }
            if (!editorDirty.value) return true;
            if (showDiaryEditorModal.value) persistDiaryDraft(); else saveEditorDraft();
            const status=showDiaryEditorModal.value?diaryDraftStatus.value:editorDraftStatus.value;
            return window.confirm(status.includes('失败')?'草稿保存失败，离开可能丢失修改。建议先导出草稿。仍要离开？':'修改尚未保存到手机。确定离开编辑器？本机草稿将保留。');
        }
        function closeEditors() {
            detailRequest++; detailLoading.value=false;diaryDetailLoading.value=false;
            persistDiaryDraft(); saveEditorDraft();
            if (diaryDraftTimer) clearTimeout(diaryDraftTimer);
            showArticleEditor.value = showDiaryEditorModal.value = showAddBookModal.value = showViewpointModal.value = false;
            editConflict.value = false;
        }
        function cancelEditor() { if (leaveEditor()) closeEditors(); }
        function refreshCollection() { loadPage(page.value); checkConnection(); }
        function newItem() {
            if (!leaveEditor()) return;
            closeEditors(); mobileDetail.value=true;
            if (page.value==='articles') showArticleModal.value=true;
            else if (page.value==='diary') openDiaryEditor();
            else openBookEditor(null);
        }
        function openArticleEditor() {
            if (!leaveEditor()) return;
            closeEditors(); articleEditTitle.value=detailItem.value.title||detailItem.value.aiTitle||'';
            articleEditComment.value=detailItem.value.comment||'';showArticleEditor.value=true;
            beginEditor(detailItem.value.version);
        }
        async function saveArticle() {
            if (editorBusy.value || !articleEditTitle.value.trim()) return;
            savingArticle.value=true;
            try {
                const result=await apiReq('/articles/'+detailItem.value.id,{method:'PUT',body:JSON.stringify({title:articleEditTitle.value.trim(),comment:articleEditComment.value,version:editorVersion.value})});
                Object.assign(detailItem.value,result); localStorage.removeItem(editorDraftKey());
                showArticleEditor.value=false; editConflict.value=false; loadArticles(pagination.value.page); showToast('文章已更新');
            } catch(e) { saveEditorDraft(); showToast(e.message,'error'); }
            savingArticle.value=false;
        }
        async function reloadEditor() {
            if(editorBusy.value)return;
            if (!window.confirm('重新读取会替换当前编辑文字，并清除本机草稿。请先导出草稿。仍要继续吗？')) return;
            const kind=showArticleEditor.value?'article':showDiaryEditorModal.value?'diary':showViewpointModal.value?'viewpoint':'book';
            const id=kind==='article'?detailItem.value.id:kind==='diary'?editingDiaryId.value:kind==='viewpoint'?editingViewpointId.value:editingBookId.value;
            if (!id) return;
            reloadingEditor.value=true;
            try {
                const endpoint={article:'/articles/',diary:'/diary/',viewpoint:'/books/viewpoints/',book:'/books/'}[kind];
                const fresh=await apiReq(endpoint+id);
                // Do not restore the stale draft over the explicitly reloaded record.
                if(kind==='diary') { clearDiaryDraft(); diaryContent.value=fresh.content||'';diaryTags.value=fresh.tags||'';diaryMood.value=fresh.mood||'';diarySourceVersion.value=fresh.version;diaryInitialSnapshot.value=diarySnapshot(); }
                else {
                    localStorage.removeItem(editorDraftKey());
                    if(kind==='article') {detailItem.value=fresh;articleEditTitle.value=fresh.title||'';articleEditComment.value=fresh.comment||'';}
                    else if(kind==='viewpoint') {viewpointTitle.value=fresh.title;viewpointContent.value=fresh.content;viewpointExample.value=fresh.example;}
                    else {newBookTitle.value=fresh.title;newBookAuthor.value=fresh.author;newBookCategory.value=fresh.category;newBookCover.value=fresh.coverImage;newBookIntroduction.value=fresh.introduction;}
                    editorVersion.value=fresh.version;editorInitial.value=editorSnapshot();
                }
                editConflict.value=false;
            } catch(e) {showToast(e.message,'error');}
            finally{reloadingEditor.value=false;}
        }
        function downloadCurrentDraft() {
            const content=showDiaryEditorModal.value?diaryContent.value:editorSnapshot();
            const url=URL.createObjectURL(new Blob([content],{type:'text/plain;charset=utf-8'}));
            const a=document.createElement('a');a.href=url;a.download='Daily-Satori-draft.txt';a.click();setTimeout(function(){URL.revokeObjectURL(url);},1000);
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

        async function loadArticles(p) {
            p = p || 1;
            const request = ++articleRequest; articlesLoading.value = true; listError.value = '';
            try {
                const query = searchKeyword.value.trim();
                const ep = (query ? '/articles/search?q=' + encodeURIComponent(query) + '&' : '/articles?') +
                    'page=' + p + '&favorites=' + (articleFilter.value === 'favorite');
                const data = await apiReq(ep);
                if (request !== articleRequest) return;
                articles.value = data.items || [];
                pagination.value = data.pagination || {page:1,totalPages:1,totalItems:articles.value.length};
            } catch (e) { if(request===articleRequest) listError.value=e.message; }
            finally { if(request===articleRequest) articlesLoading.value=false; }
        }
        const viewArticle = async function(a) {
            if (!leaveEditor()) return;
            closeEditors(); mobileDetail.value=true;
            const request=++detailRequest; showDetailModal.value=true;detailLoading.value=true;detailItem.value={};articleTab.value='summary';
            try { const item=await apiReq('/articles/'+a.id);if(request===detailRequest)detailItem.value=item; }
            catch(e) { if(request===detailRequest)showToast(e.message,'error'); }
            finally {if(request===detailRequest)detailLoading.value=false;}
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
                Object.assign(detailItem.value,result);
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
        const showDiaryPreview = ref(true);
        const diaryEditorInput = ref(null);
        const diaryInitialSnapshot = ref('');
        const diarySourceUpdatedAt = ref(0);
        const diarySourceVersion = ref('');
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

        async function loadDiaries(p) {
            p=p||1; const request=++diaryRequest; diariesLoading.value=true;listError.value='';
            try {
                const query=searchKeyword.value.trim();
                const ep=(query?'/diary/search?q='+encodeURIComponent(query)+'&':'/diary?')+'page='+p;
                const data=await apiReq(ep);if(request!==diaryRequest)return;
                diaries.value=data.items||[];
                diaryPagination.value=data.pagination||{page:1,totalPages:1,totalItems:diaries.value.length};
            } catch(e) {if(request===diaryRequest)listError.value=e.message;}
            finally{if(request===diaryRequest)diariesLoading.value=false;}
        }
        const viewDiary=async function(d){
            if(!d||!leaveEditor())return;
            closeEditors();mobileDetail.value=true;const request=++detailRequest;diaryDetailLoading.value=true;selectedDiary.value=null;
            try{const item=await apiReq('/diary/'+d.id);if(request===detailRequest)selectedDiary.value=item;}
            catch(e){if(request===detailRequest)showToast(e.message,'error');}
            finally{if(request===detailRequest)diaryDetailLoading.value=false;}
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
            if (!leaveEditor()) return;
            closeEditors(); mobileDetail.value=true; editConflict.value=false;
            editingDiaryId.value = d.id;
            diaryContent.value = d.content || '';
            diaryTags.value = d.tags || '';
            diaryMood.value = d.mood || '';
            diarySourceUpdatedAt.value = d.updatedAt || 0;
            diarySourceVersion.value = d.version || '';
            diaryInitialSnapshot.value = diarySnapshot();
            showDiaryDetailModal.value = false;
            showDiaryEditorModal.value = true;
            restoreDiaryDraft();
            focusDiaryEditor();
        };

        const openDiaryEditor = function() {
            if (!leaveEditor()) return;
            closeEditors(); mobileDetail.value=true; editConflict.value=false;
            editingDiaryId.value = null;
            diaryContent.value = ''; diaryTags.value = ''; diaryMood.value = '';
            diarySourceUpdatedAt.value = 0; diarySourceVersion.value='';
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
                editConflict.value = !!editingDiaryId.value && saved.sourceVersion !== diarySourceVersion.value;
                if(editConflict.value) diarySourceVersion.value=saved.sourceVersion||'';
                const draft = JSON.parse(saved.snapshot);
                diaryContent.value = draft.content || '';
                diaryTags.value = draft.tags || '';
                diaryMood.value = draft.mood || '';
                diaryDraftStatus.value = '已恢复未保存草稿';
                showToast('已恢复上次未完成的日记草稿');
            } catch (e) { localStorage.removeItem(diaryDraftKey()); }
        }

        function persistDiaryDraft() {
            if(!showDiaryEditorModal.value || !diaryHasUnsavedChanges.value)return;
            try {
                localStorage.setItem(diaryDraftKey(),JSON.stringify({snapshot:diarySnapshot(),sourceUpdatedAt:diarySourceUpdatedAt.value,sourceVersion:diarySourceVersion.value,savedAt:Date.now()}));
                diaryDraftStatus.value='草稿已保存到本机';
            } catch(e) {diaryDraftStatus.value='草稿保存失败，请勿关闭页面';}
        }
        function scheduleDiaryDraft() {
            if(!showDiaryEditorModal.value)return;
            if(diaryDraftTimer)clearTimeout(diaryDraftTimer);
            diaryDraftTimer=setTimeout(persistDiaryDraft,650);
        }

        function clearDiaryDraft() {
            if (diaryDraftTimer) clearTimeout(diaryDraftTimer);
            diaryDraftTimer = null;
            localStorage.removeItem(diaryDraftKey());
            diaryDraftStatus.value = '';
        }

        function closeDiaryEditor() { cancelEditor(); }

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
            if (editorBusy.value || !diaryContent.value.trim()) return;
            savingDiary.value = true;
            const body = JSON.stringify({
                content: diaryContent.value.trim(),
                tags: diaryTags.value.trim() || null,
                mood: diaryMood.value.trim() || null, version: diarySourceVersion.value
            });
            try {
                const saved = await apiReq(editingDiaryId.value ? '/diary/' + editingDiaryId.value : '/diary', { method: editingDiaryId.value ? 'PUT' : 'POST', body: body });
                selectedDiary.value=Object.assign({},selectedDiary.value||{},saved);
                showToast(editingDiaryId.value ? '日记已更新' : '日记已添加');
                clearDiaryDraft();
                diaryInitialSnapshot.value = diarySnapshot();
                showDiaryEditorModal.value = false; editConflict.value=false;
                loadDiaries(diaryPagination.value.page);
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
            const request=++bookRequest; booksLoading.value = true;listError.value='';
            try {
                const endpoint = searchKeyword.value
                    ? '/books/search?q=' + encodeURIComponent(searchKeyword.value)
                    : '/books';
                const data = await apiReq(endpoint);
                if(request!==bookRequest)return;
                const selectedBookId = currentBook.value && currentBook.value.id;
                books.value = data.items || [];
                if (books.value.length > 0 && showBookDetail.value) {
                    const preservedIndex = books.value.findIndex(function(book) { return book.id === selectedBookId; });
                    currentBookIndex.value = preservedIndex >= 0 ? preservedIndex : 0;
                    await loadViewpoints(books.value[currentBookIndex.value].id);
                } else if (!showBookDetail.value || books.value.length === 0) currentBookIndex.value = -1;
            } catch (e) { if(request===bookRequest)listError.value=e.message; }
            finally {if(request===bookRequest)booksLoading.value=false;}
        }

        const selectBook = async function(i) {
            if(!leaveEditor())return;closeEditors();mobileDetail.value=true;
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
            const request=++detailRequest;
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            try {
                const data = await apiReq('/books/' + bookId + '/viewpoints');
                if(request===detailRequest)currentBookViewpoints.value = data.items || [];
            } catch (e) { if(request===detailRequest)showToast(e.message,'error'); }
            finally{if(request===detailRequest)bookViewpointsLoading.value=false;}
        }

        const openBookEditor = function(book) {
            if(!leaveEditor())return;closeEditors();mobileDetail.value=true;
            editingBookId.value = book ? book.id : null;
            newBookTitle.value = book ? book.title : '';
            newBookAuthor.value = book ? book.author : '';
            newBookCategory.value = book ? book.category : '';
            newBookCover.value = book ? book.coverImage : '';
            newBookIntroduction.value = book ? book.introduction : '';
            showAddBookModal.value = true;beginEditor(book&&book.version);
        };

        const submitNewBook = async function() {
            if (editorBusy.value || !newBookTitle.value.trim()) return;
            addingBook.value = true;
            try {
                const body = JSON.stringify({
                    title: newBookTitle.value.trim(), author: newBookAuthor.value.trim(),
                    category: newBookCategory.value.trim(), coverImage: newBookCover.value.trim(),
                    introduction: newBookIntroduction.value.trim(), version:editorVersion.value
                });
                const endpoint = editingBookId.value ? '/books/' + editingBookId.value : '/books';
                await apiReq(endpoint, { method: editingBookId.value ? 'PUT' : 'POST', body: body });
                showToast(editingBookId.value ? '书籍已更新' : '书籍已添加');
                localStorage.removeItem(editorDraftKey());showAddBookModal.value = false;editConflict.value=false;
                loadBooks();
            } catch (e) { showToast(e.message, 'error'); }
            addingBook.value = false;
        };

        const openViewpointEditor = function(viewpoint) {
            if(!leaveEditor())return;closeEditors();mobileDetail.value=true;
            editingViewpointId.value = viewpoint ? viewpoint.id : null;
            viewpointTitle.value = viewpoint ? viewpoint.title : '';
            viewpointContent.value = viewpoint ? viewpoint.content : '';
            viewpointExample.value = viewpoint ? viewpoint.example : '';
            showViewpointModal.value = true;beginEditor(viewpoint&&viewpoint.version);
        };

        const saveViewpoint = async function() {
            if (editorBusy.value || !currentBook.value || !viewpointTitle.value.trim()) return;
            savingViewpoint.value = true;
            try {
                const body = JSON.stringify({ title: viewpointTitle.value.trim(), content: viewpointContent.value.trim(), example: viewpointExample.value.trim(), version:editorVersion.value });
                const endpoint = editingViewpointId.value
                    ? '/books/viewpoints/' + editingViewpointId.value
                    : '/books/' + currentBook.value.id + '/viewpoints';
                await apiReq(endpoint, { method: editingViewpointId.value ? 'PUT' : 'POST', body: body });
                showToast(editingViewpointId.value ? '感悟已更新' : '感悟已添加');
                localStorage.removeItem(editorDraftKey());showViewpointModal.value = false;editConflict.value=false;
                loadViewpoints(currentBook.value.id);
            } catch (e) { showToast(e.message, 'error'); }
            savingViewpoint.value = false;
        };

        let editorDraftTimer=null;
        function scheduleEditorDraft(){clearTimeout(editorDraftTimer);editorDraftTimer=setTimeout(saveEditorDraft,400);}
        watch([articleEditTitle,articleEditComment,newBookTitle,newBookAuthor,newBookCategory,newBookCover,newBookIntroduction,viewpointTitle,viewpointContent,viewpointExample],scheduleEditorDraft);

        const collectionItems=computed(function(){return page.value==='articles'?articles.value:page.value==='diary'?filteredDiaries.value:books.value;});
        const collectionLoading=computed(function(){return page.value==='articles'?articlesLoading.value:page.value==='diary'?diariesLoading.value:booksLoading.value;});
        const collectionPagination=computed(function(){return page.value==='articles'?pagination.value:page.value==='diary'?diaryPagination.value:{page:1,totalPages:1};});
        const collectionCount=computed(function(){return page.value==='books'?books.value.length:(collectionPagination.value.totalItems||collectionItems.value.length);});
        const selectedItemId=computed(function(){return page.value==='articles'?detailItem.value.id:page.value==='diary'?(selectedDiary.value||{}).id:(currentBook.value||{}).id;});
        const diaryEditorDate=computed(function(){const d=diaries.value.find(function(d){return d.id===editingDiaryId.value;});return d?formatDiaryDay(d.createdAt,true):diaryDateLabel.value;});
        function articleSource(url){try{return new URL(url).hostname.replace(/^www\./,'');}catch(e){return '个人收藏';}}
        function selectItem(item){if(page.value==='articles')viewArticle(item);else if(page.value==='diary')viewDiary(item);else selectBook(books.value.findIndex(function(b){return b.id===item.id;}));}
        function collectionPage(p){if(page.value==='articles')loadArticles(p);else loadDiaries(p);}
        function saveActiveEditor(){if(showDiaryEditorModal.value)saveDiary();else if(showArticleEditor.value)saveArticle();else if(showViewpointModal.value)saveViewpoint();else submitNewBook();}

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

        const deleting = ref(false);
        const executeDelete = async function() {
            if(deleting.value||!leaveEditor())return;deleting.value=true;
            const t = deleteTarget.value;
            const map = { article: 'articles', diary: 'diary', book: 'books', viewpoint: 'books/viewpoints' };
            try {
                await apiReq('/' + (map[t.type] || 'articles') + '/' + t.id, { method: 'DELETE' });
                showToast('已删除');
                showDeleteModal.value = showDetailModal.value = showDiaryDetailModal.value = false;
                if (t.type === 'viewpoint' && currentBook.value) loadViewpoints(currentBook.value.id);
                else { if(t.type==='diary')selectedDiary.value=null;if(t.type==='book')closeBookDetail();loadPage(page.value); }
            } catch (e) { showToast(e.message, 'error'); }
            finally {deleting.value=false;}
        };

        // Image viewer
        const imageViewer = ref({ visible: false, src: '' });

        // Keyboard and page-lifecycle guards use the same editor ownership rules.
        function onKeydown(e) {
            if ((e.ctrlKey||e.metaKey) && e.key.toLowerCase()==='s' && editorOpen.value) {
                e.preventDefault();
                saveActiveEditor();
                return;
            }
            if (((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k') || (e.key==='/'&&!e.target.closest('input,textarea,select'))) {
                e.preventDefault();const el=document.querySelector('.search-input');if(el)el.focus();
            }
            if(e.key==='Escape') {
                if(imageViewer.value.visible){imageViewer.value.visible=false;return;}
                if(showDeleteModal.value){showDeleteModal.value=false;return;}
                if(editorOpen.value){cancelEditor();return;}
                showArticleModal.value=false;mobileDetail.value=false;
            }
        }
        function beforeUnload(e) {
            persistDiaryDraft();saveEditorDraft();
            if(editorDirty.value||editorBusy.value){e.preventDefault();e.returnValue='';}
        }

        // Init
        onMounted(async function() {
            document.addEventListener('keydown', onKeydown);
            window.addEventListener('beforeunload',beforeUnload);
            document.addEventListener('visibilitychange',function(){persistDiaryDraft();saveEditorDraft();});
            if (!isLoggedIn.value) return;
            try {
                const data = await apiReq('/auth/status');
                if (data.authenticated) {
                    checkConnection();
                    if (connInterval) clearInterval(connInterval);
                    connInterval = setInterval(checkConnection, 10000);
                    loadPage(page.value);
                } else { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
            } catch (e) { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
        });

        watch(sidebarCollapsed, function(v) { localStorage.setItem('ds_sidebar', v ? '1' : '0'); });

        return {
            isLoggedIn, connected, dark, sidebarCollapsed, toggleTheme,
            collectionItems,collectionLoading,collectionPagination,collectionCount,selectedItemId,diaryEditorDate,articleSource,selectItem,collectionPage,saveActiveEditor,
            isCollectionPage,mobileDetail,listError,editConflict,editorDraftStatus,editorDirty,editorBusy,editorOpen,
            articleFilter,showArticleEditor,articleEditTitle,articleEditComment,savingArticle,openArticleEditor,saveArticle,
            cancelEditor,refreshCollection,newItem,reloadEditor,downloadCurrentDraft,safeUrl,
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
            showDeleteModal, confirmDelete, executeDelete,deleting,checkConnection,
            imageViewer,
        };
    }
}).mount('#app');
