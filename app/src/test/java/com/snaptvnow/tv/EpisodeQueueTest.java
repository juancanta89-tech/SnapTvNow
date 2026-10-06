package com.snaptvnow.tv;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class EpisodeQueueTest {
  static Catalog.Item episode(String id,String series,int season,int number){
    return new Catalog.Item("episode"+id,"Capítulo "+number,"Series","https://example.invalid/"+id+".mp4","","",series,"Una serie",season,number);
  }
  @Test public void advancesInSeasonOrderEvenWhenProviderOrderIsWrong(){
    Catalog.Item a=episode("1","series1",1,1),b=episode("2","series1",1,2),c=episode("3","series1",2,1);
    assertSame(b,EpisodeQueue.next(Arrays.asList(c,b,a),a));assertSame(c,EpisodeQueue.next(Arrays.asList(c,b,a),b));
  }
  @Test public void finalEpisodeDoesNotWrapToFirst(){
    Catalog.Item a=episode("1","series1",1,1),b=episode("2","series1",1,2);
    assertNull(EpisodeQueue.next(Arrays.asList(a,b),b));
  }
  @Test public void doesNotEnterAnotherSeries(){
    Catalog.Item a=episode("1","series1",1,1),other=episode("2","series2",1,2);
    assertNull(EpisodeQueue.next(Arrays.asList(a,other),a));
  }
  @Test public void skipsDuplicateCurrentIdAndUnplayableEpisodes(){
    Catalog.Item a=episode("1","series1",1,1),blank=new Catalog.Item("episode2","Vacío","Series","","","","series1","Serie",1,2),c=episode("3","series1",1,3);
    assertSame(c,EpisodeQueue.next(Arrays.asList(a,a,blank,c),a));
  }
  @Test public void missingCurrentEpisodeDoesNotGuessAnotherTitle(){
    assertNull(EpisodeQueue.next(Collections.singletonList(episode("2","series1",1,2)),episode("1","series1",1,1)));
  }
  @Test public void moviesAndChannelsCannotBecomeEpisodes(){
    Catalog.Item movie=new Catalog.Item("movie1","Película","Películas","https://example.invalid/1.mp4","");
    assertFalse(EpisodeQueue.isEpisode(movie));assertNull(EpisodeQueue.next(Collections.singletonList(episode("1","series1",1,1)),movie));
  }
}
