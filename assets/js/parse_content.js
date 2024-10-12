function parseContent() {
    const documentClone = document.cloneNode(true);
    const article = new Readability(documentClone).parse();

    if (window.flutter_inappwebview == null) {
        console.log("flutter 组件没有加载成功")
        return;
    }

    let image = "";
    try {
        image= getPageImage();
    } catch (error) {
        console.log("获取图片失败", error);
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

function getPageImage() {
    let image = "";
    const supportedDomains = ['x.com', 'twitter.com', 'apps.apple.com', 'infoq.cn'];
    const hostDomain = window.location.hostname;
    console.log("网站域名是:", hostDomain);

    image = getMainImage();
    if (image == "") {
        image = getOgImage();
    }

    console.log("获取的图片地址是", image);
    return image;
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
    let imageSrc = '';
    let imageWidth = 0;
    let imageHeight = 0;
    // 一般来说选最大的那张图就是这个网页的代表
    for (let img of images) {
        if (img.src && img.src.endsWith('.gif')) {
            continue;
        }
        if (img.naturalWidth > 300 && img.naturalHeight > 300) {
            console.log("分析图片尺寸", img.naturalWidth, img.naturalHeight, img.src);
            if(imageSrc == '' || img.naturalWidth * img.naturalHeight > imageWidth * imageHeight) {
               imageSrc = img.src;
               imageWidth = img.naturalWidth;
               imageHeight = img.naturalHeight;
            }
        }
    }

    if (imageSrc && !imageSrc.startsWith('http')) {
        const baseUrl = window.location.origin;
        imageSrc = new URL(imageSrc, baseUrl).href; // 合并相对路径和当前网页地址
    }

    console.log(imageSrc);
    return imageSrc;
}
