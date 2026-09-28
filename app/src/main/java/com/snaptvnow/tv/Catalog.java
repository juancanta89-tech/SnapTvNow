package com.snaptvnow.tv;
import java.util.*;
public final class Catalog {
 public static final class Item { public final String id,title,category,url,description,artwork; public Item(String id,String title,String category,String url,String description){this(id,title,category,url,description,"");} public Item(String id,String title,String category,String url,String description,String artwork){this.id=id;this.title=title;this.category=category;this.url=url;this.description=description;this.artwork=artwork;} }
 // Public Google demonstration clip. Replace this source with your licensed media API.
 public static final String DEMO_VIDEO="https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
 public static List<Item> demo(){return Arrays.asList(
 new Item("live1","Canal demo 1","TV en vivo",DEMO_VIDEO,"Contenido de demostración"),
 new Item("live2","Canal demo 2","TV en vivo",DEMO_VIDEO,"Contenido de demostración"),
 new Item("sport1","Deportes demo","Deportes",DEMO_VIDEO,"Vista previa; sin eventos reales"),
 new Item("movie1","Película demo","Películas",DEMO_VIDEO,"Video de demostración"),
 new Item("series1","Serie demo · Episodio 1","Series",DEMO_VIDEO,"Video de demostración"));}
 private Catalog(){}
}
