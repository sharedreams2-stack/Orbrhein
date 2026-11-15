// Oberjein DSP core: 16-voice virtual-analog polysynth with selectable 1x/2x/4x rendering.
// Header-only, allocation-free and lock-free on the audio thread.
#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>

namespace ob {

constexpr int kNumVoices = 16;
constexpr int kCtrlRate = 16;  // control-rate tick, in samples
constexpr float kPi = 3.14159265358979323846f;

// Keep this order identical to Params.kt (object P).
enum Param : int {
    P_VCO1_SAW = 0, P_VCO1_PULSE, P_VCO1_LEVEL,
    P_VCO2_SAW, P_VCO2_PULSE, P_VCO2_LEVEL, P_VCO2_SEMI, P_VCO2_DETUNE, P_SYNC,
    P_PWM, P_NOISE,
    P_CUTOFF, P_RESO, P_POLES, P_FENV_AMT, P_KEYTRACK,
    P_F_A, P_F_D, P_F_S, P_F_R,
    P_A_A, P_A_D, P_A_S, P_A_R,
    P_LFO_RATE, P_LFO_WAVE, P_LFO_PITCH, P_LFO_FILTER, P_LFO_PWM,
    P_PORTA, P_UNISON, P_UNI_DETUNE, P_VOLUME,
    P_SPREAD, P_XMOD, P_BRIGHT,
    P_FILTER_MIX, P_FILTER_BP, P_LP24, P_FILTER_MULTI, P_FENV_INVERT, P_SELF_OSC,
    P_LFO2_RATE, P_LFO2_WAVE, P_LFO2_PITCH, P_LFO2_FILTER, P_LFO2_PWM,
    P_VOICE_COUNT, P_UNI_VOICES, P_VAM, P_PERSISTENT_VOICING, P_LEGATO,
    P_OVERSAMPLING, P_TRANSPOSE, P_FINE_TUNE,
    P_COUNT
};

inline float fastTanh(float x) {
    x = std::clamp(x, -3.0f, 3.0f);
    const float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}

inline float polyBlep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.0f; }
    if (t > 1.0f - dt) { t = (t - 1.0f) / dt; return t * t + t + t + 1.0f; }
    return 0.0f;
}

inline float oscSample(float p, float dt, float pw, bool saw, bool pulse) {
    float s = 0.0f;
    if (saw) s += 2.0f * p - 1.0f - polyBlep(p, dt);
    if (!saw && !pulse) return 1.0f - 4.0f * std::fabs(p - 0.5f);
    if (pulse) {
        float v = (p < pw ? 1.0f : -1.0f) + polyBlep(p, dt);
        float t2 = p - pw;
        if (t2 < 0.0f) t2 += 1.0f;
        s += v - polyBlep(t2, dt);
    }
    return (saw && pulse) ? s * 0.6f : s;
}

struct EnvCoef { float a, d, s, r; };

struct Env {
    enum Stage { Idle, Attack, Decay, Sustain, Release };
    Stage stage = Idle;
    float level = 0.0f;
    void gateOn() { stage = Attack; }
    void gateOff() { if (stage != Idle) stage = Release; }
    void reset() { stage = Idle; level = 0.0f; }
    float tick(const EnvCoef& c) {
        switch (stage) {
            case Attack:
                level += (1.3f - level) * c.a;
                if (level >= 1.0f) { level = 1.0f; stage = Decay; }
                break;
            case Decay:
                level += (c.s - level) * c.d;
                if (std::fabs(level - c.s) < 1e-4f) { level = c.s; stage = Sustain; }
                break;
            case Sustain: level = c.s; break;
            case Release:
                level -= level * c.r;
                if (level < 1e-4f) { level = 0.0f; stage = Idle; }
                break;
            default: break;
        }
        return level;
    }
};

