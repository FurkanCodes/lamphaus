/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/log.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(JNIEnv* env,    \
                                                           jobject thiz,   \
                                                           ##__VA_ARGS__); \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(                \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define AUDIO_DECODER_FUNC(RETURN_TYPE, NAME, ...)               \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// LINT.IfChange
static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java)

static jmethodID growOutputBufferMethod;

// Lamphaus: surround re-encoded to AC-3 for receivers on optical (S/PDIF)
// links, which carry AC-3 but not multichannel PCM (PLY-AUD-01).
static const int AC3_BIT_RATE = 640000;

struct Ac3Transcoder {
  AVCodecContext* encoder = nullptr;
  // Decoded audio to the encoder's planar float, layout, and rate.
  SwrContext* resampler = nullptr;
  // The decoded shape the resampler was set up for.
  AVChannelLayout inputLayout = {};
  int inputRate = 0;
  int inputFormat = -1;
  // Holds samples until a whole AC-3 frame (1536 samples) is available.
  AVAudioFifo* fifo = nullptr;
  AVPacket* packet = nullptr;
  // Samples queued before the current packet's audio, so its first frame
  // can be timestamped where its audio starts.
  int leadSamples = 0;
};

// Kept in AVCodecContext.opaque.
struct DecoderState {
  SwrContext* resampler = nullptr;
  bool transcodeRequested = false;
  // The decoded audio cannot be transcoded (stereo, or the encoder failed):
  // output PCM as usual.
  bool transcodeDeclined = false;
  Ac3Transcoder* ac3 = nullptr;
};

static DecoderState* stateOf(AVCodecContext* context) {
  return static_cast<DecoderState*>(context->opaque);
}

static void releaseTranscoder(Ac3Transcoder* ac3) {
  if (!ac3) return;
  av_channel_layout_uninit(&ac3->inputLayout);
  avcodec_free_context(&ac3->encoder);
  swr_free(&ac3->resampler);
  if (ac3->fifo) av_audio_fifo_free(ac3->fifo);
  av_packet_free(&ac3->packet);
  delete ac3;
}

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
const AVCodec* getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext* createContext(JNIEnv* env, const AVCodec* codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount,
                              jboolean transcodeToAc3);

struct GrowOutputBufferCallback {
  uint8_t* operator()(int requiredSize) const;

  JNIEnv* env;
  jobject thiz;
  jobject decoderOutputBuffer;
};

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(AVCodecContext* context, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char* functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext* context);

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOGE("JNI_OnLoad: GetEnv failed");
    return -1;
  }
  jclass clazz =
      env->FindClass("androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder");
  if (!clazz) {
    LOGE("JNI_OnLoad: FindClass failed");
    return -1;
  }
  growOutputBufferMethod =
      env->GetMethodID(clazz, "growOutputBuffer",
                       "(Landroidx/media3/decoder/"
                       "SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
  if (!growOutputBufferMethod) {
    LOGE("JNI_OnLoad: GetMethodID failed");
    return -1;
  }
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

LIBRARY_FUNC(jint, ffmpegGetInputBufferPaddingSize) {
  return (jint)AV_INPUT_BUFFER_PADDING_SIZE;
}

LIBRARY_FUNC(jboolean, ffmpegHasDecoder, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

AUDIO_DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName,
                   jbyteArray extraData, jboolean outputFloat,
                   jint rawSampleRate, jint rawChannelCount,
                   jboolean transcodeToAc3) {
  const AVCodec* codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount, transcodeToAc3);
}

AUDIO_DECODER_FUNC(jint, ffmpegDecode, jlong context, jobject inputData,
                   jint inputSize, jobject decoderOutputBuffer,
                   jobject outputData, jint outputSize) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  if (!inputData || !decoderOutputBuffer || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return -1;
  }
  uint8_t* inputBuffer = (uint8_t*)env->GetDirectBufferAddress(inputData);
  uint8_t* outputBuffer = (uint8_t*)env->GetDirectBufferAddress(outputData);
  AVPacket* packet = av_packet_alloc();
  if (!packet) {
    LOGE("Failed to allocate packet.");
    return -1;
  }
  packet->data = inputBuffer;
  packet->size = inputSize;
  const int ret =
      decodePacket((AVCodecContext*)context, packet, outputBuffer, outputSize,
                   GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
  av_packet_free(&packet);
  return ret;
}

