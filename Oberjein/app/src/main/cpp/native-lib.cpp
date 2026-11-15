#include <jni.h>
#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "synth.h"

// Owns the Oboe output stream and the synth. Param/note calls are lock-free;
// the mutex only guards stream start/stop.
class Engine : public oboe::AudioStreamDataCallback,
               public oboe::AudioStreamErrorCallback,
               public std::enable_shared_from_this<Engine> {
public:
    ob::Synth synth;

    bool start() {
        std::lock_guard<std::mutex> lock(mutex_);
        wantRunning_ = true;
        if (stream_) return true;
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setFormatConversionAllowed(true)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setChannelConversionAllowed(true)
            ->setUsage(oboe::Usage::Game)
            ->setDataCallback(shared_from_this())
            ->setErrorCallback(shared_from_this());
        if (builder.openStream(stream_) != oboe::Result::OK) {
            stream_.reset();
            return false;
        }
        synth.setSampleRate(static_cast<float>(stream_->getSampleRate()));
        stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
        if (stream_->requestStart() != oboe::Result::OK) {
            stream_->close();
            stream_.reset();
            return false;
        }
        return true;
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mutex_);
        wantRunning_ = false;
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* audioData,
                                          int32_t numFrames) override {
        synth.render(static_cast<float*>(audioData), numFrames);
        return oboe::DataCallbackResult::Continue;
    }

    // Headphones unplugged, route changed, etc.: reopen on the new device.
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result error) override {
        if (error != oboe::Result::ErrorDisconnected) return;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            stream_.reset();
        }
        if (wantRunning_) start();
    }

private:
    std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> wantRunning_{false};
};

static std::shared_ptr<Engine>& engine() {
    static std::shared_ptr<Engine> e = std::make_shared<Engine>();
    return e;
}

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_oberjein_synth_SynthEngine_nativeStart(JNIEnv*, jobject) {
    return engine()->start() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativeStop(JNIEnv*, jobject) {
    engine()->stop();
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativeSetParam(JNIEnv*, jobject, jint id, jfloat v) {
    engine()->synth.setParam(id, v);
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativeNoteOn(JNIEnv*, jobject, jint note, jfloat vel) {
    engine()->synth.noteOn(note, vel);
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativeNoteOff(JNIEnv*, jobject, jint note) {
    engine()->synth.noteOff(note);
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativeAllNotesOff(JNIEnv*, jobject) {
    engine()->synth.allNotesOff();
}
JNIEXPORT void JNICALL Java_com_oberjein_synth_SynthEngine_nativePitchBend(JNIEnv*, jobject, jfloat v) {
    engine()->synth.pitchBend(v);
}

}  // extern "C"
