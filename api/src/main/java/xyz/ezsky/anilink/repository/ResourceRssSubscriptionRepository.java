package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.ResourceRssSubscription;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import java.util.List;

public interface ResourceRssSubscriptionRepository extends BaseRepository<ResourceRssSubscription, Long> {
    List<ResourceRssSubscription> findByEnabledTrueOrderByCreatedAtAsc();

    List<ResourceRssSubscription> findAllByOrderByCreatedAtDesc();

    long countByEnabledTrue();
}
