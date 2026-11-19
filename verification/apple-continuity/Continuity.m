#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <QuartzCore/QuartzCore.h>

@interface Continuity : UIResponder <UIApplicationDelegate>
@property(nonatomic,strong) UIWindow *window;
@property(nonatomic,strong) AVPlayer *player;
@property(nonatomic,strong) AVPlayerLayer *layer;
@property(nonatomic,strong) AVPlayerItemVideoOutput *output;
@property(nonatomic,strong) NSMutableIndexSet *buckets;
@property(nonatomic,strong) NSTimer *timer;
@property(nonatomic,copy) NSString *sample;
@property(nonatomic) double start, first, last, maxGap;
@property(nonatomic) NSInteger frames;
@end
@implementation Continuity
- (BOOL)application:(UIApplication *)app didFinishLaunchingWithOptions:(NSDictionary *)options {
  self.window=[[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
  self.window.rootViewController=[UIViewController new]; [self.window makeKeyAndVisible];
  self.sample=NSProcessInfo.processInfo.environment[@"SAMPLE"];
  NSString *port=NSProcessInfo.processInfo.environment[@"PORT"];
  if(![@[@"real",@"synthetic"] containsObject:self.sample] || !port.intValue) return YES;
  NSURL *url=[NSURL URLWithString:[NSString stringWithFormat:@"http://localhost:%d/%@/index.m3u8",port.intValue,self.sample]];
  AVPlayerItem *item=[AVPlayerItem playerItemWithURL:url];
  self.output=[[AVPlayerItemVideoOutput alloc] initWithPixelBufferAttributes:@{(NSString *)kCVPixelBufferPixelFormatTypeKey:@(kCVPixelFormatType_32BGRA)}];
  [item addOutput:self.output];
  self.player=[AVPlayer playerWithPlayerItem:item];
  self.layer=[AVPlayerLayer playerLayerWithPlayer:self.player]; self.layer.frame=self.window.bounds;
  [self.window.rootViewController.view.layer addSublayer:self.layer];
  self.buckets=[NSMutableIndexSet indexSet];self.start=CACurrentMediaTime();self.first=NAN;self.last=NAN;
  [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:nil];
  [[AVAudioSession sharedInstance] setActive:YES error:nil];[self.player play];
  self.timer=[NSTimer scheduledTimerWithTimeInterval:0.125 target:self selector:@selector(tick:) userInfo:nil repeats:YES];
  return YES;
}
- (void)tick:(NSTimer *)timer {
  AVPlayerItem *item=self.player.currentItem;
  CMTime t=[self.output itemTimeForHostTime:CACurrentMediaTime()], displayed=kCMTimeInvalid;
  if(CMTIME_IS_NUMERIC(t) && [self.output hasNewPixelBufferForItemTime:t]){
    CVPixelBufferRef pixel=[self.output copyPixelBufferForItemTime:t itemTimeForDisplay:&displayed];
    if(pixel){CVPixelBufferRelease(pixel);double seconds=CMTimeGetSeconds(displayed);
      if(isfinite(seconds) && (!isfinite(self.last)||seconds>self.last)){
        if(!isfinite(self.first))self.first=seconds;
        if(isfinite(self.last))self.maxGap=MAX(self.maxGap,seconds-self.last);
        self.last=seconds;self.frames++;
        [self.buckets addIndex:(NSUInteger)((seconds-self.first)/4.0)];
      }
    }
  }
  double span=isfinite(self.first)?self.last-self.first:0;
  if(span<20.5 && CACurrentMediaTime()-self.start<62 && item.status!=AVPlayerItemStatusFailed)return;
  [timer invalidate];NSInteger audio=0;for(AVPlayerItemTrack *track in item.tracks)
    if(track.isEnabled && [track.assetTrack.mediaType isEqualToString:AVMediaTypeAudio])audio++;
  BOOL ok=span>=20 && self.frames>=30 && self.buckets.count>=6 && self.maxGap<3 && audio>0;
  NSDictionary *report=@{@"case":self.sample,@"ios":UIDevice.currentDevice.systemVersion,
    @"rendered_frames":@(self.frames),@"rendered_span_ms":@(lround(span*1000)),
    @"largest_frame_gap_ms":@(lround(self.maxGap*1000)),@"four_second_windows":@(self.buckets.count),
    @"enabled_audio_tracks":@(audio),@"error_code":@(item.error.code),@"ok":@(ok)};
  NSURL *dir=[NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
  [[NSJSONSerialization dataWithJSONObject:report options:0 error:nil] writeToURL:[dir URLByAppendingPathComponent:@"continuity.json"] atomically:YES];
  [self.player pause];
}
@end
int main(int argc,char *argv[]){@autoreleasepool{return UIApplicationMain(argc,argv,nil,NSStringFromClass(Continuity.class));}}