// TPT state-variable filter: simultaneous LP/BP/HP/Notch outputs.
struct SemFilter {
    float ic1 = 0.0f, ic2 = 0.0f;
    void reset() { ic1 = ic2 = 0.0f; }
    struct Outputs { float lp, bp, hp, notch; };
    Outputs process(float x, float g, float damping) {
        const float a1 = 1.0f / (1.0f + g * (g + damping));
        const float a2 = g * a1;
        const float a3 = g * a2;
        const float v3 = x - ic2;
        const float band = a1 * ic1 + a2 * v3;
        const float low = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2.0f * band - ic1;
        ic2 = 2.0f * low - ic2;
        const float high = x - damping * band - low;
        return {low, band, high, low + high};
    }
};
struct Voice {
    int note = -1;
    bool gate = false;
    uint64_t age = 0;
    float vel = 1.0f;
    float curSemi = 60.0f, tgtSemi = 60.0f, uniCents = 0.0f;
    float p1 = 0.0f, p2 = 0.0f;
    float dt1 = 0.0f, dt2 = 0.0f, pw = 0.5f, g = 0.5f;
    float drift1 = 0.0f, drift2 = 0.0f;
    float gl = 0.7f, gr = 0.7f;
    Env fenv, aenv;
    SemFilter filter, filter2;
    float lp6 = 0.0f;
    bool active() const { return aenv.stage != Env::Idle; }
};

struct Event { int type; int note; float value; };

class Synth {
public:
    Synth() {
        for (int i = 0; i < kNumVoices; ++i) {
            const float pan = (static_cast<float>(i) - 7.5f) / 7.5f * 0.35f;
            voices_[i].gl = std::cos((pan + 1.0f) * kPi / 4.0f);
            voices_[i].gr = std::sin((pan + 1.0f) * kPi / 4.0f);
        }
        for (auto& p : params_) p.store(0.0f);
    }

    void setSampleRate(float sr) { sr_ = sr; }

    // ---- control API (any thread) ----
    void setParam(int id, float v) {
        if (id >= 0 && id < P_COUNT) params_[id].store(v, std::memory_order_relaxed);
    }
    void noteOn(int note, float vel) { push({0, note, vel}); }
    void noteOff(int note) { push({1, note, 0.0f}); }
    void allNotesOff() { push({2, 0, 0.0f}); }
    void pitchBend(float v) { push({3, 0, v}); }

    // ---- audio thread: interleaved stereo float ----
    void render(float* out, int frames) {
        drainEvents();
        static constexpr float fir2[15] = {
            0.0f, 0.0f, 0.005758462f, 0.0f, -0.048720975f, 0.0f, 0.292961742f,
            0.500001543f, 0.292961742f, 0.0f, -0.048720975f, 0.0f, 0.005758462f, 0.0f, 0.0f
        };
        static constexpr float fir4[31] = {
            0.0f, -0.000091147f, -0.000290069f, 0.0f, 0.001576655f, 0.004137030f,
            0.005019806f, 0.0f, -0.012666079f, -0.027038521f, -0.028353120f, 0.0f,
            0.063699249f, 0.148026618f, 0.221009744f, 0.249939669f, 0.221009744f,
            0.148026618f, 0.063699249f, 0.0f, -0.028353120f, -0.027038521f,
            -0.012666079f, 0.0f, 0.005019806f, 0.004137030f, 0.001576655f,
            0.0f, -0.000290069f, -0.000091147f, 0.0f
        };
        for (int n = 0; n < frames; ++n) {
            if (ctrl_ == 0) { updateControl(); ctrl_ = kCtrlRate; }
            --ctrl_;
            volSm_ += (q_.volume - volSm_) * 0.002f;
            const int factor = q_.oversampling;
            if (factor != lastOversampling_) {
                decimatorL_.fill(0.0f);
                decimatorR_.fill(0.0f);
                decimatorCursor_ = 0;
                lastOversampling_ = factor;
            }
            float l = 0.0f, r = 0.0f;
            for (int sub = 0; sub < factor; ++sub) {
                float subL = 0.0f, subR = 0.0f;
                for (auto& v : voices_) {
                    if (!v.active()) continue;
                    const float sample = tickVoice(v);
                    subL += sample * v.gl;
                    subR += sample * v.gr;
                }
                if (factor == 1) {
                    l = subL; r = subR;
                } else {
                    decimatorL_[decimatorCursor_] = subL;
                    decimatorR_[decimatorCursor_] = subR;
                    decimatorCursor_ = (decimatorCursor_ + 1) % 32;
                }
            }
            if (factor > 1) {
                const float* coeff = factor == 2 ? fir2 : fir4;
                const int taps = factor == 2 ? 15 : 31;
                for (int tap = 0; tap < taps; ++tap) {
                    const int index = (decimatorCursor_ + 32 - 1 - tap) % 32;
                    l += decimatorL_[index] * coeff[tap];
                    r += decimatorR_[index] * coeff[tap];
                }
            }
            const float g = volSm_ * q_.master;
            out[2 * n] = fastTanh(l * g);
            out[2 * n + 1] = fastTanh(r * g);
        }
    }

private:
    struct Snap {
        bool saw1 = true, pulse1 = false, saw2 = true, pulse2 = false;
        bool sync = false, four = true, unison = false;
        float l1 = 0, l2 = 0, ln = 0, semi2 = 0, det2 = 0, pwBase = 0.5f;
        float cutOct = 0, k = 0, fenvAmt = 0, keytrack = 0;
        float lfoPitch = 0, lfoFilter = 0, lfoPwm = 0;
        float lfo2Pitch = 0, lfo2Filter = 0, lfo2Pwm = 0;
        float xmod = 0.0f, bright = 0.0f, spread = 0.0f, uniSpread = 0.0f;
        float filterMix = 0.0f, multi = 0.0f, fenvInvert = 0.0f, selfOsc = 0.0f;
        float glide = 1.0f, volume = 0, master = 0.4f;
        bool bp = false, lp24 = false, persistent = false;
        int voiceCount = 8, uniVoices = 8, vam = 0, legato = 0, oversampling = 1;
        float transpose = 0.0f, fineTune = 0.0f;
        EnvCoef fe{}, ae{};
    };

