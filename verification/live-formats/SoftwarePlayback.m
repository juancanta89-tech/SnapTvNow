#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <MobileVLCKit/MobileVLCKit.h>

@interface SoftwareCheck : UIResponder <UIApplicationDelegate>
@property (nonatomic, strong) UIWindow *window;
@property (nonatomic, strong) VLCMediaPlayer *player;
@property (nonatomic, strong) VLCMedia *media;
@property (nonatomic, copy) NSString *root;
@property (nonatomic, copy) NSArray<NSString *> *formats;
@property (nonatomic) NSInteger index;
@property (nonatomic) NSTimeInterval started;
@property (nonatomic, strong) NSTimer *timer;
@end

@implementation SoftwareCheck
- (void)record:(NSDictionary *)data {
    NSURL *dir = [NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
    NSString *file = [dir URLByAppendingPathComponent:@"verification.jsonl"].path;
    if (![NSFileManager.defaultManager fileExistsAtPath:file])
        [NSFileManager.defaultManager createFileAtPath:file contents:nil attributes:nil];
    NSFileHandle *handle = [NSFileHandle fileHandleForWritingAtPath:file];
    [handle seekToEndOfFile];
    [handle writeData:[NSJSONSerialization dataWithJSONObject:data options:0 error:nil]];
    [handle writeData:[@"\n" dataUsingEncoding:NSUTF8StringEncoding]];
    [handle closeFile];
}
- (BOOL)application:(UIApplication *)app didFinishLaunchingWithOptions:(NSDictionary *)opts {
    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
    NSDictionary *env = NSProcessInfo.processInfo.environment;
    NSString *idText = env[@"PRIMARY_ID"];
    NSString *password = [env[@"TEST_PASSWORD"] stringByAddingPercentEncodingWithAllowedCharacters:
        [NSCharacterSet characterSetWithCharactersInString:@"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"]];
    if (![idText isEqualToString:@"878"] || !password.length) {
        [self record:@{@"stage":@"setup",@"error":@"verified_feed_required"}];
        [self record:@{@"stage":@"finished"}];
        return YES;
    }
    self.root = [NSString stringWithFormat:@"https://api.snaptvnow.com/live/dieguito2026/%@/%@.", password, idText];
    self.formats = @[@"m3u8",@"ts"];
    [self record:@{@"stage":@"software_simulator",@"os":UIDevice.currentDevice.systemVersion}];
    [self nextFormat];
    return YES;
}
- (void)nextFormat {
    if (self.index >= self.formats.count) {
        [self record:@{@"stage":@"finished"}];
        return;
    }
    self.player = [[VLCMediaPlayer alloc] initWithOptions:@[@"--stats"]];
    self.player.drawable = self.window.rootViewController.view;
    NSURL *url = [NSURL URLWithString:[self.root stringByAppendingString:self.formats[self.index]]];
    self.media = [VLCMedia mediaWithURL:url];
    self.player.media = self.media;
    self.started = NSProcessInfo.processInfo.systemUptime;
    [self.player play];
    self.timer = [NSTimer scheduledTimerWithTimeInterval:0.25 target:self selector:@selector(tick:) userInfo:nil repeats:YES];
}
- (void)tick:(NSTimer *)timer {
    NSInteger pictures = self.media.numberOfDisplayedPictures;
    NSInteger audio = self.media.numberOfPlayedAudioBuffers;
    NSTimeInterval elapsed = NSProcessInfo.processInfo.systemUptime - self.started;
    if ((pictures < 8 || audio < 3) && elapsed < 30 && self.player.state != VLCMediaPlayerStateError) return;
    [timer invalidate];
    [self record:@{@"stage":@"software_playback", @"format":self.formats[self.index],
                   @"displayed_pictures":@(pictures), @"played_audio_buffers":@(audio),
                   @"decoded_audio_blocks":@(self.media.numberOfDecodedAudioBlocks), @"has_video":@(self.player.hasVideoOut),
                   @"elapsed_ms":@((NSInteger)(elapsed * 1000)), @"state":@(self.player.state)}];
    [self.player stop];
    self.player.drawable = nil;
    self.player = nil;
    self.media = nil;
    self.index++;
    [self performSelector:@selector(nextFormat) withObject:nil afterDelay:2.0];
}
@end

int main(int argc, char **argv) {
    @autoreleasepool { return UIApplicationMain(argc, argv, nil, NSStringFromClass(SoftwareCheck.class)); }
}
