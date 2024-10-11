function parseContent() {
    const documentClone = document.cloneNode(true);
    const article = new Readability(documentClone).parse();

    if (window.flutter_inappwebview == null) {
        console.log("flutter 组件没有加载成功")
        return;
    }

    let image;
    const supportedDomains = ['x.com', 'twitter.com'];
    console.log("网站域名是:", window.location.hostname);

    if (supportedDomains.some(domain => window.location.hostname.includes(domain))) {
        image = getMainImage();
        if (image == '') {
            image = getOgImage();
        }
    } else {
        image = getOgImage();
        if (image == '') {
            image = getMainImage();
        }
    }

    console.log("获取的图片地址是", image);

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
        console.log("分析图片尺寸", img.naturalWidth, img.naturalHeight);
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
