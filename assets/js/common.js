
// 网页删除 header, 这样截图的时候不会重复这些内容
function removeHeaderNode() {
    console.log("removeAllAdNode");
    const headers = document.querySelectorAll('body header');
    headers.forEach(node => node.remove());
}

function removeObstructiveNodes() {
    console.log("开始删除妨碍截图的节点");

    // 删除 header 节点
    removeHeaderNode();

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
    const openNodes = document.querySelectorAll('[class*="open"], [class*="app"]');
    openNodes.forEach(node => {
        console.log('命中规则: 该节点含有 "open" 或 "app" class');
        node.remove();
    });
}


function testNode() {
  //  const ads = document.querySelectorAll('.ADEvent_tag_S3mUa');
  //  ads.forEach(ad => removeAdNode(ad));
  //  console.log("==================================", ads.length);
}
