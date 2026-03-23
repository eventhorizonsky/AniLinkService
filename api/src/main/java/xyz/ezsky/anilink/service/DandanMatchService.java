package xyz.ezsky.anilink.service;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.AnimeInfo;
import xyz.ezsky.anilink.model.dto.MatchResult;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.util.DandanClientUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DandanMatchService {

    private final DandanClientUtil client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AnimeRepository animeRepository;
    private final SiteConfigService siteConfigService;

    public DandanMatchService(
            DandanClientUtil client,
            AnimeRepository animeRepository,
            SiteConfigService siteConfigService
    ) {
        this.client = client;
        this.animeRepository = animeRepository;
        this.siteConfigService = siteConfigService;
    }

    /**
     * 使用 /api/v2/match 接口尝试根据文件信息匹配动漫信息。
     * 返回第一个可用的匹配信息，若未匹配返回 null。
     * 
     * @deprecated 建议使用 batchMatch 方法进行批量匹配
     */
    @Deprecated
    public AnimeInfo queryByFile(String fileName, String hash, Long fileSize) {
        try {
            Map<String, Object> item = new HashMap<>();
            if (fileName != null) item.put("fileName", fileName);
            if (hash != null) item.put("fileHash", hash);
            if (fileSize != null) item.put("fileSize", fileSize);
            item.put("matchMode","hashAndFileName");
            log.debug("Querying Dandan with: {}", item);

            ResponseEntity<String> resp;
            try {
                resp = client.post(siteConfigService.getDandanBaseUrl(), "/api/v2/match", item);
            } catch (Exception e) {
                log.error("Dandan match call failed", e);
                return null;
            }
            log.debug("Dandan match response: {} - {}", resp.getStatusCode(), resp.getBody());
            if (!resp.getStatusCode().is2xxSuccessful()) return null;

            String json = resp.getBody();
            if (json == null || json.isEmpty()) return null;

            JsonNode root = objectMapper.readTree(json);

            // 递归查找包含动漫字段的节点
            AnimeInfo info = findAnimeInfo(root);

            // 仅创建基本的 Anime 记录，不再调用详情接口
            if (info != null && info.getAnimeId() != null) {
                // queryByFile 返回的是 AnimeInfo，不包含 type 等字段，只保存基本信息
                try {
                    Optional<Anime> existing = animeRepository.findByAnimeId(info.getAnimeId());
                    if (existing.isEmpty()) {
                        Anime anime = new Anime();
                        anime.setAnimeId(info.getAnimeId());
                        anime.setTitle(info.getAnimeTitle() != null ? info.getAnimeTitle() : "未知动漫");
                        animeRepository.save(anime);
                        log.info("Created basic anime record for animeId: {}", info.getAnimeId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to save anime record", e);
                }
            }

            return info;
        } catch (Exception e) {
            log.error("Unexpected error querying Dandan for fileName={} hash={} size={}", fileName, hash, fileSize, e);
            return null;
        }
    }

    /**
     * 调用 /api/v2/match 返回原始结果，用于前端展示候选并手动选择。
     */
    public String matchRawByFile(String fileName, String hash, Long fileSize) {
        try {
            Map<String, Object> item = new HashMap<>();
            if (fileName != null) item.put("fileName", fileName);
            if (hash != null) item.put("fileHash", hash);
            if (fileSize != null) item.put("fileSize", fileSize);
            item.put("matchMode", "hashAndFileName");

            ResponseEntity<String> resp = client.post(siteConfigService.getDandanBaseUrl(), "/api/v2/match", item);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return null;
            }

            String body = resp.getBody();
            if (body == null || body.isEmpty()) {
                return null;
            }
            return body;
        } catch (Exception e) {
            log.error("Dandan raw match call failed for fileName={}", fileName, e);
            return null;
        }
    }

    private AnimeInfo findAnimeInfo(JsonNode node) {
        if (node == null) return null;

        if (node.isObject()) {
            JsonNode epId = node.get("episodeId");
            JsonNode anId = node.get("animeId");
            JsonNode anTitle = node.get("animeTitle");
            JsonNode epTitle = node.get("episodeTitle");

            if ((epId != null && !epId.isNull()) || (anId != null && !anId.isNull())
                    || (anTitle != null && !anTitle.isNull()) || (epTitle != null && !epTitle.isNull())) {
                AnimeInfo info = new AnimeInfo();
                if (epId != null && !epId.isNull()) info.setEpisodeId(epId.asText(null));
                if (anId != null && !anId.isNull()) {
                    try {
                        info.setAnimeId(anId.isNumber() ? anId.longValue() : Long.parseLong(anId.asText()));
                    } catch (Exception ignored) {}
                }
                if (anTitle != null && !anTitle.isNull()) info.setAnimeTitle(anTitle.asText(null));
                if (epTitle != null && !epTitle.isNull()) info.setEpisodeTitle(epTitle.asText(null));
                return info;
            }
        }

        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                AnimeInfo found = findAnimeInfo(child);
                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * 批量匹配文件到弹弹动画库
     * 使用 /api/v2/match/batch 接口进行批量匹配
     * 
     * @param items 文件信息列表，每个item包含fileName、fileHash、fileSize
     * @return 匹配结果列表，与输入顺序一一对应
     */
    public List<MatchResult> batchMatch(List<Map<String, Object>> items) {
        List<MatchResult> results = new ArrayList<>();
        
        if (items == null || items.isEmpty()) {
            return results;
        }

        try {
            // 构造批量匹配请求
            Map<String, Object> request = new HashMap<>();
            List<Map<String, Object>> fileList = new ArrayList<>();
            
            for (Map<String, Object> item : items) {
                Map<String, Object> fileItem = new HashMap<>();
                if (item.containsKey("fileName") && item.get("fileName") != null) {
                    fileItem.put("fileName", item.get("fileName"));
                }
                if (item.containsKey("fileHash") && item.get("fileHash") != null) {
                    fileItem.put("fileHash", item.get("fileHash"));
                }
                if (item.containsKey("fileSize") && item.get("fileSize") != null) {
                    fileItem.put("fileSize", item.get("fileSize"));
                }
                fileItem.put("matchMode", "hashAndFileName");
                fileList.add(fileItem);
            }
            
            request.put("requests", fileList);
            
            log.debug("Batch matching {} files with dandan api", items.size());
            
            ResponseEntity<String> resp;
            try {
                resp = client.post(siteConfigService.getDandanBaseUrl(), "/api/v2/match/batch", request);
            } catch (Exception e) {
                log.error("Dandan batch match call failed", e);
                // 返回与输入等量的失败结果
                for (int i = 0; i < items.size(); i++) {
                    MatchResult result = new MatchResult();
                    result.setSuccess(false);
                    result.setErrorMessage("API调用失败");
                    results.add(result);
                }
                return results;
            }

            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("Dandan batch match returned status: {}", resp.getStatusCode());
                // 返回与输入等量的失败结果
                for (int i = 0; i < items.size(); i++) {
                    MatchResult result = new MatchResult();
                    result.setSuccess(false);
                    result.setErrorMessage("API返回错误状态: " + resp.getStatusCode());
                    results.add(result);
                }
                return results;
            }

            String json = resp.getBody();
            if (json == null || json.isEmpty()) {
                log.warn("Dandan batch match returned empty response");
                // 返回与输入等量的失败结果
                for (int i = 0; i < items.size(); i++) {
                    MatchResult result = new MatchResult();
                    result.setSuccess(false);
                    result.setErrorMessage("API返回空响应");
                    results.add(result);
                }
                return results;
            }

            JsonNode root = objectMapper.readTree(json);
            
            // 检查顶层返回是否成功
            JsonNode topSuccess = root.get("success");
            if (topSuccess != null && !topSuccess.asBoolean(false)) {
                String errorMsg = root.get("errorMessage") != null ? root.get("errorMessage").asText() : "API返回失败";
                log.warn("Dandan batch match API returned failure: {}", errorMsg);
                
                // 打印完整的请求入参以便调试
                try {
                    String requestJson = objectMapper.writeValueAsString(request);
                    log.error("弹弹批量匹配API失败 - 错误: {}, 请求参数: {}", errorMsg, requestJson);
                } catch (Exception e) {
                    log.error("Failed to serialize request for logging", e);
                }
                
                // 返回与输入等量的失败结果
                for (int i = 0; i < items.size(); i++) {
                    MatchResult result = new MatchResult();
                    result.setSuccess(false);
                    result.setErrorMessage(errorMsg);
                    results.add(result);
                }
                return results;
            }
            
            // 提取results数组
            JsonNode resultsArray = root.get("results");
            if (resultsArray != null && resultsArray.isArray()) {
                for (JsonNode resultNode : resultsArray) {
                    MatchResult matchResult = parseMatchResult(resultNode);
                    results.add(matchResult);
                }
            } else {
                log.warn("Dandan batch match response does not contain results array");
                // 返回与输入等量的失败结果
                for (int i = 0; i < items.size(); i++) {
                    MatchResult result = new MatchResult();
                    result.setSuccess(false);
                    result.setErrorMessage("响应格式无效");
                    results.add(result);
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error in batch match", e);
            // 返回与输入等量的失败结果
            for (int i = 0; i < items.size(); i++) {
                MatchResult result = new MatchResult();
                result.setSuccess(false);
                result.setErrorMessage("处理异常: " + e.getMessage());
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 解析单个匹配结果节点
     * 结构: { success: true, fileHash: "...", matchResult: { episodeId, animeId, ... } }
     */
    private MatchResult parseMatchResult(JsonNode node) {
        MatchResult result = new MatchResult();
        
        try {
            // 读取外层字段
            JsonNode successNode = node.get("success");
            if (successNode != null) {
                result.setSuccess(successNode.asBoolean(false));
            }

            JsonNode fileHashNode = node.get("fileHash");
            if (fileHashNode != null && !fileHashNode.isNull()) {
                result.setFileHash(fileHashNode.asText(null));
            }

            // 如果匹配成功，从matchResult中提取字段
            if (result.getSuccess()) {
                JsonNode matchResultNode = node.get("matchResult");
                if (matchResultNode != null && !matchResultNode.isNull()) {
                    // 提取episodeId
                    JsonNode episodeIdNode = matchResultNode.get("episodeId");
                    if (episodeIdNode != null && !episodeIdNode.isNull()) {
                        result.setEpisodeId(episodeIdNode.asText(null));
                    }

                    // 提取animeId
                    JsonNode animeIdNode = matchResultNode.get("animeId");
                    if (animeIdNode != null && !animeIdNode.isNull()) {
                        try {
                            result.setAnimeId(animeIdNode.isNumber() ? 
                                animeIdNode.longValue() : Long.parseLong(animeIdNode.asText()));
                        } catch (Exception ignored) {}
                    }

                    // 提取animeTitle
                    JsonNode animeTitleNode = matchResultNode.get("animeTitle");
                    if (animeTitleNode != null && !animeTitleNode.isNull()) {
                        result.setAnimeTitle(animeTitleNode.asText(null));
                    }

                    // 提取episodeTitle
                    JsonNode episodeTitleNode = matchResultNode.get("episodeTitle");
                    if (episodeTitleNode != null && !episodeTitleNode.isNull()) {
                        result.setEpisodeTitle(episodeTitleNode.asText(null));
                    }

                    // 提取type
                    JsonNode typeNode = matchResultNode.get("type");
                    if (typeNode != null && !typeNode.isNull()) {
                        result.setType(typeNode.asText(null));
                    }

                    // 提取typeDescription
                    JsonNode typeDescNode = matchResultNode.get("typeDescription");
                    if (typeDescNode != null && !typeDescNode.isNull()) {
                        result.setTypeDescription(typeDescNode.asText(null));
                    }

                    // 提取shift
                    JsonNode shiftNode = matchResultNode.get("shift");
                    if (shiftNode != null && !shiftNode.isNull()) {
                        try {
                            result.setShift(shiftNode.asInt(0));
                        } catch (Exception ignored) {}
                    }

                    // 提取imageUrl
                    JsonNode imageUrlNode = matchResultNode.get("imageUrl");
                    if (imageUrlNode != null && !imageUrlNode.isNull()) {
                        result.setImageUrl(imageUrlNode.asText(null));
                    }
                    
                    // 如果匹配成功，确保 Anime 记录存在
                    if (result.getAnimeId() != null) {
                        ensureAnimeExists(result);
                    }
                } else {
                    result.setSuccess(false);
                    result.setErrorMessage("matchResult字段缺失");
                }
            } else {
                // 匹配失败，尝试提取错误信息
                JsonNode errorNode = node.get("errorMessage");
                if (errorNode != null && !errorNode.isNull()) {
                    result.setErrorMessage(errorNode.asText(null));
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing match result node", e);
            result.setSuccess(false);
            result.setErrorMessage("解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 确保 Anime 记录存在于数据库中
     * 使用匹配结果返回的信息保存Anime表
     * 
     * @param matchResult 匹配结果，包含Anime表需要的所有字段
     */
    private void ensureAnimeExists(MatchResult matchResult) {
        try {
            Long animeId = matchResult.getAnimeId();
            if (animeId == null) {
                return;
            }

            Optional<Anime> existing = animeRepository.findByAnimeId(animeId);
            
            if (existing.isEmpty()) {
                // 创建新的 Anime 记录，使用matchResult返回的所有字段
                Anime anime = new Anime();
                anime.setAnimeId(animeId);
                anime.setTitle(matchResult.getAnimeTitle() != null ? matchResult.getAnimeTitle() : "未知动漫");
                anime.setImageUrl(matchResult.getImageUrl());
                anime.setType(matchResult.getType());
                
                try {
                    animeRepository.save(anime);
                    log.info("Created anime record - animeId: {}, title: {}, type: {}, imageUrl: {}", 
                        animeId, matchResult.getAnimeTitle(), matchResult.getType(), matchResult.getImageUrl());
                } catch (Exception e) {
                    log.warn("Failed to save anime record for animeId: {}", animeId, e);
                }
            }
        } catch (Exception e) {
            log.error("Error ensuring anime exists for animeId: {}", matchResult.getAnimeId(), e);
        }
    }

    /**
     * 创建文件信息map，用于批量匹配请求
     */
    public static Map<String, Object> createFileInfo(String fileName, String fileHash, Long fileSize) {
        Map<String, Object> item = new HashMap<>();
        if (fileName != null && !fileName.isEmpty()) {
            item.put("fileName", fileName);
        }
        if (fileHash != null && !fileHash.isEmpty()) {
            item.put("fileHash", fileHash);
        }
        if (fileSize != null && fileSize > 0) {
            item.put("fileSize", fileSize);
        }
        return item;
    }
}