uint8_t* GrowOutputBufferCallback::operator()(int requiredSize) const {
  jobject newOutputData = env->CallObjectMethod(
      thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
  if (env->ExceptionCheck()) {
    LOGE("growOutputBuffer() failed");
    env->ExceptionDescribe();
    return nullptr;
  }
  return static_cast<uint8_t*>(env->GetDirectBufferAddress(newOutputData));
}

AUDIO_DECODER_FUNC(jint, ffmpegGetChannelCount, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  DecoderState* state = stateOf((AVCodecContext*)context);
  if (state && state->ac3) return state->ac3->encoder->ch_layout.nb_channels;
  return ((AVCodecContext*)context)->ch_layout.nb_channels;
}

AUDIO_DECODER_FUNC(jint, ffmpegGetSampleRate, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  DecoderState* state = stateOf((AVCodecContext*)context);
  if (state && state->ac3) return state->ac3->encoder->sample_rate;
  return ((AVCodecContext*)context)->sample_rate;
}

AUDIO_DECODER_FUNC(jboolean, ffmpegIsTranscodingToAc3, jlong context) {
  if (!context) return JNI_FALSE;
  DecoderState* state = stateOf((AVCodecContext*)context);
  return state && state->ac3 ? JNI_TRUE : JNI_FALSE;
}

AUDIO_DECODER_FUNC(jint, ffmpegGetTranscodeLeadSamples, jlong context) {
  if (!context) return 0;
  DecoderState* state = stateOf((AVCodecContext*)context);
  return state && state->ac3 ? state->ac3->leadSamples : 0;
}

AUDIO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext, jbyteArray extraData) {
  AVCodecContext* context = (AVCodecContext*)jContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecID codecId = context->codec_id;
  DecoderState* state = stateOf(context);
  jboolean transcodeToAc3 =
      (jboolean)(state && state->transcodeRequested);
  if (codecId == AV_CODEC_ID_TRUEHD) {
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(context);
    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1, transcodeToAc3);
  }

  avcodec_flush_buffers(context);
  // A seek starts a fresh AC-3 stream: queued samples belong to the old position.
  if (state && state->ac3) {
    releaseTranscoder(state->ac3);
    state->ac3 = nullptr;
  }
  return (jlong)context;
}

AUDIO_DECODER_FUNC(void, ffmpegRelease, jlong context) {
  if (context) {
    releaseContext((AVCodecContext*)context);
  }
}

const AVCodec* getCodecByName(JNIEnv* env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char* codecNameChars = env->GetStringUTFChars(codecName, NULL);
  const AVCodec* codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

AVCodecContext* createContext(JNIEnv* env, const AVCodec* codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount,
                              jboolean transcodeToAc3) {
  AVCodecContext* context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t*)av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(context);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte*)context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    av_channel_layout_default(&context->ch_layout, rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(context);
    return NULL;
  }
  DecoderState* state = new DecoderState();
  state->transcodeRequested = transcodeToAc3;
  context->opaque = state;
  return context;
}

/**
 * Opens an AC-3 encoder for audio shaped like [frame]: 5.1 for six or more
 * channels (7.1 folds down), the decoded layout when AC-3 carries it, and
 * 48 kHz unless the source is already at an AC-3 rate. Returns null for
 * stereo or mono, which optical links carry as PCM.
 */
