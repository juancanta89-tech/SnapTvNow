package com.snaptvnow.tv;

import static org.junit.Assert.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AudioRenderersFactoryTest {
 @Test public void compatibleAudioNeverAddsASoftwareVideoRenderer(){
  ArrayList<Renderer> renderers=new ArrayList<>();
  new AudioRenderersFactory(RuntimeEnvironment.getApplication(),true).buildVideoRenderers(RuntimeEnvironment.getApplication(),DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,MediaCodecSelector.DEFAULT,true,new Handler(Looper.getMainLooper()),new VideoRendererEventListener(){},5000,renderers);
  assertEquals(1,renderers.size());assertTrue(renderers.get(0) instanceof MediaCodecVideoRenderer);
 }
 @Test public void stereoPcmIsNotChanged()throws Exception{
  ChannelMixingAudioProcessor mixer=AudioRenderersFactory.stereoMixer();
  assertEquals(AudioProcessor.AudioFormat.NOT_SET,mixer.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT)));
 }
 @Test public void monoIsAudibleOnBothSpeakers()throws Exception{
  ChannelMixingAudioProcessor mixer=AudioRenderersFactory.stereoMixer();
  AudioProcessor.AudioFormat output=mixer.configure(new AudioProcessor.AudioFormat(48000,1,C.ENCODING_PCM_16BIT));mixer.flush();
  ByteBuffer input=ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).putShort((short)12000);input.flip();mixer.queueInput(input);
  ByteBuffer pcm=mixer.getOutput().order(ByteOrder.nativeOrder());assertEquals(2,output.channelCount);assertEquals(12000,pcm.getShort());assertEquals(12000,pcm.getShort());
 }
 @Test public void surroundDialogueIsMixedIntoBothStereoSpeakers()throws Exception{
  ChannelMixingAudioProcessor mixer=AudioRenderersFactory.stereoMixer();
  AudioProcessor.AudioFormat output=mixer.configure(new AudioProcessor.AudioFormat(48000,6,C.ENCODING_PCM_16BIT));mixer.flush();
  ByteBuffer input=ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());for(int i=0;i<6;i++)input.putShort((short)(i==2?12000:0));input.flip();mixer.queueInput(input);
  ByteBuffer pcm=mixer.getOutput().order(ByteOrder.nativeOrder());assertEquals(2,output.channelCount);assertEquals(48000,output.sampleRate);
  short left=pcm.getShort(),right=pcm.getShort();assertTrue(left>3000);assertEquals(left,right);
 }
 @Test public void fullScaleSurroundDoesNotOverflowPcm()throws Exception{
  ChannelMixingAudioProcessor mixer=AudioRenderersFactory.stereoMixer();mixer.configure(new AudioProcessor.AudioFormat(48000,8,C.ENCODING_PCM_16BIT));mixer.flush();
  ByteBuffer input=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());for(int i=0;i<8;i++)input.putShort(Short.MAX_VALUE);input.flip();mixer.queueInput(input);
  ByteBuffer pcm=mixer.getOutput().order(ByteOrder.nativeOrder());assertTrue(pcm.getShort()>0);assertTrue(pcm.getShort()>0);
 }
 @Test public void fourAndFiveChannelBackLeftStaysOnTheLeft()throws Exception{
  for(int channels:new int[]{4,5}){
   ChannelMixingAudioProcessor mixer=AudioRenderersFactory.stereoMixer();mixer.configure(new AudioProcessor.AudioFormat(48000,channels,C.ENCODING_PCM_16BIT));mixer.flush();
   ByteBuffer input=ByteBuffer.allocateDirect(channels*2).order(ByteOrder.nativeOrder());int backLeft=channels==4?2:3;
   for(int i=0;i<channels;i++)input.putShort((short)(i==backLeft?12000:0));input.flip();mixer.queueInput(input);
   ByteBuffer pcm=mixer.getOutput().order(ByteOrder.nativeOrder());assertTrue(pcm.getShort()>3000);assertEquals(0,pcm.getShort());
  }
 }
}
