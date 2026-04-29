function parseContent() {
    const documentClone = document.cloneNode(true);
    let title = '';
    let excerpt = '';
    let htmlContent = '';
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
        htmlContent = article.content;
        textContent = article.textContent;
        publishedTime = article.publishedTime;

        // 尝试获取有 aria-labelledby 属性的 section 节点
        const sectionWithAriaLabel = document.querySelector('section[aria-labelledby]');
        if (sectionWithAriaLabel) {
            console.log("找到 aria-labelledby section 节点，应该是twitter的正文");
            // 如果找到了这个节点,使用它的内容覆盖原有内容
            htmlContent = sectionWithAriaLabel.innerHTML;
            textContent = sectionWithAriaLabel.textContent;
        }
    } catch (error) {
        console.log("Readability解析文章失败, 直接获取网页原始内容", error);
        title = document.title;
        htmlContent = document.documentElement.innerHTML;
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

    return {
        url: window.location.href,
        title: title,
        excerpt: excerpt,
        htmlContent: htmlContent,
        textContent: textContent,
        publishedTime: publishedTime,
        imageUrls: images
    }
}

function getPageImage() {
    const hostDomain = window.location.hostname;
    console.log("网站域名是:", hostDomain);

    let images = getMainImage();
    let ogImage = getOgImage();
    if (ogImage != '') {
        images.push(ogImage);
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
    if (ogImage.includes('logo')) {
        console.log("跳过包含 logo 的图片", ogImage);
        ogImage = '';
    }

    if (ogImage.includes('avatar')) {
        console.log("跳过包含 avatar 的图片", ogImage);
        ogImage = '';
    }

    if (ogImage.includes('icon')) {
        console.log("跳过包含 icon 的图片", ogImage);
        ogImage = '';
    }

    console.log("og:image:", ogImage);
    return ogImage;
}

// 检查图片是否应该被跳过
function shouldSkipImage(imgSrc) {
    if (!imgSrc) {
        return true;
    }
    if (imgSrc.endsWith('.gif')) {
        console.log("跳过gif图");
        return true;
    }
    if (imgSrc.startsWith("data:image/")) {
        return true; // 是 Base64 内容
    }
    return false;
}

// 检查图片尺寸是否符合要求
function isImageLargeEnough(img) {
    return img.naturalWidth > 300 || img.naturalHeight > 300;
}

// 检查图片 URL 是否包含不需要的关键词
function shouldSkipByKeyword(src) {
    const skipKeywords = ['logo', 'avatar'];
    for (const keyword of skipKeywords) {
        if (src.includes(keyword)) {
            console.log(`跳过包含 ${keyword} 的图片`, src);
            return true;
        }
    }
    return false;
}

// 优化 Twitter 图片 URL
function optimizeTwitterImageUrl(src) {
    if (src.match(/https:\/\/pbs\.twimg\.com\/media\/.*\?format=\w+&name=\w+/)) {
        return src.replace(/name=\w+/, 'name=large');
    }
    return src;
}

// 转换相对路径为绝对路径
function toAbsoluteUrl(src) {
    if (!src.startsWith('http')) {
        const baseUrl = window.location.origin;
        return new URL(src, baseUrl).href;
    }
    return src;
}

// 处理单张图片
function processImage(img) {
    if (shouldSkipImage(img.src)) {
        return null;
    }

    if (!isImageLargeEnough(img)) {
        return null;
    }

    console.log("分析图片尺寸", img.naturalWidth, img.naturalHeight, img.src);

    let src = img.src;

    if (shouldSkipByKeyword(src)) {
        return null;
    }

    src = optimizeTwitterImageUrl(src);
    src = toAbsoluteUrl(src);

    return src;
}

function getMainImage() {
    console.log("开始获取主图");
    const images = document.getElementsByTagName('img');
    let imagesSrc = [];

    // 一般来说选最大的那张图就是这个网页的代表
    for (let img of images) {
        const processedSrc = processImage(img);
        if (processedSrc) {
            imagesSrc.push(processedSrc);
        }
    }

    console.log("分析得到图片", imagesSrc);
    return imagesSrc;
}