    float P(int id) const { return params_[id].load(std::memory_order_relaxed); }

    static float timeOf(float v) { return 0.001f * std::pow(10.0f, 4.0f * v); }  // 1 ms .. 10 s

    EnvCoef coefs(float a, float d, float s, float r) const {
        const float rate = sr_ * static_cast<float>(q_.oversampling);
        auto cA = [&](float t) { return 1.0f - std::exp(-1.466f / (std::max(t, 0.0005f) * rate)); };
        auto cD = [&](float t) { return 1.0f - std::exp(-6.9f / (std::max(t, 0.0005f) * rate)); };
        return {cA(timeOf(a)), cD(timeOf(d)), s, cD(timeOf(r))};
    }

    void updateControl() {
        Snap& q = q_;
        const int osIndex = std::clamp(static_cast<int>(std::lround(P(P_OVERSAMPLING) * 2.0f)), 0, 2);
        q.oversampling = osIndex == 0 ? 1 : (osIndex == 1 ? 2 : 4);
        q.transpose = std::round((P(P_TRANSPOSE) - 0.5f) * 8.0f);
        q.fineTune = (P(P_FINE_TUNE) - 0.5f) * 200.0f;
        q.saw1 = P(P_VCO1_SAW) > 0.5f;   q.pulse1 = P(P_VCO1_PULSE) > 0.5f;
        q.saw2 = P(P_VCO2_SAW) > 0.5f;   q.pulse2 = P(P_VCO2_PULSE) > 0.5f;
        q.sync = P(P_SYNC) > 0.5f;       q.lp24 = P(P_LP24) > 0.5f;
        q.bp = P(P_FILTER_BP) > 0.5f && !q.lp24;
        const int previousVoiceCount = q.voiceCount;
        q.voiceCount = std::clamp(1 + static_cast<int>(std::lround(P(P_VOICE_COUNT) * 15.0f)), 1, kNumVoices);
        q.uniVoices = std::clamp(1 + static_cast<int>(std::lround(P(P_UNI_VOICES) * 15.0f)), 1, kNumVoices);
        if (!q.unison && q.voiceCount < previousVoiceCount) {
            for (int i = q.voiceCount; i < previousVoiceCount; ++i) {
                voices_[i].gate = false;
                voices_[i].fenv.gateOff(); voices_[i].aenv.gateOff();
            }
        }
        q.vam = P(P_VAM) > 0.5f ? 1 : 0;
        q.persistent = P(P_PERSISTENT_VOICING) > 0.5f;
        q.legato = std::clamp(static_cast<int>(std::lround(P(P_LEGATO) * 3.0f)), 0, 3);
        q.filterMix = P(P_FILTER_MIX); q.multi = P(P_FILTER_MULTI);
        q.fenvInvert = P(P_FENV_INVERT); q.selfOsc = P(P_SELF_OSC);
        const bool uni = P(P_UNISON) > 0.5f;
        if (uni != q.unison) { q.unison = uni; releaseAll(); }
        q.l1 = P(P_VCO1_LEVEL); q.l2 = P(P_VCO2_LEVEL); q.ln = P(P_NOISE) * 0.5f;
        q.semi2 = std::round((P(P_VCO2_SEMI) - 0.5f) * 24.0f);
        q.det2 = P(P_VCO2_DETUNE) * 100.0f;
        q.xmod = P(P_XMOD);
        q.bright = P(P_BRIGHT);
        q.spread = P(P_SPREAD) * 8.0f;
        q.pwBase = 0.5f - 0.45f * P(P_PWM);
        q.cutOct = P(P_CUTOFF) * 9.81f;
        q.k = P(P_RESO);
        q.fenvAmt = P(P_FENV_AMT);
        q.keytrack = P(P_KEYTRACK);
        q.lfoPitch = P(P_LFO_PITCH); q.lfoFilter = P(P_LFO_FILTER); q.lfoPwm = P(P_LFO_PWM);
        q.lfo2Pitch = P(P_LFO2_PITCH); q.lfo2Filter = P(P_LFO2_FILTER); q.lfo2Pwm = P(P_LFO2_PWM);
        const float gt = P(P_PORTA) < 0.01f ? 0.0f : 0.01f * std::pow(300.0f, P(P_PORTA));
        q.glide = gt <= 0.0f ? 1.0f : 1.0f - std::exp(-kCtrlRate / (sr_ * gt / 3.0f));
        q.uniSpread = P(P_UNI_DETUNE) * 30.0f;
        q.volume = P(P_VOLUME);
        q.master = q.unison ? 0.4f : 0.7f;
        q.fe = coefs(P(P_F_A), P(P_F_D), P(P_F_S), P(P_F_R));
        q.ae = coefs(P(P_A_A), P(P_A_D), P(P_A_S), P(P_A_R));

        // LFO
        lfoPhase_ += 0.1f * std::pow(300.0f, P(P_LFO_RATE)) * kCtrlRate / sr_;
        if (lfoPhase_ >= 1.0f) { lfoPhase_ -= std::floor(lfoPhase_); shValue_ = noise(); }
        switch (static_cast<int>(std::lround(P(P_LFO_WAVE) * 4.0f))) {
            case 0: lfo_ = std::sin(2.0f * kPi * lfoPhase_); break;
            case 1: lfo_ = 1.0f - 4.0f * std::fabs(lfoPhase_ - 0.5f); break;
            case 2: lfo_ = 2.0f * lfoPhase_ - 1.0f; break;
            case 3: lfo_ = lfoPhase_ < 0.5f ? 1.0f : -1.0f; break;
            default: lfo_ = shValue_; break;
        }

        // Independent second LFO; both modulators run continuously in parallel.
        lfo2Phase_ += 0.1f * std::pow(300.0f, P(P_LFO2_RATE)) * kCtrlRate / sr_;
        if (lfo2Phase_ >= 1.0f) { lfo2Phase_ -= std::floor(lfo2Phase_); shValue2_ = noise(); }
        switch (static_cast<int>(std::lround(P(P_LFO2_WAVE) * 4.0f))) {
            case 0: lfo2_ = std::sin(2.0f * kPi * lfo2Phase_); break;
            case 1: lfo2_ = 1.0f - 4.0f * std::fabs(lfo2Phase_ - 0.5f); break;
            case 2: lfo2_ = 2.0f * lfo2Phase_ - 1.0f; break;
            case 3: lfo2_ = lfo2Phase_ < 0.5f ? 1.0f : -1.0f; break;
            default: lfo2_ = shValue2_; break;
        }

        const float internalRate = sr_ * static_cast<float>(q.oversampling);
        const float maxF = 0.45f * internalRate;
        for (auto& v : voices_) {
            if (!v.active()) continue;
            v.curSemi += (v.tgtSemi - v.curSemi) * q.glide;
            const float vib = lfo_ * q.lfoPitch * q.lfoPitch * 2.0f
                            + lfo2_ * q.lfo2Pitch * q.lfo2Pitch * 2.0f;
            const float base = v.curSemi + bend_ * 2.0f + vib;
            const float oscBase = base + q.transpose;
            const float tuning = q.fineTune / 100.0f;
            const float f1 = 440.0f * std::exp2((oscBase - 69.0f + tuning + (v.drift1 + v.uniCents) / 100.0f) / 12.0f);
            const float f2 = 440.0f * std::exp2((oscBase + q.semi2 - 69.0f + tuning + (q.det2 + v.drift2 + v.uniCents) / 100.0f) / 12.0f);
            v.dt1 = std::min(f1, maxF) / internalRate;
            v.dt2 = std::min(f2, maxF) / internalRate;
            v.pw = std::clamp(q.pwBase + 0.4f * (lfo_ * q.lfoPwm + lfo2_ * q.lfo2Pwm), 0.05f, 0.95f);
            const float envValue = q.fenvInvert > 0.5f ? -v.fenv.level : v.fenv.level;
            const float oct = q.cutOct + q.fenvAmt * envValue * 6.0f
                            + q.keytrack * (v.curSemi - 60.0f) / 12.0f + q.lfoFilter * lfo_ * 2.0f
                            + q.lfo2Filter * lfo2_ * 2.0f;
            const float fc = std::clamp(20.0f * std::exp2(oct), 20.0f, 0.49f * internalRate);
            v.g = std::tan(kPi * fc / (2.0f * internalRate));
        }
    }

