package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends BaseRepository<MediaFile, Long> {
    List<MediaFile> findByLibraryId(Long libraryId);
    Optional<MediaFile> findByFilePath(String filePath);
}
