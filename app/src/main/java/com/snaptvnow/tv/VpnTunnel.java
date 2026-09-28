package com.snaptvnow.tv;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.IBinder;
import java.io.StringReader;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.ConnectionStatus;

/** Runs the bundled OpenVPN engine in Android's VpnService after system consent. */
final class VpnTunnel {
  private static volatile boolean active;
  private static volatile boolean listenerInstalled;
  static void initialize(){
    if(listenerInstalled)return;
    listenerInstalled=true;
    VpnStatus.addStateListener(new VpnStatus.StateListener(){
      @Override public void updateState(String state,String message,int resourceId,ConnectionStatus level,Intent intent){
        active=level==ConnectionStatus.LEVEL_CONNECTED;
      }
      @Override public void setConnectedVPN(String uuid){}
    });
  }
  static boolean isConnected(){return active;}
  static boolean needsConsent(Context context){return VpnService.prepare(context)!=null;}

  static void start(Context context,VpnLocations.Config config) throws Exception {
    if(needsConsent(context))throw new IllegalStateException("Android no autorizó la VPN");
    ConfigParser parser=new ConfigParser();
    parser.parseConfig(new StringReader(config.profile));
    VpnProfile profile=parser.convertProfile();
    if(profile.mAuthenticationType==VpnProfile.TYPE_KEYSTORE ||
        profile.mAuthenticationType==VpnProfile.TYPE_USERPASS_KEYSTORE ||
        profile.mAuthenticationType==VpnProfile.TYPE_EXTERNAL_APP)
      throw new IllegalArgumentException("El perfil VPN requiere un certificado no disponible");
    profile.mAuthenticationType=VpnProfile.TYPE_USERPASS;
    profile.mUsername=config.username;
    profile.mPassword=config.password;
    profile.mName="SNAPTVNOW VPN";
    profile.mPersistTun=true;
    profile.mBlockUnusedAddressFamilies=true;
    ProfileManager.setTemporaryProfile(context.getApplicationContext(),profile);
    VPNLaunchHelper.startOpenVpn(profile,context.getApplicationContext(),"SNAPTVNOW",false);
  }
  static boolean disconnect(Context context){
    Context app=context.getApplicationContext();
    Intent intent=new Intent(app,OpenVPNService.class);intent.setAction(OpenVPNService.START_SERVICE);
    ServiceConnection connection=new ServiceConnection(){
      @Override public void onServiceConnected(ComponentName name,IBinder binder){
        try{IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false);}
        catch(Exception ignored){}
        finally{try{app.unbindService(this);}catch(Exception ignored){}}
      }
      @Override public void onServiceDisconnected(ComponentName name){}
    };
    boolean bound=app.bindService(intent,connection,0);
    if(!bound)try{app.unbindService(connection);}catch(Exception ignored){}
    return bound;
  }
}
