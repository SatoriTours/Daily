// Code-level client regression tests; no Android, browser or extra dependencies.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const root = require('node:path').resolve(__dirname, '../app/src/main/assets/website');
function client() {
    const storage = new Map(), pending = [], timers = new Set();
    const context = vm.createContext({ console, URL, AbortController, setTimeout: (fn, ms) => {
        const timer = setTimeout(fn, ms); timer.unref(); timers.add(timer); return timer;
    }, clearTimeout, setInterval: () => 1, clearInterval, location: { origin: 'http://phone:8080' },
        localStorage: { getItem: k => storage.get(k) || null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) },
        document: { createElement: () => ({}), documentElement: { setAttribute() {} }, addEventListener() {}, querySelector() {} },
        window: { matchMedia: () => ({ matches: false }), addEventListener() {}, confirm: () => false },
        fetch: (url, options) => new Promise((resolve, reject) => pending.push({url, options, resolve, reject})) });
    vm.runInContext(fs.readFileSync(root + '/js/lib/vue.global.prod.min.js', 'utf8'), context);
    vm.runInContext('Vue.onMounted = () => {}; Vue.createApp = options => ({ mount: () => { globalThis.state = options.setup(); } });', context);
    vm.runInContext(fs.readFileSync(root + '/js/app.js', 'utf8'), context);
    return { state: context.state, pending, storage, next: context.Vue.nextTick, window: context.window, localStorage:context.localStorage,
        respond: (request, data, status = 200) => request.resolve({ ok: status < 400, status, json: async () => ({code: status < 400 ? 0 : 1, data, msg: 'conflict'}) }),
        close: () => timers.forEach(clearTimeout) };
}

test('article search survives pagination and a stale response cannot replace newer results', async () => {
    const c = client();try {
        c.state.searchKeyword.value = 'knowledge';
        const first = c.state.loadArticles(1, true);
        const second = c.state.loadArticles(2);
        assert.match(c.pending[1].url, /search.*knowledge.*page=2/);
        c.respond(c.pending[1], {items:[{id:2}],pagination:{page:2,totalPages:2}});await second;
        c.respond(c.pending[0], {items:[{id:1}]});await first;
        assert.equal(c.state.articles.value[0].id, 2);
    } finally { c.close(); }
});

test('article editor preserves a failed save and sends the original version', async () => {
    const c = client();try {
        c.state.detailItem.value = {id:1,title:'original',comment:'',version:'v1'};
        c.state.openArticleEditor();
        c.state.articleEditComment.value = 'my work';
        const saving = c.state.saveArticle();
        assert.equal(JSON.parse(c.pending[0].options.body).version, 'v1');
        c.respond(c.pending[0], null, 409);await saving;
        assert.equal(c.state.showArticleEditor.value, true);
        assert.equal(c.state.articleEditComment.value, 'my work');
        assert.equal(c.state.editConflict.value, true);
    } finally { c.close(); }
});

test('diary draft from an older phone revision remains recoverable', async () => {
    const c = client();try {
        c.storage.set('ds_diary_draft_1', JSON.stringify({snapshot:JSON.stringify({content:'unsaved work',tags:'life',mood:'calm'}), sourceVersion:'old'}));
        c.state.editDiary({id:1,content:'new phone text',version:'new'});await c.next();
        assert.equal(c.state.diaryContent.value, 'unsaved work');
        assert.equal(c.state.editConflict.value, true);
    } finally { c.close(); }
});

test('401 requests return to login without erasing unsaved content', async () => {
    const c = client();try {
        c.state.isLoggedIn.value = true;
        c.state.editDiary({id:1,content:'start',version:'v1'});
        c.state.diaryContent.value='keep this';
        const saving=c.state.saveDiary();c.respond(c.pending[0],null,401);await saving;
        assert.equal(c.state.isLoggedIn.value,false);
        assert.equal(c.state.diaryContent.value,'keep this');
        assert.equal(c.state.showDiaryEditorModal.value,true);
    } finally { c.close(); }
});

test('navigation refuses to discard changed book fields', () => {
    const c = client();try {
        c.state.page.value='books';c.state.openBookEditor({id:1,title:'old',version:'v1'});
        c.state.newBookTitle.value='changed';c.state.navigate('articles');
        assert.equal(c.state.page.value,'books');
        assert.equal(c.state.showAddBookModal.value,true);
    } finally { c.close(); }
});

test('explicit reload warns that it clears the draft and only discards after confirmation', async () => {
    const c=client();try {
        c.state.detailItem.value={id:1,title:'phone',comment:'',version:'old'};
        c.state.openArticleEditor();c.state.articleEditComment.value='my draft';
        c.storage.set('ds_article_draft_1','backup');
        let prompt='';c.window.confirm=message=>{prompt=message;return false;};
        await c.state.reloadEditor();
        assert.match(prompt,/清除本机草稿/);assert.match(prompt,/先导出/);
        assert.equal(c.state.articleEditComment.value,'my draft');assert.equal(c.storage.get('ds_article_draft_1'),'backup');
        assert.equal(c.pending.length,0);
        c.window.confirm=()=>true;const reading=c.state.reloadEditor();
        assert.equal(c.state.editorBusy.value,true);
        const ignoredSave=c.state.saveArticle();await ignoredSave;assert.equal(c.pending.length,1);
        c.respond(c.pending[0],{id:1,title:'latest phone',comment:'new',version:'new'});await reading;
        assert.equal(c.state.articleEditTitle.value,'latest phone');assert.equal(c.storage.has('ds_article_draft_1'),false);
        assert.equal(c.state.editorBusy.value,false);
    } finally {c.close();}
});

test('a failed local draft write warns before navigation can discard edits', () => {
    const c=client();try {
        c.state.page.value='books';c.state.openBookEditor({id:1,title:'old',version:'v1'});
        c.state.newBookTitle.value='changed';c.localStorage.setItem=()=>{throw Error('quota');};
        let prompt='';c.window.confirm=message=>{prompt=message;return false;};c.state.navigate('articles');
        assert.match(prompt,/草稿保存失败/);assert.match(prompt,/丢失修改/);assert.equal(c.state.page.value,'books');
    } finally {c.close();}
});
