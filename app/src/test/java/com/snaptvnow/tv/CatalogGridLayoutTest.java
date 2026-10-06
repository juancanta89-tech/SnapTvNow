package com.snaptvnow.tv;

import static org.junit.Assert.*;
import org.junit.Test;

public class CatalogGridLayoutTest {
  @Test public void unfoldedPhoneUsesFourColumnsAtDifferentWindowWidths(){
    assertEquals(4,CatalogGridLayout.columns(false,600));assertEquals(4,CatalogGridLayout.columns(false,700));assertEquals(4,CatalogGridLayout.columns(false,900));
  }
  @Test public void televisionUsesFiveColumnsIndependentlyOfPhoneOrientation(){
    assertEquals(5,CatalogGridLayout.columns(true,960));assertEquals(5,CatalogGridLayout.columns(true,1280));
    assertEquals(4,CatalogGridLayout.columns(false,960));
  }
  @Test public void foldedPhoneRetainsReadableTwoColumnLayout(){assertEquals(2,CatalogGridLayout.columns(false,360));assertEquals(2,CatalogGridLayout.columns(false,599));}
  @Test public void compactCardsLeaveRoomForLogoAndTwoNameLines(){
    assertTrue(CatalogGridLayout.channelHeightDp(false,700)<158);
    assertTrue(CatalogGridLayout.channelHeightDp(true,960)>=112);
    assertTrue(CatalogGridLayout.posterHeightDp(false,700)>CatalogGridLayout.channelHeightDp(false,700));
  }
}
