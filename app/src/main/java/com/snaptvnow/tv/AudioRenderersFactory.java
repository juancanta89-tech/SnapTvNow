package com.snaptvnow.tv;

import android.content.Context;
import android.os.Handler;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import java.util.ArrayList;

/** Audio-only compatibility path. Video always keeps the platform renderer. */
final class AudioRenderersFactory extends DefaultRenderersFactory {
 static final String COMPATIBLE_KEY="live_compatible_audio";
 private final boolean compatible;
 AudioRenderersFactory(Context context,boolean compatible){
  super(context);this.compatible=compatible;
  setEnableDecoderFallback(true);
  // ON also covers MP2 in ordinary mode when the device has no platform decoder.
  setExtensionRendererMode(compatible?EXTENSION_RENDERER_MODE_PREFER:EXTENSION_RENDERER_MODE_ON);
 }
 @Override protected AudioSink buildAudioSink(Context context,boolean floatOutput,boolean playbackParameters){
  if(!compatible)return super.buildAudioSink(context,floatOutput,playbackParameters);
  return new DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
    .setAudioProcessors(new AudioProcessor[]{stereoMixer()}).build();
 }
 @Override protected void buildVideoRenderers(Context context,int mode,MediaCodecSelector selector,boolean fallback,Handler handler,VideoRendererEventListener listener,long joiningMs,ArrayList<Renderer> renderers){
  // Enabling an audio extension must never prefer the experimental software video renderer.
  super.buildVideoRenderers(context,EXTENSION_RENDERER_MODE_OFF,selector,fallback,handler,listener,joiningMs,renderers);
 }
 static ChannelMixingAudioProcessor stereoMixer(){
  ChannelMixingAudioProcessor mixer=new ChannelMixingAudioProcessor();
  for(int channels=1;channels<=8;channels++)mixer.putChannelMixingMatrix(stereoMatrix(channels));
  return mixer;
 }
 static ChannelMixingMatrix stereoMatrix(int channels){
  if(channels<=2)return ChannelMixingMatrix.createForConstantGain(channels,2);
  // getMixingCoefficient(input, output) indexes input * outputCount + output.
  float[] weights=new float[channels*2];weights[0]=.5f;weights[3]=.5f;
  if(channels==4){weights[4]=weights[7]=.3535534f;}
  else{
   weights[4]=weights[5]=.3535534f; // front-center dialogue to both speakers
   if(channels==5){weights[6]=weights[9]=.3535534f;}
   else if(channels>=6){
    weights[6]=weights[7]=.25f; // low-frequency channel
    if(channels==7){weights[8]=weights[9]=.25f;weights[10]=weights[13]=.3535534f;}
    else{weights[8]=weights[11]=.3535534f;if(channels==8)weights[12]=weights[15]=.3535534f;}
   }
  }
  return new ChannelMixingMatrix(channels,2,weights);
 }
}
