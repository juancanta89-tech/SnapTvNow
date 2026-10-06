package com.snaptvnow.tv;

/** Shared sizing for the selected compact catalog design. */
final class CatalogGridLayout {
  static int columns(boolean television,int windowWidthDp) {
    return television?5:windowWidthDp>=600?4:2;
  }
  static int cardWidthDp(boolean television,int windowWidthDp) {
    int available=Math.max(180,windowWidthDp-(television?232:28));
    return Math.max(60,available/columns(television,windowWidthDp)-6);
  }
  static int channelHeightDp(boolean television,int windowWidthDp) {
    return Math.max(112,Math.min(156,Math.round(cardWidthDp(television,windowWidthDp)*.92f)));
  }
  static int posterHeightDp(boolean television,int windowWidthDp) {
    return Math.max(160,Math.min(224,Math.round(cardWidthDp(television,windowWidthDp)*1.38f)));
  }
  private CatalogGridLayout() {}
}