    float noise() {
        rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5;
        return static_cast<float>(static_cast<int32_t>(rng_)) / 2147483648.0f;
    }

    float tickVoice(Voice& v) {
        v.fenv.tick(q_.fe);
        const float ae = v.aenv.tick(q_.ae);
        if (!v.active()) { if (!q_.persistent) { v.filter.reset(); v.filter2.reset(); v.lp6 = 0.0f; } v.note = -1; v.gate = false; return 0.0f; }

        v.p1 += v.dt1;
        const bool wrapped = v.p1 >= 1.0f;
        if (wrapped) v.p1 -= 1.0f;
        if (wrapped && q_.sync) {
            v.p2 = v.p1 * (v.dt2 / std::max(v.dt1, 1e-6f));
            v.p2 -= std::floor(v.p2);
        } else {
            v.p2 += v.dt2;
            if (v.p2 >= 1.0f) v.p2 -= 1.0f;
        }
        const float o1 = oscSample(v.p1, v.dt1, v.pw, q_.saw1, q_.pulse1);
        float p2mod = v.p2 + o1 * q_.xmod * 0.12f;
        p2mod -= std::floor(p2mod);
        const float o2 = oscSample(p2mod, v.dt2, v.pw, q_.saw2, q_.pulse2);
        const float bright1 = o1 + q_.bright * (o1 - std::sin(2.0f * kPi * v.p1)) * 0.7f;
        const float bright2 = o2 + q_.bright * (o2 - std::sin(2.0f * kPi * v.p2)) * 0.7f;
        const float mix = (bright1 * q_.l1 + bright2 * q_.l2 + noise() * q_.ln) * 0.7f
                        + noise() * q_.selfOsc * 0.0004f;
        const float damping = std::max(0.025f, 2.0f - q_.k * 1.92f - q_.selfOsc * 1.75f);
        auto a = v.filter.process(mix, v.g, damping);
        a = v.filter.process(mix, v.g, damping); // 2x internal filter rate
        float y;
        if (q_.lp24) {
            auto b = v.filter2.process(a.lp, v.g, damping);
            b = v.filter2.process(a.lp, v.g, damping);
            const float onePole = v.g / (1.0f + v.g);
            v.lp6 += (mix - v.lp6) * onePole;
            v.lp6 += (mix - v.lp6) * onePole;
            const float slope = q_.multi;
            y = b.lp * (1.0f - slope) + v.lp6 * slope;
        } else {
            const float center = q_.bp ? a.bp : a.notch;
            if (q_.filterMix < 0.5f) {
                const float t = q_.filterMix * 2.0f;
                y = a.lp * (1.0f - t) + center * t;
            } else {
                const float t = (q_.filterMix - 0.5f) * 2.0f;
                y = center * (1.0f - t) + a.hp * t;
            }
        }
        return y * ae * (0.3f + 0.7f * v.vel);
    }

