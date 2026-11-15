#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <vector>

namespace ob {

/**
 * Stereo FX processor inspired by granular loop pedals.
 * All buffers are allocated by setSampleRate(), never in the audio callback.
 * Parameter IDs are documented in FxParam below.
 */
class FxEngine {
public:
    enum FxParam : int {
        ENABLE = 0, EFFECT, VARIATION, TIME, ACTIVITY, REPEATS, SHAPE, FILTER,
        MIX, SPACE, LOOP_LEVEL, PITCH_DEPTH, PITCH_RATE, RESONANCE, REVERB_MODE,
        BYPASS_MODE, TRAILS, REVERSE, STEREO_MODE, TEMPO, TEMPO_MODE,
        FX_MASTER, LOOP_FADE, FX_PARAM_COUNT
    };
    enum LoopCommand : int { LOOP_STOP = 0, LOOP_RECORD = 1, LOOP_PLAY = 2, LOOP_DUB = 3, LOOP_ERASE = 4 };

    FxEngine() {
        for (auto& p : params_) p.store(0.0f, std::memory_order_relaxed);
        params_[MIX].store(0.35f);
        params_[SPACE].store(0.25f);
        params_[LOOP_LEVEL].store(0.7f);
        params_[TEMPO].store(120.0f);
        params_[FX_MASTER].store(0.8f);
        params_[LOOP_FADE].store(0.4f);
        params_[ENABLE].store(0.0f);
        params_[BYPASS_MODE].store(0.0f);
    }

    void setSampleRate(float sr) {
        sr_ = std::clamp(sr, 8000.0f, 192000.0f);
        const size_t delayFrames = static_cast<size_t>(sr_ * 2.0f) + 8;
        const size_t loopFrames = static_cast<size_t>(sr_ * 60.0f) + 8;
        delayL_.assign(delayFrames, 0.0f);
        delayR_.assign(delayFrames, 0.0f);
        holdL_.assign(static_cast<size_t>(sr_ * 2.0f) + 8, 0.0f);
        holdR_.assign(static_cast<size_t>(sr_ * 2.0f) + 8, 0.0f);
        loopL_.assign(loopFrames, 0.0f);
        loopR_.assign(loopFrames, 0.0f);
        delayWrite_ = holdWrite_ = 0;
        loopLength_ = loopWrite_ = loopRead_ = 0;
        loopRecording_ = loopPlaying_ = loopOverdub_ = false;
        holdActive_ = false;
        resetReverb();
    }

    void setParam(int id, float value) {
        if (id < 0 || id >= FX_PARAM_COUNT) return;
        if (id == TEMPO) value = std::clamp(value, 30.0f, 300.0f);
        else value = std::clamp(value, 0.0f, 1.0f);
        params_[id].store(value, std::memory_order_relaxed);
    }

    float getParam(int id) const {
        return (id >= 0 && id < FX_PARAM_COUNT) ? params_[id].load(std::memory_order_relaxed) : 0.0f;
    }

    void tapTempo(float bpm) { setParam(TEMPO, bpm); }
    void setHold(bool enabled) { holdRequest_.store(enabled, std::memory_order_release); }
    void commandLooper(LoopCommand command) { loopCommand_.store(static_cast<int>(command), std::memory_order_release); }
    bool loopHasAudio() const { return loopLength_.load(std::memory_order_relaxed) > 0; }

