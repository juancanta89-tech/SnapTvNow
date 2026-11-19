import AVFoundation
import CoreVideo
import QuartzCore
import UIKit

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let surface = UIViewController()
    private let layer = AVPlayerLayer()

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = surface
        window.makeKeyAndVisible()
        self.window = window
        layer.frame = window.bounds
        surface.view.layer.addSublayer(layer)
        Task { await runChecks() }
        return true
    }

    private func append(_ fields: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: fields),
              let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let file = directory.appendingPathComponent("verification.jsonl")
        if !FileManager.default.fileExists(atPath: file.path) {
            FileManager.default.createFile(atPath: file.path, contents: nil)
        }
        guard let handle = try? FileHandle(forWritingTo: file) else { return }
        defer { try? handle.close() }
        handle.seekToEndOfFile()
        handle.write(data)
        handle.write(Data([10]))
    }

    private func runChecks() async {
        let env = ProcessInfo.processInfo.environment
        guard let password = env["TEST_PASSWORD"], !password.isEmpty,
              let encoded = password.addingPercentEncoding(withAllowedCharacters:
                CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")) else {
            append(["stage": "setup", "error": "missing_test_access"])
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        append(["stage": "simulator", "os": UIDevice.current.systemVersion])
        guard env["PRIMARY_ID"] == "876" else { append(["stage": "scope_failed"]); return }
        for format in ["ts", "m3u8"] {
            if let url = URL(string: "https://api.snaptvnow.com/live/dieguito2026/" + encoded + "/876." + format) {
                await play(url, label: "Gol Peru L 876", format: format)
                try? await Task.sleep(nanoseconds: 1_500_000_000)
            }
        }
        append(["stage": "finished"])
    }

    private func play(_ url: URL, label: String, format: String) async {
        let item = AVPlayerItem(url: url)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes:
            [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA])
        item.add(output)
        let player = AVPlayer(playerItem: item)
        layer.player = player
        player.play()
        let start = CACurrentMediaTime()
        var firstClock: Double?
        var clockAdvance = 0.0
        var frameCount = 0
        var lastFrame: CMTime?
        var firstFrameMs: Int?
        var firstMediaFrame: Double?
        var lastMediaFrame: Double?
        var lastNewPicture: Double?
        var maxPictureGap = 0.0
        var occupied = Set<Int>()
        var frameSpan = 0.0
        var confirmed = false
        repeat {
            try? await Task.sleep(nanoseconds: 150_000_000)
            let time = item.currentTime().seconds
            if player.timeControlStatus == .playing && time.isFinite {
                if firstClock == nil { firstClock = time }
                clockAdvance = max(clockAdvance, time - (firstClock ?? time))
            }
            let requested = output.itemTime(forHostTime: CACurrentMediaTime())
            if requested.isNumeric && output.hasNewPixelBuffer(forItemTime: requested) {
                var presented = CMTime.invalid
                if output.copyPixelBuffer(forItemTime: requested, itemTimeForDisplay: &presented) != nil,
                   presented.isNumeric && (lastFrame == nil || CMTimeCompare(presented, lastFrame!) != 0) {
                    lastFrame = presented
                    frameCount += 1
                    let now = CACurrentMediaTime()
                    if let lastNewPicture { maxPictureGap = max(maxPictureGap, now-lastNewPicture) }
                    lastNewPicture = now
                    if presented.seconds.isFinite {
                        if firstMediaFrame == nil { firstMediaFrame = presented.seconds }
                        if let prior = lastMediaFrame, presented.seconds < prior - 0.5 {
                            occupied.removeAll(); firstMediaFrame = presented.seconds
                        }
                        lastMediaFrame = presented.seconds
                        frameSpan = max(0, presented.seconds - (firstMediaFrame ?? presented.seconds))
                        occupied.insert(Int(frameSpan / 2))
                    }
                    if firstFrameMs == nil { firstFrameMs = Int((CACurrentMediaTime() - start) * 1000) }
                }
            }
            confirmed = frameSpan >= 20 && clockAdvance >= 20 && frameCount >= 45 &&
                (0..<10).allSatisfy { occupied.contains($0) } && maxPictureGap <= 4
            if item.status == .failed || confirmed { break }
        } while CACurrentMediaTime() - start < 72

        var result: [String: Any] = [
            "stage": "apple_playback", "case": label, "format": format,
            "player_ready": item.status == .readyToPlay,
            "presented_frames": frameCount, "clock_ms": Int(clockAdvance * 1000),
            "video_display_ready": layer.isReadyForDisplay,
            "audio_tracks": item.tracks.filter { $0.assetTrack?.mediaType == .audio }.count,
            "enabled_audio_tracks": item.tracks.filter { $0.isEnabled && $0.assetTrack?.mediaType == .audio }.count,
            "frame_span_ms": Int(frameSpan * 1000), "max_picture_gap_ms": Int(maxPictureGap * 1000),
            "two_second_buckets": occupied.count,
            "segments_reported": (item.accessLog()?.events ?? []).reduce(0) { $0 + $1.numberOfSegmentsDownloaded },
            "stalls_reported": (item.accessLog()?.events ?? []).reduce(0) { $0 + $1.numberOfStalls },
            "outcome": confirmed ? "continuous_video" :
                (item.status == .failed ? "player_error" : "no_confirmed_video")
        ]
        if let firstFrameMs { result["first_frame_ms"] = firstFrameMs }
        if let ns = item.error as NSError? {
            result["error_code"] = ns.code
            result["error_domain"] = safeDomain(ns.domain)
            if let nested = ns.userInfo[NSUnderlyingErrorKey] as? NSError {
                result["underlying_code"] = nested.code
                result["underlying_domain"] = safeDomain(nested.domain)
            }
        }
        result["log_codes"] = Array((item.errorLog()?.events ?? []).suffix(4)).map { $0.errorStatusCode }
        append(result)
        player.pause()
        layer.player = nil
        player.replaceCurrentItem(with: nil)
        item.remove(output)
    }

    private func safeDomain(_ name: String) -> String {
        ["AVFoundationErrorDomain", "CoreMediaErrorDomain", "NSURLErrorDomain",
         "NSOSStatusErrorDomain"].contains(name) ? name : "other"
    }
}
