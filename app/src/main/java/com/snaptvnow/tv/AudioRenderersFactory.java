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
  if(channels<=6)return ChannelMixingMatrix.createForConstantPower(channels,2).scaleBy(.5f);
  // Matrix rows are output channels: all left coefficients, then all right coefficients.
  // 6.1 uses FL FR FC LFE BC SL SR; 7.1 uses FL FR FC LFE BL BR SL SR.
  float[] left=channels==7?new float[]{1,0,.7071f,.5f,.5f,.7071f,0}:new float[]{1,0,.7071f,.5f,.7071f,0,.7071f,0};
  float[] right=channels==7?new float[]{0,1,.7071f,.5f,.5f,0,.7071f}:new float[]{0,1,.7071f,.5f,0,.7071f,0,.7071f};
  float[] weights=new float[channels*2];System.arraycopy(left,0,weights,0,channels);System.arraycopy(right,0,weights,channels,channels);
  return new ChannelMixingMatrix(channels,2,weights).scaleBy(.5f);
 }
}