    // ---- voice allocation ----
    Voice* pickVoice(int note) {
        for (int i = 0; i < q_.voiceCount; ++i)
            if (voices_[i].note == note && voices_[i].active()) return &voices_[i];
        for (int i = 0; i < q_.voiceCount; ++i)
            if (!voices_[i].active()) return &voices_[i];
        if (q_.vam == 1) {
            // Low-note priority: keep the lowest currently held notes.
            Voice* highest = nullptr;
            for (int i = 0; i < q_.voiceCount; ++i) {
                Voice& v = voices_[i];
                if (v.gate && (!highest || v.note > highest->note)) highest = &v;
            }
            if (highest) return note < highest->note ? highest : nullptr;
            // If every slot is in its release tail, reuse the oldest released voice.
            Voice* released = nullptr;
            for (int i = 0; i < q_.voiceCount; ++i) {
                Voice& v = voices_[i];
                if (!v.gate && (!released || v.age < released->age)) released = &v;
            }
            return released;
        }
        Voice* best = nullptr;
        for (int i = 0; i < q_.voiceCount; ++i) {
            Voice& v = voices_[i];
            if (!v.gate && (!best || v.age < best->age)) best = &v;
        }
        if (best) return best;
        for (int i = 0; i < q_.voiceCount; ++i)
            if (!best || voices_[i].age < best->age) best = &voices_[i];
        return best;
    }

