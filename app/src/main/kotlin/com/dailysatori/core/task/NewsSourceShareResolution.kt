package com.dailysatori.core.task

import com.dailysatori.service.remotenews.RemoteArticle

/**
 * Resolution of a shared source fetch. A merged call must reload the owner's result from the local
 * repository and clear its own loading state; a failed call keeps the caller's failure path; an owned
 * call already delivered its result inside the fetching block.
 */
internal suspend fun resolveSharedSourceArticles(
    share: NewsSourceFetchShare<*>,
    reload: suspend () -> List<RemoteArticle>,
    onLoaded: (List<RemoteArticle>) -> Unit,
    onFailure: (String) -> Unit,
) {
    when (share) {
        is NewsSourceFetchShare.Merged -> onLoaded(reload())
        is NewsSourceFetchShare.Failed -> onFailure("来源文章同步失败，请稍后重试")
        is NewsSourceFetchShare.Owned -> Unit
    }
}