    void process(float& left, float& right) {
        const float dryL = left, dryR = right;
        if (delayL_.empty()) return;
        const int stereoMode = std::clamp(static_cast<int>(std::lround(param(STEREO_MODE) * 3.0f)), 0, 3);
        const float monoInput = 0.5f * (dryL + dryR);
        const float fxInL = (stereoMode == 0 || stereoMode == 1) ? monoInput : dryL;
        const float fxInR = (stereoMode == 0 || stereoMode == 1) ? monoInput : dryR;

        const bool holdRequest = holdRequest_.load(std::memory_order_acquire);
        if (holdRequest != holdActive_) {
            holdActive_ = holdRequest;
            if (holdActive_) {
                holdLength_ = std::clamp(static_cast<size_t>(sr_ * (0.25f + 1.25f * param(ACTIVITY))), size_t(256), holdL_.size() - 1);
                holdStart_ = (holdWrite_ + holdL_.size() - holdLength_) % holdL_.size();
                holdRead_ = 0;
            } else {
                holdLength_ = 0;
            }
        }
        if (!holdActive_) {
            holdL_[holdWrite_] = fxInL;
            holdR_[holdWrite_] = fxInR;
            holdWrite_ = (holdWrite_ + 1) % holdL_.size();
        }

        const int command = loopCommand_.exchange(-1, std::memory_order_acq_rel);
        applyLoopCommand(command);
        const bool enabled = param(ENABLE) > 0.5f;
        if (!enabled && !loopRecording_ && !loopOverdub_ && !loopPlaying_ && !holdActive_) {
            delayWrite_ = (delayWrite_ + 1) % delayL_.size();
            return;
        }

        delayL_[delayWrite_] = fxInL;
        delayR_[delayWrite_] = fxInR;

        const float time = param(TIME);
        const float activity = param(ACTIVITY);
        const float repeats = param(REPEATS);
        const float shape = param(SHAPE);
        const float cutoff = param(FILTER);
        const float space = param(SPACE);
        const float pitchDepth = param(PITCH_DEPTH);
        const float pitchRate = param(PITCH_RATE);
        const int effect = std::clamp(static_cast<int>(std::lround(param(EFFECT) * 10.0f)), 0, 10);
        const int variation = std::clamp(static_cast<int>(std::lround(param(VARIATION) * 3.0f)), 0, 3);
        const float bpm = param(TEMPO);
        const float beat = 60.0f / std::max(30.0f, bpm);
        const float subdivision = param(TEMPO_MODE) > 0.5f ? 0.5f : std::pow(2.0f, -5.0f + time * 7.0f);
        const float baseDelay = std::clamp(beat * subdivision * sr_, 64.0f, static_cast<float>(delayL_.size() - 4));
        const float feedback = std::clamp(0.08f + repeats * 0.86f, 0.08f, 0.94f);

        float wetL = dryL, wetR = dryR;
        const float phase = phase_;
        const float modulation = std::sin(phase * 6.283185307f) * pitchDepth;
        const float sweep = 1.0f + modulation * (0.03f + pitchRate * 0.22f);
        auto readDelay = [&](float frames, bool reverse) {
            frames = std::clamp(frames, 1.0f, static_cast<float>(delayL_.size() - 3));
            const float pos = reverse
                ? static_cast<float>(delayWrite_) + frames
                : static_cast<float>(delayWrite_) - frames;
            float wrapped = std::fmod(pos, static_cast<float>(delayL_.size()));
            if (wrapped < 0.0f) wrapped += static_cast<float>(delayL_.size());
            const size_t i0 = static_cast<size_t>(wrapped);
            const size_t i1 = (i0 + 1) % delayL_.size();
            const float frac = wrapped - static_cast<float>(i0);
            const float l = delayL_[i0] + (delayL_[i1] - delayL_[i0]) * frac;
            const float r = delayR_[i0] + (delayR_[i1] - delayR_[i0]) * frac;
            return std::array<float,2>{l,r};
        };

        switch (effect) {
            case 0: { // MOSAIC
                const float d = baseDelay * (variation == 0 ? 1.0f : variation == 1 ? 2.0f : variation == 2 ? 0.5f : 1.5f);
                auto a = readDelay(d, false); auto b = readDelay(d * (variation == 3 ? 0.25f : 0.5f), false);
                wetL = (a[0] + b[0] * activity) * 0.6f;
                wetR = (a[1] + b[1] * activity) * 0.6f;
                break;
            }
            case 1: { // SEQ
                const int slices = 2 + static_cast<int>(activity * 14.0f);
                const int slice = static_cast<int>(phase_ * slices) % slices;
                auto a = readDelay(baseDelay * (1.0f + slice * 0.125f), (slice + variation) % 2 == 1);
                wetL = a[0] * (0.65f + 0.35f * shape); wetR = a[1] * (0.65f + 0.35f * shape);
                break;
            }
            case 2: { // GLIDE
                auto a = readDelay(baseDelay * sweep, variation == 3 && modulation < 0.0f);
                wetL = a[0]; wetR = a[1];
                break;
            }
            case 3: { // HAZE
                wetL = wetR = 0.0f;
                const int grains = 2 + static_cast<int>(activity * 10.0f);
                for (int g = 0; g < grains; ++g) {
                    const float offset = baseDelay * (0.25f + 0.13f * g) * (variation == 2 ? 0.5f : variation == 3 ? 2.0f : 1.0f);
                    auto a = readDelay(offset * sweep, (g & 1) != 0);
                    const float weight = 1.0f / static_cast<float>(grains);
                    wetL += a[0] * weight; wetR += a[1] * weight;
                }
                break;
            }
            case 4: { // TUNNEL
                auto a = readDelay(std::min(baseDelay * (1.0f + activity * 3.0f), sr_ * 1.8f), false);
                wetL = a[0] * (0.6f + 0.4f * repeats); wetR = a[1] * (0.6f + 0.4f * repeats);
                break;
            }
            case 5: { // GLITCH
                const int block = std::max(32, static_cast<int>(baseDelay * (0.2f + 0.8f * (1.0f - activity))));
                auto a = readDelay(static_cast<float>((delayWrite_ / static_cast<size_t>(block)) * block % delayL_.size()), variation == 1);
                wetL = a[0]; wetR = a[1];
                break;
            }
            case 6: { // BLOCKS
                const int block = std::max(32, static_cast<int>(baseDelay / (1.0f + activity * 7.0f)));
                auto a = readDelay(static_cast<float>(block + (delayWrite_ % static_cast<size_t>(block))), variation == 2);
                wetL = a[0]; wetR = a[1];
                break;
            }
            case 7: { // INTERRUPT
                const int period = std::max(64, static_cast<int>(beat * sr_ / (1.0f + activity * 7.0f)));
                const bool muted = ((delayWrite_ / static_cast<size_t>(period)) % 4) == (variation % 4);
                auto a = readDelay(baseDelay, false);
                wetL = muted ? 0.0f : a[0]; wetR = muted ? 0.0f : a[1];
                break;
            }
            case 8: { // ARP
                const int divisions = 3 + variation;
                const int idx = static_cast<int>(phase_ * divisions) % divisions;
                auto a = readDelay(baseDelay * std::pow(2.0f, (idx - 1) * (0.25f + activity * 0.5f)), false);
                wetL = a[0]; wetR = a[1];
                break;
            }
            case 9: { // PATTERN
                const int tapCount = 2 + static_cast<int>(activity * 6.0f);
                wetL = wetR = 0.0f;
                for (int tap = 1; tap <= tapCount; ++tap) {
                    auto a = readDelay(baseDelay * tap * (variation == 2 ? 0.5f : 1.0f), false);
                    wetL += a[0] / tapCount; wetR += a[1] / tapCount;
                }
                break;
            }
            case 10: { // WARP
                auto a = readDelay(baseDelay * (0.55f + 0.45f * sweep), modulation < 0.0f);
                wetL = a[0] + previousWarpL_ * feedback; wetR = a[1] + previousWarpR_ * feedback;
                previousWarpL_ = wetL; previousWarpR_ = wetR;
                break;
            }
        }

        const float lpCoeff = std::clamp(0.005f + cutoff * cutoff * 0.45f, 0.005f, 0.95f);
        fxLowL_ += lpCoeff * (wetL - fxLowL_ + param(RESONANCE) * (fxLowL_ - fxLowPrevL_));
        fxLowR_ += lpCoeff * (wetR - fxLowR_ + param(RESONANCE) * (fxLowR_ - fxLowPrevR_));
        fxLowPrevL_ = fxLowL_; fxLowPrevR_ = fxLowR_;
        wetL = fxLowL_; wetR = fxLowR_;

        const int revMode = std::clamp(static_cast<int>(std::lround(param(REVERB_MODE) * 3.0f)), 0, 3);
        const float revFb = 0.35f + revMode * 0.13f + space * 0.12f;
        float revL = 0.0f, revR = 0.0f;
        const int lengths[4] = {149, 211, 293, 401};
        for (int i = 0; i < 4; ++i) {
            const size_t pos = revPos_[i];
            const float dl = reverbL_[i][pos], dr = reverbR_[i][pos];
            reverbL_[i][pos] = wetL + dl * revFb;
            reverbR_[i][pos] = wetR + dr * revFb;
            revPos_[i] = (pos + 1) % static_cast<size_t>(lengths[i]);
            revL += dl; revR += dr;
        }
        const float reverbAmount = std::clamp(space * (0.25f + 0.75f * shape), 0.0f, 1.0f);
        wetL = wetL * (1.0f - reverbAmount) + (revL * 0.25f) * reverbAmount;
        wetR = wetR * (1.0f - reverbAmount) + (revR * 0.25f) * reverbAmount;

        if (holdActive_ && holdLength_ > 0) {
            const size_t i = (holdStart_ + holdRead_) % holdL_.size();
            wetL += holdL_[i] * activity * 0.35f;
            wetR += holdR_[i] * activity * 0.35f;
            holdRead_ = (holdRead_ + 1) % holdLength_;
        }

        const int bypass = std::clamp(static_cast<int>(std::lround(param(BYPASS_MODE) * 2.0f)), 0, 2);
        const float mix = (enabled || holdActive_) ? param(MIX) : (bypass == 1 && param(TRAILS) > 0.5f ? bypassFade_ : 0.0f);
        if (!enabled && bypass == 1) bypassFade_ = std::max(0.0f, bypassFade_ - 0.0002f);
        else if (enabled) bypassFade_ = 1.0f;
        const float effectMaster = param(FX_MASTER);
        // The secondary master affects only the processed path; bypass keeps dry unity gain.
        float outL = dryL * (1.0f - mix) + wetL * mix * effectMaster;
        float outR = dryR * (1.0f - mix) + wetR * mix * effectMaster;
        if (stereoMode == 0 || stereoMode == 3) { const float mono = 0.5f * (outL + outR); outL = mono; outR = mono; }

        if (loopRecording_ || loopOverdub_) {
            if (loopWrite_ < loopL_.size()) {
                if (loopOverdub_ && loopLength_ > 0) {
                    loopL_[loopWrite_] = loopL_[loopWrite_] * 0.75f + outL * 0.5f;
                    loopR_[loopWrite_] = loopR_[loopWrite_] * 0.75f + outR * 0.5f;
                    loopWrite_ = (loopWrite_ + 1) % loopLength_;
                    if (loopDubRemaining_ > 0 && --loopDubRemaining_ == 0) loopOverdub_ = false;
                } else {
                    loopL_[loopWrite_] = outL; loopR_[loopWrite_] = outR;
                    ++loopWrite_;
                    if (loopRecording_ && loopWrite_ >= loopL_.size()) finishRecording();
                }
            }
        }
        if (loopPlaying_ && loopLength_ > 0) {
            const float length = static_cast<float>(loopLength_);
            float pos = loopReadPos_;
            pos = std::fmod(pos, length); if (pos < 0.0f) pos += length;
            const size_t i0 = static_cast<size_t>(pos) % loopLength_;
            const size_t i1 = (i0 + 1) % loopLength_;
            const float fraction = pos - static_cast<float>(i0);
            const float loopL = loopL_[i0] + (loopL_[i1] - loopL_[i0]) * fraction;
            const float loopR = loopR_[i0] + (loopR_[i1] - loopR_[i0]) * fraction;
            const float fade = loopFadeRemaining_ > 0 ? static_cast<float>(loopFadeRemaining_) / static_cast<float>(loopFadeTotal_) : 1.0f;
            const float level = param(LOOP_LEVEL) * fade;
            outL += loopL * level; outR += loopR * level;
            const float speed = std::pow(2.0f, -2.0f + param(TIME) * 4.0f);
            loopReadPos_ += param(REVERSE) > 0.5f ? -speed : speed;
            while (loopReadPos_ >= length) loopReadPos_ -= length;
            while (loopReadPos_ < 0.0f) loopReadPos_ += length;
            loopRead_ = static_cast<size_t>(loopReadPos_) % loopLength_;
            if (loopFadeRemaining_ > 0 && --loopFadeRemaining_ == 0) loopPlaying_ = false;
        }

        left = std::clamp(outL, -4.0f, 4.0f);
        right = std::clamp(outR, -4.0f, 4.0f);
        delayWrite_ = (delayWrite_ + 1) % delayL_.size();
        phase_ += 1.0f / (sr_ * beat);
        phase_ -= std::floor(phase_);
    }

private:
    float param(int id) const { return params_[id].load(std::memory_order_relaxed); }
    void resetReverb() {
        for (auto& line : reverbL_) line.fill(0.0f);
        for (auto& line : reverbR_) line.fill(0.0f);
        revPos_.fill(0);
    }
    void finishRecording() {
        loopRecording_ = false;
        loopLength_.store(loopWrite_, std::memory_order_relaxed);
        loopRead_ = 0;
        loopReadPos_ = 0.0f;
        loopFadeRemaining_ = 0;
        loopPlaying_ = true;
    }
    void applyLoopCommand(int command) {
        if (command < 0) return;
        switch (command) {
            case LOOP_STOP:
                loopRecording_ = false; loopOverdub_ = false;
                if (loopPlaying_ && loopLength_ > 0) {
                    loopFadeTotal_ = std::max<size_t>(1, static_cast<size_t>(sr_ * (0.03f + param(LOOP_FADE) * 2.0f)));
                    loopFadeRemaining_ = loopFadeTotal_;
                } else loopPlaying_ = false;
                break;
            case LOOP_RECORD: loopWrite_ = 0; loopLength_.store(0); loopFadeRemaining_ = 0; loopRecording_ = true; loopOverdub_ = false; loopPlaying_ = false; break;
            case LOOP_PLAY: loopRead_ = 0; loopReadPos_ = 0.0f; loopFadeRemaining_ = 0; loopRecording_ = false; loopPlaying_ = loopLength_ > 0; loopOverdub_ = false; break;
            case LOOP_DUB: if (loopLength_ > 0) { loopWrite_ = 0; loopReadPos_ = 0.0f; loopDubRemaining_ = loopLength_; loopRecording_ = false; loopPlaying_ = true; loopOverdub_ = true; } break;
            case LOOP_ERASE: loopPlaying_ = loopRecording_ = loopOverdub_ = false; loopFadeRemaining_ = 0; loopWrite_ = loopRead_ = 0; loopLength_.store(0); break;
        }
    }