    void handleNoteOn(int note, float vel) {
        if (q_.legato > 0 && !q_.unison) {
            // Legato is a one-voice priority mode with an ordered held-note stack.
            const bool was = voices_[0].active() && voices_[0].gate;
            removeFromStack(note);
            if (stackSize_ < static_cast<int>(stack_.size())) stack_[stackSize_++] = note;
            const int selected = q_.vam == 1
                ? *std::min_element(stack_.begin(), stack_.begin() + stackSize_)
                : stack_[stackSize_ - 1];
            Voice& v = voices_[0];
            v.note = selected; v.gate = true; v.vel = vel; v.age = ++counter_;
            v.uniCents = 0.0f;
            v.drift1 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
            v.drift2 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
            v.tgtSemi = static_cast<float>(selected);
            if (!was) v.curSemi = v.tgtSemi;
            if (!was || (q_.legato & 1) == 0) v.fenv.gateOn();
            if (!was || (q_.legato & 2) == 0) v.aenv.gateOn();
            return;
        }
        if (q_.unison) {
            const bool wasEmpty = stackSize_ == 0;
            removeFromStack(note);
            if (stackSize_ < static_cast<int>(stack_.size())) stack_[stackSize_++] = note;
            const int selected = q_.vam == 1
                ? *std::min_element(stack_.begin(), stack_.begin() + stackSize_)
                : stack_[stackSize_ - 1];
            for (int i = 0; i < q_.uniVoices; ++i) {
                Voice& v = voices_[i];
                v.uniCents = (static_cast<float>(i) - (q_.uniVoices - 1) * 0.5f)
                           / std::max(0.5f, (q_.uniVoices - 1) * 0.5f) * q_.uniSpread;
                v.drift1 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
                v.drift2 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
                v.note = selected; v.gate = true; v.vel = vel; v.age = ++counter_;
                v.tgtSemi = static_cast<float>(selected);
                if (wasEmpty || !v.active()) {
                    v.curSemi = v.tgtSemi;
                    v.fenv.gateOn(); v.aenv.gateOn();
                }
            }
            for (int i = q_.uniVoices; i < kNumVoices; ++i) {
                voices_[i].gate = false;
                voices_[i].fenv.gateOff(); voices_[i].aenv.gateOff();
            }
        } else {
            Voice* v = pickVoice(note);
            if (!v) return;
            const bool was = v->active();
            v->note = note; v->gate = true; v->vel = vel; v->age = ++counter_;
            v->uniCents = 0.0f;
            v->drift1 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
            v->drift2 = (noise() * 2.0f - 1.0f) * (0.15f + q_.spread);
            v->tgtSemi = static_cast<float>(note);
            if (!was) v->curSemi = v->tgtSemi;
            v->fenv.gateOn(); v->aenv.gateOn();
        }
    }

