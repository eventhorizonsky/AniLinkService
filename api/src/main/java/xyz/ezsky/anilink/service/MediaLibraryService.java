package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.vo.MediaLibraryVO;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.util.List;
import java.util.stream.Collectors;
import xyz.ezsky.anilink.model.vo.PathVO;

@Service
public class MediaLibraryService {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaScannerService mediaScannerService;

    public MediaLibraryVO addLibrary(MediaLibraryDTO mediaLibraryDTO) {
        MediaLibrary mediaLibrary = new MediaLibrary();
        BeanUtils.copyProperties(mediaLibraryDTO, mediaLibrary);
        MediaLibrary savedLibrary = mediaLibraryRepository.save(mediaLibrary);
        mediaScannerService.scanLibrary(savedLibrary);
        MediaLibraryVO mediaLibraryVO = new MediaLibraryVO();
        BeanUtils.copyProperties(savedLibrary, mediaLibraryVO);
        return mediaLibraryVO;
    }

    public List<MediaLibraryVO> findAll() {
        return mediaLibraryRepository.findAll().stream().map(mediaLibrary -> {
            MediaLibraryVO mediaLibraryVO = new MediaLibraryVO();
            BeanUtils.copyProperties(mediaLibrary, mediaLibraryVO);
            return mediaLibraryVO;
        }).collect(Collectors.toList());
    }

    public void deleteLibrary(Long id) {
        mediaScannerService.stopWatching(id);
        mediaLibraryRepository.deleteById(id);
    }

    public void scanLibrary(Long id) {
        mediaLibraryRepository.findById(id).ifPresent(mediaScannerService::scanLibrary);
    }

    public void scanAllLibraries() {
        mediaScannerService.scanAllLibraries();
    }


    /**
     * 查询指定目录下的所有文件和文件夹路径，带类型
     * @param rootPath 根目录路径
     * @return 路径及类型列表
     */
    public List<PathVO> listServerPaths(String rootPath) {
        java.io.File root = new java.io.File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return java.util.Collections.emptyList();
        }
        java.io.File[] files = root.listFiles();
        if (files == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(files)
                .map(f -> new PathVO(f.getName(), f.getAbsolutePath(), f.isDirectory() ? "directory" : "file"))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询指定目录下的所有文件夹路径，带类型
     * @param rootPath 根目录路径
     * @return 路径及类型列表
     */
    public List<PathVO> listServerPaths(String rootPath, boolean onlyDir, String fileType) {
        java.io.File root = new java.io.File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return java.util.Collections.emptyList();
        }
        java.io.File[] files = root.listFiles();
        if (files == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(files)
                .filter(f -> {
                    if (onlyDir) return f.isDirectory();
                    if (fileType != null && !fileType.isEmpty()) {
                        return f.isDirectory() || (f.isFile() && f.getName().toLowerCase().endsWith("." + fileType.toLowerCase()));
                    }
                    return true;
                })
                .map(f -> new PathVO(f.getName(), f.getAbsolutePath(), f.isDirectory() ? "directory" : "file"))
                .collect(java.util.stream.Collectors.toList());
    }
}
