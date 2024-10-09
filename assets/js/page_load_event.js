// 用下面几个方法判断页面是否加载完成都不太靠谱,还要想其他的办法


// 1. 用 页面加载状态判断,会监听不到ajax的请求
var pageLoaded = false;

console.log("page_load_event.js is loaded");

// 使用 DOMContentLoaded 事件
document.addEventListener("DOMContentLoaded", function() {
    console.log("DOM fully loaded and parsed");
    // 在这里执行你的代码
});

window.addEventListener('load', function() {
    console.log('所有资源加载完成！');
});

// 检查 document.readyState
function checkReadyState() {
    if (document.readyState === "complete") {
        console.log("Document is fully loaded, including all resources.");
        pageLoaded = true;
    } else {
        console.log("Document is not fully loaded yet.");
    }
}

// 在页面加载时检查状态
window.onload = function() {
    checkReadyState();
};


// 2. 用 ajax的请求是否都完成了来判断,如果页面有定时器,就还不行
(function() {
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function() {
        this.addEventListener('load', function() {
            console.log('AJAX 请求完成！');
            // 在这里可以添加你的逻辑
        });
        originalOpen.apply(this, arguments);
    };
})();

// 3. 还可以判断body里面是否有元素了, 这个好像也不太通用, 虽然twitter加载的时候, 开始body里面没有东西, 然后用ajax请求完之后才加入了内容
// 4. 还可以用比较土的方法, 就是用定时器来
// 5. 目前我选择让人来, 直接看到页面内容后再点保存按钮