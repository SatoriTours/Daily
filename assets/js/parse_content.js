function parseContent() {
    const documentClone = document.cloneNode(true);
    const article = new Readability(documentClone).parse();

    if (window.flutter_inappwebview == null) {
        console.log("flutter 组件没有加载成功")
        return;
    }

    removeAdNode();

    let image = getOgImage();
    if(image == '') {
        image = getMainImage();
    }

    window.flutter_inappwebview.callHandler(
        "getPageContent",
        window.location.href, // 获取当前网页的URL
        article.title,
        article.excerpt,
        article.content,
        article.textContent,
        article.publishedTime,
        image
    )
}

function getOgImage() {
    const metaTags = document.getElementsByTagName('meta');
    let ogImage = '';

    for (const element of metaTags) {
        if (element.getAttribute('property') === 'og:image') {
            ogImage = element.getAttribute('content');
            break;
        }
    }
    console.log("og:image:", ogImage);
    return ogImage;
}


function getMainImage() {
    const images = document.getElementsByTagName('img');
    let mainImage = null;

    for (let img of images) {
        if (img.naturalWidth > 500 && img.naturalHeight > 500) { // 根据尺寸过滤
            mainImage = img.src;
            break; // 找到第一个符合条件的图片
        }
    }

    if (mainImage && !mainImage.startsWith('http')) {
        const baseUrl = window.location.origin;
        mainImage = new URL(mainImage, baseUrl).href; // 合并相对路径和当前网页地址
    }

    console.log(mainImage);
    return mainImage;
}
