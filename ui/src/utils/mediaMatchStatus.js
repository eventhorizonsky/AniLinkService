// 媒体文件匹配状态元信息。
// 此前 AnimeLibrary.vue 与 VideoFileManager.vue 各自实现了相同函数，统一收敛到这里。

export const getMatchStatusMeta = (status) => {
  if (status === 'MATCHED') {
    return { color: 'success', text: '已匹配' }
  }
  if (status === 'NO_MATCH_FOUND') {
    return { color: 'warning', text: '无匹配' }
  }
  return { color: 'grey', text: '未匹配' }
}
