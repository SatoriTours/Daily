function parseContent() {
    const documentClone = document.cloneNode(true);
    let title = '';
    let excerpt = '';
    let content = '';
    let textContent = '';
    let publishedTime = '';

    try{
        // 避免如下的错误, 所以只能捕获异常来处理
        // [Satori] [D]  浏览器日志: This document requires 'TrustedScript' assignment. An HTMLScriptElement was directly modified and will not be executed.
        // [Satori] [D]  浏览器日志: This document requires 'TrustedScript' assignment. This script element was modified without use of TrustedScript assignment.
        // [Satori] [D]  浏览器日志: This document requires 'TrustedScript' assignment. An HTMLScriptElement was directly modified and will not be executed.
        // [Satori] [D]  浏览器日志: This document requires 'TrustedScript' assignment. This script element was modified without use of TrustedScript assignment.
        // [Satori] [D]  浏览器日志: This document requires 'TrustedHTML' assignment.
        const article = new Readability(documentClone).parse();
        console.log("使用 Readability 解析网页成功");
        title = article.title;
        excerpt = article.excerpt;
        content = article.content;
        textContent = article.textContent;
        publishedTime = article.publishedTime;
    } catch (error) {
        console.log("Readability解析文章失败, 直接获取网页原始内容", error);
        title = document.title;
        content = document.documentElement.innerHTML;
        textContent = document.body.textContent;
    }


    if (window.flutter_inappwebview == null) {
        console.log("flutter 组件没有加载成功")
        return;
    }

    let images = [];
    try {
        images = getPageImage();
    } catch (error) {
        console.log("获取图片失败", error);
    }


    window.flutter_inappwebview.callHandler(
        "getPageContent",
        window.location.href, // 获取当前网页的URL
        title,
        excerpt,
        content,
        textContent,
        publishedTime,
        images
    )
}

function getPageImage() {
    // const supportedDomains = ['x.com', 'twitter.com', 'apps.apple.com', 'infoq.cn'];
    const hostDomain = window.location.hostname;
    console.log("网站域名是:", hostDomain);

    let images = getMainImage();
    if (images.length <= 0) {
        images = [getOgImage()];
    }

    console.log("获取的图片地址是", images);
    return images;
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
    console.log("开始获取主图");
    const images = document.getElementsByTagName('img');
    let imagesSrc = [];
    // 一般来说选最大的那张图就是这个网页的代表
    for (let img of images) {
        if (!img.src) {
            continue; // 如果 img.src 为空，则跳过
        }
        if (img.src.endsWith('.gif')) {
            console.log("跳过gif图");
            continue;
        }

        if (img.src.startsWith("data:image/")) {
            continue; // 是 Base64 内容
        }

        if (img.naturalWidth > 300 || img.naturalHeight > 300) {
            console.log("分析图片尺寸", img.naturalWidth, img.naturalHeight, img.src);
            let src = img.src;

            if (src.includes('logo')) {
                console.log("跳过包含 logo 的图片", src);
                continue;
            }

            if (!src.startsWith('http')) {
                const baseUrl = window.location.origin;
                src = new URL(src, baseUrl).href; // 合并相对路径和当前网页地址
            }
            imagesSrc = imagesSrc.concat(src);
        }
    }

    console.log("分析得到图片", imagesSrc);
    return imagesSrc;
}
