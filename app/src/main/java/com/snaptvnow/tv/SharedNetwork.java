package com.snaptvnow.tv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** One connection pool for playback, login, catalog and profile. No global credentials. */
final class SharedNetwork {
  private static final OkHttpClient BASE=new OkHttpClient.Builder()
      .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
      .retryOnConnectionFailure(true).followSslRedirects(false).build();
  static OkHttpClient media(int readMs){return BASE.newBuilder().readTimeout(readMs,TimeUnit.MILLISECONDS).build();}
  static Response execute(Request request,int connectMs,int readMs)throws java.io.IOException {
    return BASE.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(connectMs,TimeUnit.MILLISECONDS).readTimeout(readMs,TimeUnit.MILLISECONDS)
        .callTimeout(connectMs+readMs,TimeUnit.MILLISECONDS).build().newCall(request).execute();
  }
  static String text(Response response,int limit)throws java.io.IOException {
    if(response.body()==null)throw new java.io.IOException("empty_response");
    if(response.body().contentLength()>limit)throw new java.io.IOException("response_too_large");
    try(InputStream in=response.body().byteStream()){
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
      while((n=in.read(buffer))!=-1){if(n>limit-out.size())throw new java.io.IOException("response_too_large");out.write(buffer,0,n);}
      return out.toString("UTF-8");
    }
  }
  private SharedNetwork(){}
}
