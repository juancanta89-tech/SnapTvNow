package com.snaptvnow.tv;

import android.content.Context;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

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
 static ChannelMixingAudioProcessor stereoMixer(){
  ChannelMixingAudioProcessor mixer=new ChannelMixingAudioProcessor();
  for(int channels=1;channels<=8;channels++)mixer.putChannelMixingMatrix(stereoMatrix(channels));
  return mixer;
 }
 static ChannelMixingMatrix stereoMatrix(int channels){
  if(channels<=2)return ChannelMixingMatrix.createForConstantGain(channels,2);
  // FL, FR, FC, LFE, BL, BR, SL, SR. Leave headroom when summing surround voices.
  float[] weights=new float[channels*2];weights[0]=.5f;weights[3]=.5f;
  weights[4]=weights[5]=.3535534f;
  if(channels>3)weights[6]=weights[7]=.25f;
  if(channels==7){weights[8]=weights[9]=.25f;weights[10]=weights[13]=.3535534f;}
  else for(int input=4;input<channels;input++)weights[input*2+(input%2)]=.3535534f;
  return new ChannelMixingMatrix(channels,2,weights);
 }
}
