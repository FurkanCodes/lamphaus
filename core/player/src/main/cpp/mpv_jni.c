/*
 * Lamphaus MPV engine JNI shim.
 *
 * Deliberately dynamically linked (dlopen/dlsym) so the module builds and
 * runs without libmpv.so present: MpvLibrary.availability reports "absent"
 * and the Media3 engine stays primary. The pinned, reproducible libmpv build
 * (scripts/build-mpv-libs.sh) drops libmpv.so into jniLibs/<abi>/ to activate
 * the engine. LGPL: this shim is thin glue and carries no GPL code.
 */
#include <jni.h>
#include <dlfcn.h>
#include <android/native_window_jni.h>
#include <stdlib.h>
#include <string.h>

typedef struct mpv_handle mpv_handle;

typedef enum mpv_error {
    MPV_ERROR_SUCCESS = 0,
    MPV_ERROR_EVENT_QUEUE_FULL = -1,
    MPV_ERROR_NOMEM = -2,
    MPV_ERROR_UNINITIALIZED = -3,
    MPV_ERROR_INVALID_PARAMETER = -4,
    MPV_ERROR_OPTION_NOT_FOUND = -5,
    MPV_ERROR_OPTION_FORMAT = -6,
    MPV_ERROR_OPTION_ERROR = -7,
    MPV_ERROR_PROPERTY_NOT_FOUND = -8,
    MPV_ERROR_PROPERTY_FORMAT = -9,
    MPV_ERROR_PROPERTY_UNAVAILABLE = -10,
    MPV_ERROR_PROPERTY_ERROR = -11,
    MPV_ERROR_COMMAND = -12,
    MPV_ERROR_LOADING_FAILED = -13,
    MPV_ERROR_AO_INIT_FAILED = -14,
    MPV_ERROR_VO_INIT_FAILED = -15,
    MPV_ERROR_NOTHING_TO_PLAY = -16,
    MPV_ERROR_UNKNOWN_FORMAT = -17,
    MPV_ERROR_UNSUPPORTED = -18,
    MPV_ERROR_NOT_IMPLEMENTED = -19,
    MPV_ERROR_GENERIC = -20
} mpv_error;

typedef enum mpv_event_id {
    MPV_EVENT_NONE = 0,
    MPV_EVENT_SHUTDOWN = 1,
    MPV_EVENT_LOG_MESSAGE = 2,
    MPV_EVENT_GET_PROPERTY_REPLY = 3,
    MPV_EVENT_SET_PROPERTY_REPLY = 4,
    MPV_EVENT_COMMAND_REPLY = 5,
    MPV_EVENT_START_FILE = 6,
    MPV_EVENT_END_FILE = 7,
    MPV_EVENT_FILE_LOADED = 8,
    MPV_EVENT_PROPERTY_CHANGE = 22,
} mpv_event_id;

typedef struct mpv_event {
    mpv_event_id event_id;
    int error;
    uint64_t reply_userdata;
    void *data;
} mpv_event;

typedef enum mpv_format {
    MPV_FORMAT_NONE = 0,
    MPV_FORMAT_STRING = 1,
    MPV_FORMAT_OSD_STRING = 2,
    MPV_FORMAT_FLAG = 3,
    MPV_FORMAT_INT64 = 4,
    MPV_FORMAT_DOUBLE = 5,
} mpv_format;

// Function pointers resolved with dlsym once per process.
static struct {
    void *lib;
    mpv_handle *(*create)(void);
    int (*initialize)(mpv_handle *);
    void (*terminate_destroy)(mpv_handle *);
    int (*set_option_string)(mpv_handle *, const char *, const char *);
    int (*set_property_string)(mpv_handle *, const char *, const char *);
    char *(*get_property_string)(mpv_handle *, const char *);
    int (*command)(mpv_handle *, const char **);
    int (*observe_property)(mpv_handle *, uint64_t, const char *, mpv_format);
    mpv_event *(*wait_event)(mpv_handle *, double);
    void (*wakeup)(mpv_handle *);
    int (*free)(void *);
} mpv;

