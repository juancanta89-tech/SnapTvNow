package com.snaptvnow.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Next playable episode in this series, including the following season. Never wraps. */
final class EpisodeQueue {
  static boolean isEpisode(Catalog.Item item) {
    return item != null && item.id.startsWith("episode") && !item.seriesId.isEmpty();
  }
  static final Comparator<Catalog.Item> ORDER = (a, b) -> {
    int season = Integer.compare(a.seasonNumber, b.seasonNumber);
    return season != 0 ? season : Integer.compare(a.episodeNumber, b.episodeNumber);
  };
  static Catalog.Item next(List<Catalog.Item> episodes, Catalog.Item current) {
    if (!isEpisode(current)) return null;
    List<Catalog.Item> ordered = new ArrayList<>();
    for (Catalog.Item item : episodes)
      if (isEpisode(item) && item.seriesId.equals(current.seriesId)) ordered.add(item);
    Collections.sort(ordered, ORDER);
    int index = -1;
    for (int i = 0; i < ordered.size(); i++)
      if (ordered.get(i).id.equals(current.id)) { index = i; break; }
    if (index < 0) return null;
    for (int i = index + 1; i < ordered.size(); i++) {
      Catalog.Item candidate = ordered.get(i);
      if (!candidate.id.equals(current.id) && candidate.url != null && !candidate.url.isEmpty())
        return candidate;
    }
    return null;
  }
  private EpisodeQueue() {}
}
