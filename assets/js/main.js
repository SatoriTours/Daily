function parseContent() {
    const documentClone = document.cloneNode(true);
    const article = new Readability(documentClone).parse();

    if (window.flutter_inappwebview == null) {
        console.log("flutter 组件没有加载成功")
        return;
     }

    window.flutter_inappwebview.callHandler(
        "getPageContent",
        article.title,
        article.excerpt,
        article.content,
        article.textContent
    )
}