    void handleNoteOff(int note) {
        if (q_.legato > 0 && !q_.unison) {
            removeFromStack(note);
            Voice& v = voices_[0];
            if (stackSize_ == 0) {
                v.gate = false; v.fenv.gateOff(); v.aenv.gateOff();
                return;
            }
            const int selected = q_.vam == 1
                ? *std::min_element(stack_.begin(), stack_.begin() + stackSize_)
                : stack_[stackSize_ - 1];
            v.note = selected; v.tgtSemi = static_cast<float>(selected); v.gate = true;
        } else if (q_.unison) {
            removeFromStack(note);
            if (stackSize_ == 0) { releaseAll(); return; }
            const int selected = q_.vam == 1
                ? *std::min_element(stack_.begin(), stack_.begin() + stackSize_)
                : stack_[stackSize_ - 1];
            for (int i = 0; i < q_.uniVoices; ++i) {
                voices_[i].note = selected;
                voices_[i].tgtSemi = static_cast<float>(selected);
            }
        } else {
            for (auto& v : voices_)
                if (v.note == note && v.gate) { v.gate = false; v.fenv.gateOff(); v.aenv.gateOff(); }
        }
    }

    void releaseAll() {
        stackSize_ = 0;
        for (auto& v : voices_) { v.gate = false; v.fenv.gateOff(); v.aenv.gateOff(); }
    }

    void removeFromStack(int note) {
        int w = 0;
        for (int i = 0; i < stackSize_; ++i) if (stack_[i] != note) stack_[w++] = stack_[i];
        stackSize_ = w;
    }

    // ---- event queue: many producers (spin-locked), one consumer (audio thread) ----
    void push(const Event& e) {
        while (lock_.test_and_set(std::memory_order_acquire)) {}
        const uint32_t t = tail_.load(std::memory_order_relaxed);
        if (t - head_.load(std::memory_order_acquire) < kQueue) {
            queue_[t % kQueue] = e;
            tail_.store(t + 1, std::memory_order_release);
        }
        lock_.clear(std::memory_order_release);
    }

    void drainEvents() {
        uint32_t h = head_.load(std::memory_order_relaxed);
        const uint32_t t = tail_.load(std::memory_order_acquire);
        while (h != t) {
            const Event e = queue_[h % kQueue];
            ++h;
            switch (e.type) {
                case 0: handleNoteOn(e.note, e.value); break;
                case 1: handleNoteOff(e.note); break;
                case 2: releaseAll(); break;
                case 3: bend_ = e.value; break;
            }
        }
        head_.store(h, std::memory_order_release);
    }

    static constexpr uint32_t kQueue = 512;
    std::array<Event, kQueue> queue_{};
    std::atomic<uint32_t> head_{0}, tail_{0};
    std::atomic_flag lock_ = ATOMIC_FLAG_INIT;

    std::array<std::atomic<float>, P_COUNT> params_;
    std::array<Voice, kNumVoices> voices_;
    std::array<int, 16> stack_{};
    int stackSize_ = 0;
    Snap q_;
    float sr_ = 48000.0f, volSm_ = 0.0f, lfoPhase_ = 0.0f, lfo_ = 0.0f, shValue_ = 0.0f;
    float lfo2Phase_ = 0.0f, lfo2_ = 0.0f, shValue2_ = 0.0f, bend_ = 0.0f;
    std::array<float, 32> decimatorL_{}, decimatorR_{};
    int decimatorCursor_ = 0, lastOversampling_ = 1;
    int ctrl_ = 0;
    uint64_t counter_ = 0;
    uint32_t rng_ = 22222u;
};

}  // namespace ob
