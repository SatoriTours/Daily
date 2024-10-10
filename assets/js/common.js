function removeAllAdNode() {
  const allElements = document.querySelectorAll('*');

  allElements.forEach(node => {
    const regex = /(\bad\b|(?=.*open)(?=.*app))/i;
    if (regex.test(node.className)) {
      console.log('命中规则: 该节点的 class 包含 "ad"');
      node.remove();
      return;
    }
  });
}

function removeAdNode(node) {
  try {
    const regex = /(ad|popup|modal|geo|(?=.*open)(?=.*app))/i;
    if (regex.test(node.className)) {
      console.log('命中规则: 该节点的 class 包含 "ad"');
      node.remove();
      return;
    }

    if (node.nodeName === 'IFRAME' && regex.test(node.src)) {
      console.log('命中规则: 该节点是iframe', node.src);
      node.remove();
      return;
    }

    if (regex.test(node.id)) {
      console.log('命中规则: 该节点的 id 包含 "ad"');
      node.remove();
      return;
    }

    // 获取计算样式
    const computedStyle = getComputedStyle(node);
    const zIndex = computedStyle.zIndex;
    if (zIndex >= 1) {
      console.log('命中规则: 该节点的 zIndex > 1');
      node.remove();
      return;
    }
  } catch (error) { }

}

(function () {
  const observer = new MutationObserver(mutations => {
    mutations.forEach(mutation => {
      mutation.addedNodes.forEach(node => {
        if (node.nodeType === 1) { // 确保是元素节点
          removeAdNode(node);
        }
      });
    });
  });

  // 开始观察整个文档
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
})();

function testNode() {
  //  const ads = document.querySelectorAll('.ADEvent_tag_S3mUa');
  //  ads.forEach(ad => removeAdNode(ad));
  //  console.log("==================================", ads.length);
}