    std::array<std::atomic<float>, FX_PARAM_COUNT> params_{};
    float sr_ = 48000.0f;
    std::vector<float> delayL_, delayR_, holdL_, holdR_, loopL_, loopR_;
    size_t delayWrite_ = 0, holdWrite_ = 0, holdStart_ = 0, holdRead_ = 0, holdLength_ = 0;
    std::atomic<bool> holdRequest_{false};
    bool holdActive_ = false;
    size_t loopWrite_ = 0, loopRead_ = 0, loopDubRemaining_ = 0, loopFadeRemaining_ = 0, loopFadeTotal_ = 1;
    float loopReadPos_ = 0.0f;
    std::atomic<size_t> loopLength_{0};
    bool loopRecording_ = false, loopPlaying_ = false, loopOverdub_ = false;
    std::atomic<int> loopCommand_{-1};
    std::array<std::array<float, 2048>,4> reverbL_{}, reverbR_{};
    std::array<size_t,4> revPos_{};
    float fxLowL_ = 0.0f, fxLowR_ = 0.0f, fxLowPrevL_ = 0.0f, fxLowPrevR_ = 0.0f;
    float previousWarpL_ = 0.0f, previousWarpR_ = 0.0f;
    float bypassFade_ = 0.0f, phase_ = 0.0f;
};

} // namespace ob