static Ac3Transcoder* createTranscoder(const AVFrame* frame) {
  if (frame->ch_layout.nb_channels <= 2) return nullptr;
  const AVCodec* codec = avcodec_find_encoder(AV_CODEC_ID_AC3);
  if (!codec) {
    LOGE("AC-3 encoder not available.");
    return nullptr;
  }
  Ac3Transcoder* ac3 = new Ac3Transcoder();
  ac3->encoder = avcodec_alloc_context3(codec);
  ac3->packet = av_packet_alloc();
  if (!ac3->encoder || !ac3->packet) {
    releaseTranscoder(ac3);
    return nullptr;
  }
  AVCodecContext* encoder = ac3->encoder;
  AVChannelLayout layout = AV_CHANNEL_LAYOUT_5POINT1;
  if (frame->ch_layout.nb_channels < 6) {
    const AVChannelLayout* supported = nullptr;
    int count = 0;
    if (avcodec_get_supported_config(nullptr, codec,
                                     AV_CODEC_CONFIG_CHANNEL_LAYOUT, 0,
                                     (const void**)&supported, &count) >= 0) {
      for (int i = 0; i < count; i++) {
        if (!av_channel_layout_compare(&supported[i], &frame->ch_layout)) {
          av_channel_layout_copy(&layout, &frame->ch_layout);
          break;
        }
      }
    }
  }
  av_channel_layout_copy(&encoder->ch_layout, &layout);
  int rate = frame->sample_rate;
  encoder->sample_rate =
      rate == 48000 || rate == 44100 || rate == 32000 ? rate : 48000;
  encoder->sample_fmt = AV_SAMPLE_FMT_FLTP;
  encoder->bit_rate = AC3_BIT_RATE;
  int result = avcodec_open2(encoder, codec, nullptr);
  if (result < 0) {
    logError("avcodec_open2(ac3)", result);
    releaseTranscoder(ac3);
    return nullptr;
  }
  ac3->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_FLTP,
                                  encoder->ch_layout.nb_channels,
                                  encoder->frame_size * 2);
  if (!ac3->fifo) {
    releaseTranscoder(ac3);
    return nullptr;
  }
  return ac3;
}

/**
 * Queues [frame] and writes every whole AC-3 frame now available to the
 * output, returning the bytes written or a negative error.
 */
static int transcodeFrame(Ac3Transcoder* ac3, const AVFrame* frame,
                          uint8_t** outputBuffer, int* outputSize, int outSize,
                          GrowOutputBufferCallback& growBuffer) {
  AVCodecContext* encoder = ac3->encoder;
  // Set up (or, when the stream changes shape, redo) the conversion into
  // the encoder's format.
  if (!ac3->resampler || ac3->inputRate != frame->sample_rate ||
      ac3->inputFormat != frame->format ||
      av_channel_layout_compare(&ac3->inputLayout, &frame->ch_layout)) {
    swr_free(&ac3->resampler);
    int result = swr_alloc_set_opts2(
        &ac3->resampler, &encoder->ch_layout, AV_SAMPLE_FMT_FLTP,
        encoder->sample_rate, &frame->ch_layout, (AVSampleFormat)frame->format,
        frame->sample_rate, 0, nullptr);
    if (result < 0 || swr_init(ac3->resampler) < 0) {
      LOGE("Failed to set up the AC-3 resampler.");
      return AUDIO_DECODER_ERROR_OTHER;
    }
    av_channel_layout_uninit(&ac3->inputLayout);
    av_channel_layout_copy(&ac3->inputLayout, &frame->ch_layout);
    ac3->inputRate = frame->sample_rate;
    ac3->inputFormat = frame->format;
  }
  int channels = encoder->ch_layout.nb_channels;
  int capacity = swr_get_out_samples(ac3->resampler, frame->nb_samples);
  uint8_t** converted = nullptr;
  if (av_samples_alloc_array_and_samples(&converted, nullptr, channels,
                                         capacity, AV_SAMPLE_FMT_FLTP,
                                         0) < 0) {
    return AUDIO_DECODER_ERROR_OTHER;
  }
  int samples = swr_convert(ac3->resampler, converted, capacity,
                            (const uint8_t**)frame->extended_data,
                            frame->nb_samples);
  if (samples > 0) av_audio_fifo_write(ac3->fifo, (void**)converted, samples);
  av_freep(&converted[0]);
  av_freep(&converted);
  if (samples < 0) return AUDIO_DECODER_ERROR_INVALID_DATA;

  int written = 0;
  while (av_audio_fifo_size(ac3->fifo) >= encoder->frame_size) {
    AVFrame* input = av_frame_alloc();
    if (!input) return AUDIO_DECODER_ERROR_OTHER;
    input->nb_samples = encoder->frame_size;
    input->format = AV_SAMPLE_FMT_FLTP;
    input->sample_rate = encoder->sample_rate;
    av_channel_layout_copy(&input->ch_layout, &encoder->ch_layout);
    if (av_frame_get_buffer(input, 0) < 0) {
      av_frame_free(&input);
      return AUDIO_DECODER_ERROR_OTHER;
    }
    av_audio_fifo_read(ac3->fifo, (void**)input->data, encoder->frame_size);
    int result = avcodec_send_frame(encoder, input);
    av_frame_free(&input);
    if (result < 0) {
      logError("avcodec_send_frame(ac3)", result);
      return AUDIO_DECODER_ERROR_OTHER;
    }
    while (avcodec_receive_packet(encoder, ac3->packet) == 0) {
      int size = ac3->packet->size;
      if (outSize + written + size > *outputSize) {
        *outputSize = outSize + written + size;
        uint8_t* grown = growBuffer(*outputSize);
        if (!grown) {
          av_packet_unref(ac3->packet);
          return AUDIO_DECODER_ERROR_OTHER;
        }
        *outputBuffer = grown + outSize;
      }
      memcpy(*outputBuffer + written, ac3->packet->data, size);
      written += size;
      av_packet_unref(ac3->packet);
    }
  }
  return written;
}