// One native event loop per player handle; serialized by the Kotlin side.
static mpv_handle *handle_of(jlong raw) {
    return (mpv_handle *) (intptr_t) raw;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeCreate(JNIEnv *env, jclass clazz) {
    if (mpv.lib == NULL) {
        mpv.lib = dlopen("libmpv.so", RTLD_NOW | RTLD_LOCAL);
        if (mpv.lib == NULL) return 0;
#define SYM(field, name)                                                  \
        do {                                                              \
            *(void **) (&mpv.field) = dlsym(mpv.lib, name);               \
            if (mpv.field == NULL) { dlclose(mpv.lib); mpv.lib = NULL; return 0; } \
        } while (0)
        SYM(create, "mpv_create");
        SYM(initialize, "mpv_initialize");
        SYM(terminate_destroy, "mpv_terminate_destroy");
        SYM(set_option_string, "mpv_set_option_string");
        SYM(set_property_string, "mpv_set_property_string");
        SYM(get_property_string, "mpv_get_property_string");
        SYM(command, "mpv_command");
        SYM(observe_property, "mpv_observe_property");
        SYM(wait_event, "mpv_wait_event");
        SYM(wakeup, "mpv_wakeup");
        SYM(free, "mpv_free");
#undef SYM
    }
    mpv_handle *handle = mpv.create();
    return (jlong) (intptr_t) handle;
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeInitialize(JNIEnv *env, jclass clazz, jlong raw) {
    mpv_handle *handle = handle_of(raw);
    return handle != NULL && mpv.initialize(handle) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeDestroy(JNIEnv *env, jclass clazz, jlong raw) {
    mpv_handle *handle = handle_of(raw);
    if (handle != NULL) mpv.terminate_destroy(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeSetOptionString(
        JNIEnv *env, jclass clazz, jlong raw, jstring name, jstring value) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return JNI_FALSE;
    const char *nameChars = (*env)->GetStringUTFChars(env, name, NULL);
    const char *valueChars = value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
    int result = mpv.set_option_string(handle, nameChars, valueChars);
    (*env)->ReleaseStringUTFChars(env, name, nameChars);
    if (valueChars != NULL) (*env)->ReleaseStringUTFChars(env, value, valueChars);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeSetPropertyString(
        JNIEnv *env, jclass clazz, jlong raw, jstring name, jstring value) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return JNI_FALSE;
    const char *nameChars = (*env)->GetStringUTFChars(env, name, NULL);
    const char *valueChars = value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
    int result = mpv.set_property_string(handle, nameChars, valueChars);
    (*env)->ReleaseStringUTFChars(env, name, nameChars);
    if (valueChars != NULL) (*env)->ReleaseStringUTFChars(env, value, valueChars);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeGetPropertyString(
        JNIEnv *env, jclass clazz, jlong raw, jstring name) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return NULL;
    const char *nameChars = (*env)->GetStringUTFChars(env, name, NULL);
    char *value = mpv.get_property_string(handle, nameChars);
    (*env)->ReleaseStringUTFChars(env, name, nameChars);
    if (value == NULL) return NULL;
    jstring result = (*env)->NewStringUTF(env, value);
    mpv.free(value);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeCommand(
        JNIEnv *env, jclass clazz, jlong raw, jobjectArray args) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return JNI_FALSE;
    jsize count = (*env)->GetArrayLength(env, args);
    const char **argv = calloc((size_t) count + 1, sizeof(char *));
    for (int i = 0; i < count; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *chars = (*env)->GetStringUTFChars(env, arg, NULL);
        argv[i] = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, arg, chars);
        (*env)->DeleteLocalRef(env, arg);
    }
    int result = mpv.command(handle, argv);
    for (int i = 0; argv[i] != NULL; i++) free((void *) argv[i]);
    free(argv);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeObserveProperty(
        JNIEnv *env, jclass clazz, jlong raw, jstring name) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return JNI_FALSE;
    const char *nameChars = (*env)->GetStringUTFChars(env, name, NULL);
    static uint64_t replyUserdata = 0;
    replyUserdata += 1;
    int result = mpv.observe_property(handle, replyUserdata, nameChars, MPV_FORMAT_STRING);
    (*env)->ReleaseStringUTFChars(env, name, nameChars);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

/**
 * Returns the event id of the next event, or MPV_EVENT_NONE (0) on timeout.
 * Values of property changes are intentionally not marshalled: the Kotlin
 * side re-reads the properties it tracks through get-property calls.
 */
JNIEXPORT jint JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeWaitEvent(
        JNIEnv *env, jclass clazz, jlong raw, jdouble timeoutSeconds) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return 0;
    mpv_event *event = mpv.wait_event(handle, timeoutSeconds);
    if (event == NULL) return 0;
    return (jint) event->event_id;
}

JNIEXPORT void JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeWakeup(JNIEnv *env, jclass clazz, jlong raw) {
    mpv_handle *handle = handle_of(raw);
    if (handle != NULL) mpv.wakeup(handle);
}

/** Attach an android.view.Surface: mediacodec_embed renders straight to it. */
JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeAttachSurface(
        JNIEnv *env, jclass clazz, jlong raw, jobject surface) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL || surface == NULL) return JNI_FALSE;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == NULL) return JNI_FALSE;
    char wid[32];
    snprintf(wid, sizeof(wid), "%lld", (long long) (intptr_t) window);
    int result = mpv.set_property_string(handle, "wid", wid);
    // The window handle stays owned by mpv until wid changes or teardown.
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lamphaus_core_player_mpv_MpvLibrary_nativeDetachSurface(JNIEnv *env, jclass clazz, jlong raw) {
    mpv_handle *handle = handle_of(raw);
    if (handle == NULL) return JNI_FALSE;
    return mpv.set_property_string(handle, "wid", "0") == 0 ? JNI_TRUE : JNI_FALSE;
}
