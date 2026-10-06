package com.snaptvnow.tv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One complete catalog per section and subscriber session; never a currently opened folder. */
final class CatalogSearch {
  enum Scope {
    MOVIES("Películas", "movie"), SERIES("Series", "series"), CHANNELS("TV en vivo", "live");
    final String section, kind;
    Scope(String section, String kind) { this.section=section; this.kind=kind; }
    static Scope forSection(String section, Scope fallback) {
      if ("Películas".equals(section)) return MOVIES;
      if ("Series".equals(section)) return SERIES;
      if ("TV en vivo".equals(section) || "PPV HOY".equals(section)) return CHANNELS;
      return fallback;
    }
    boolean accepts(Catalog.Item item) { return item.id.startsWith(kind); }
  }
  interface Source { List<Catalog.Item> load(Scope scope) throws Exception; }
  private final Source source;
  private final Map<Scope,List<Catalog.Item>> catalogs=new EnumMap<>(Scope.class);
  CatalogSearch(Source source) { this.source=source; }
  synchronized List<Catalog.Item> all(Scope scope) throws Exception {
    List<Catalog.Item> catalog=catalogs.get(scope);
    if(catalog==null) {
      List<Catalog.Item> loaded=source.load(scope);
      List<Catalog.Item> valid=new ArrayList<>();
      java.util.Set<String> ids=new java.util.HashSet<>();
      for(Catalog.Item item:loaded) if(scope.accepts(item) && ids.add(item.id)) valid.add(item);
      catalog=Collections.unmodifiableList(valid);
      catalogs.put(scope,catalog);
    }
    return catalog;
  }
  static List<Catalog.Item> filter(List<Catalog.Item> catalog, Scope scope, String query) {
    String normalized=normalize(query);
    if(normalized.isEmpty()) return Collections.emptyList();
    String[] words=normalized.split("\\s+");
    List<Catalog.Item> matches=new ArrayList<>();
    for(Catalog.Item item:catalog) {
      if(!scope.accepts(item)) continue;
      String title=normalize(item.title); boolean match=true;
      for(String word:words) if(!title.contains(word)) { match=false; break; }
      if(match) matches.add(item);
    }
    return matches;
  }
  private static String normalize(String text) {
    return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD)
        .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();
  }
}
