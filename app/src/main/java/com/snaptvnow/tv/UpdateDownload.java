package com.snaptvnow.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

/** A bounded HTTPS download. Redirects never inherit app/subscriber credentials. */
final class UpdateDownload {
  static final long MAX_BYTES=200L*1024*1024;
  interface Connections { HttpURLConnection open(URL url) throws IOException; }
  interface Progress { void changed(long bytes,long total); }
  private final Connections connections;
  private volatile boolean cancelled;
  private volatile HttpURLConnection active;

  UpdateDownload(){this(url->(HttpURLConnection)url.openConnection());}
  UpdateDownload(Connections connections){this.connections=connections;}

  static URL httpsUrl(String value) throws IOException {
    URL url=new URL(value);
    if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()
        ||url.getUserInfo()!=null||url.getRef()!=null)
      throw new IOException("El enlace de actualización debe ser HTTPS.");
    return url;
  }
  static URL apkUrl(String value) throws IOException {
    URL url=httpsUrl(value);
    if(!url.getPath().toLowerCase(Locale.ROOT).endsWith(".apk"))
      throw new IOException("El enlace de actualización no es una APK.");
    return url;
  }
  void cancel(){cancelled=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
  private void checkCancelled() throws InterruptedIOException {
    if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Descarga cancelada.");
  }

  File download(String link,File destination,Progress progress) throws IOException {
    URL url=apkUrl(link);
    File directory=destination.getParentFile();
    if(directory==null||(!directory.isDirectory()&&!directory.mkdirs()))
      throw new IOException("No se pudo preparar la descarga.");
    File partial=new File(directory,UUID.randomUUID()+".part");
    try {
      for(int redirects=0;redirects<=5;redirects++){
        checkCancelled();
        HttpURLConnection connection=connections.open(url);active=connection;
        try {
          checkCancelled();
          connection.setInstanceFollowRedirects(false);
          connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
          connection.setRequestProperty("User-Agent","SNAPTVNOW-Updater");
          connection.setRequestProperty("Accept-Encoding","identity");
          int status=connection.getResponseCode();
          if(status==301||status==302||status==303||status==307||status==308){
            String location=connection.getHeaderField("Location");
            if(location==null||redirects==5)throw new IOException("El servidor no pudo completar la descarga.");
            url=httpsUrl(new URL(url,location).toString());continue;
          }
          if(status!=200)throw new IOException("El servidor no pudo descargar la actualización. Reintenta.");
          long total=contentLength(connection.getHeaderField("Content-Length"));
          if(total>MAX_BYTES)throw new IOException("La actualización supera el tamaño permitido.");
          if(total>0&&directory.getUsableSpace()<total+5L*1024*1024)
            throw new IOException("No hay espacio suficiente para descargar la actualización.");
          long bytes=0;
          try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial)){
            byte[] buffer=new byte[64*1024];int count;
            progress.changed(0,total);
            while((count=input.read(buffer))!=-1){
              checkCancelled();bytes+=count;
              if(bytes>MAX_BYTES)throw new IOException("La actualización supera el tamaño permitido.");
              output.write(buffer,0,count);progress.changed(bytes,total);
            }
            checkCancelled();
            if(bytes==0||(total>=0&&bytes!=total))throw new IOException("La descarga quedó incompleta. Reintenta.");
            output.getFD().sync();
          }
          checkCancelled();
          if(!partial.renameTo(destination))throw new IOException("No se pudo guardar la actualización.");
          return destination;
        }finally{active=null;connection.disconnect();}
      }
      throw new IOException("El servidor no pudo completar la descarga.");
    }finally{partial.delete();}
  }
  private static long contentLength(String header){
    try{return header==null?-1:Long.parseLong(header);}catch(NumberFormatException ignored){return -1;}
  }
}
