package com.snaptvnow.tv;

import java.util.List;

/** A stored account is a credential source, never proof of a working connection. */
final class SessionRecovery {
  interface Connector { XtreamClient connect(XtreamClient saved,List<String> servers) throws Exception; }
  interface Writer { void save(XtreamClient verified) throws Exception; }
  static final class Result {
    final XtreamClient client;
    final String message;
    Result(XtreamClient client,String message){this.client=client;this.message=message;}
  }

  static Result reconnect(XtreamClient saved,List<String> servers,Connector connector,Writer writer){
    if(saved==null)return new Result(null,null);
    final XtreamClient verified;
    try{
      verified=connector.connect(saved,servers);
      if(verified==null)throw new IllegalStateException("Unverified connection");
    }catch(Exception unavailable){
      // Leave the encrypted account untouched, but do not expose stale access.
      return new Result(null,"No se pudo validar la cuenta guardada. Tus credenciales se conservan; pulsa Reconectar para volver a intentarlo.");
    }
    try{writer.save(verified);}
    catch(Exception storage){
      return new Result(verified,"La cuenta se verificó, pero no se pudieron guardar los datos actualizados. Puedes seguir usando el servicio y reintentar el guardado.");
    }
    return new Result(verified,null);
  }
}
