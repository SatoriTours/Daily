function initPage() {
  removeObstructiveNodes();
  adjustPageHeight();
}

function removeObstructiveNodes() {
  console.log("开始删除妨碍截图的节点");

  // 删除 header 节点
  const headers = document.querySelectorAll('body header');
  headers.forEach(node => node.remove());

  // 删除 z-index 大于 1 的节点
  const allNodes = document.querySelectorAll('*');
  allNodes.forEach(node => {
    const computedStyle = getComputedStyle(node);
    const zIndex = computedStyle.zIndex;
    if (zIndex > 1) {
      console.log('命中规则: 该节点的 zIndex > 1');
      node.remove();
    }
  });

  // 删除含有 open 和 app class 属性的节点
  const openNodes = document.querySelectorAll('[class*="open"][class*="app"]');
  openNodes.forEach(node => {
    console.log('命中规则: 该节点含有 "open" 或 "app" class');
    node.remove();
  });
}


// 解决body下面有一个 position 布局的元素比 body高, 导致网页高度识别不正确, 而且不能滑动的问题.
// 例如 https://manual.nssurge.com/overview/configuration.html
function adjustPageHeight() {
  const body = document.body;
  const staticElements = body.querySelectorAll('*');

  let maxHeight = 0;

  staticElements.forEach(element => {
    const computedStyle = getComputedStyle(element);
    if (computedStyle.position === 'static') {
      const elementHeight = element.offsetHeight;
      maxHeight = Math.max(maxHeight, elementHeight);
    }
  });

  body.style.height = `${maxHeight}px`;
  console.log(`页面高度已调整为: ${maxHeight}px`);
}


window.onload = function () {
  try {
    if (translate != null) {
      translate.service.use('client.edge');
      translate.selectLanguageTag.show = true;
    } else {
      console.log("加载 translate 失败");
    }
  } catch (error) {
    console.log("初始化 translate 失败", error);
  }

};


function testNode() {
  //  const ads = document.querySelectorAll('.ADEvent_tag_S3mUa');
  //  ads.forEach(ad => removeAdNode(ad));
  //  console.log("==================================", ads.length);
}
