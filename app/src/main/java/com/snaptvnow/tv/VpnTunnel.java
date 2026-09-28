package com.snaptvnow.tv;

import android.content.Context;
import android.net.VpnService;
import java.io.StringReader;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;

/** Runs the bundled OpenVPN engine in Android's VpnService after system consent. */
final class VpnTunnel {
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
}