int decodePacket(AVCodecContext* context, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer) {
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return transformError(result);
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  DecoderState* state = stateOf(context);
  if (state->ac3) state->ac3->leadSamples = av_audio_fifo_size(state->ac3->fifo);
  while (true) {
    AVFrame* frame = av_frame_alloc();
    if (!frame) {
      LOGE("Failed to allocate output frame.");
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_free(&frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return transformError(result);
    }

    if (state->transcodeRequested && !state->transcodeDeclined) {
      if (!state->ac3) {
        state->ac3 = createTranscoder(frame);
        if (state->ac3) {
          state->ac3->leadSamples = 0;
        } else {
          state->transcodeDeclined = true;
        }
      }
      if (state->ac3) {
        uint8_t* frameOutput = outputBuffer;
        int written = transcodeFrame(state->ac3, frame, &frameOutput,
                                     &outputSize, outSize, growBuffer);
        av_frame_free(&frame);
        if (written < 0) return written;
        outputBuffer = frameOutput + written;
        outSize += written;
        continue;
      }
    }

    // Resample output.
    AVSampleFormat sampleFormat = context->sample_fmt;
    int channelCount = context->ch_layout.nb_channels;
    int sampleRate = context->sample_rate;
    int sampleCount = frame->nb_samples;
    int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                              sampleFormat, 1);
    SwrContext* resampleContext = state->resampler;
    if (!resampleContext) {
      result =
          swr_alloc_set_opts2(&resampleContext,             // ps
                              &context->ch_layout,          // out_ch_layout
                              context->request_sample_fmt,  // out_sample_fmt
                              sampleRate,                   // out_sample_rate
                              &context->ch_layout,          // in_ch_layout
                              sampleFormat,                 // in_sample_fmt
                              sampleRate,                   // in_sample_rate
                              0,                            // log_offset
                              NULL                          // log_ctx
          );
      if (result < 0) {
        logError("swr_alloc_set_opts2", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      result = swr_init(resampleContext);
      if (result < 0) {
        logError("swr_init", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      state->resampler = resampleContext;
    }

    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = swr_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGD(
          "Output buffer size (%d) too small for output data (%d), "
          "reallocating buffer.",
          outputSize, outSize + bufferOutSize);
      outputSize = outSize + bufferOutSize;
      outputBuffer = growBuffer(outputSize);
      if (!outputBuffer) {
        LOGE("Failed to reallocate output buffer.");
        av_frame_free(&frame);
        return AUDIO_DECODER_ERROR_OTHER;
      }
    }
    result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                         (const uint8_t**)frame->data, frame->nb_samples);
    av_frame_free(&frame);
    if (result < 0) {
      logError("swr_convert", result);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    int available = swr_get_out_samples(resampleContext, 0);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    outputBuffer += bufferOutSize;
    outSize += bufferOutSize;
  }
  return outSize;
}

int transformError(int errorNumber) {
  return errorNumber == AVERROR_INVALIDDATA ? AUDIO_DECODER_ERROR_INVALID_DATA
                                            : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char* functionName, int errorNumber) {
  char* buffer = (char*)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(AVCodecContext* context) {
  if (!context) {
    return;
  }
  DecoderState* state = stateOf(context);
  if (state) {
    swr_free(&state->resampler);
    releaseTranscoder(state->ac3);
    delete state;
    context->opaque = NULL;
  }
  avcodec_free_context(&context);
}
