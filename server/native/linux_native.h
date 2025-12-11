#include "generated/net_joshe_signman_server_driver_NativeLinux.h"

#define BASE_EXC_CLASS		("java/lang/Exception")
#define OOM_EXC_CLASS		("java/lang/OutOfMemoryError")
#define DEV_EXC_CLASS		("net/joshe/signman/server/DeviceException")

#define GENCONST(t, n)							\
	((t)(net_joshe_signman_server_driver_NativeLinux_ ## n))

#define UNUSED			__attribute__((unused))

#define PTR_TO_U64(p)		((uint64_t)(size_t)(p))
#define PTR_TO_JLONG(p)		((jlong)(size_t)(p))
#define PTR_FROM_JLONG(l)	((void *)(size_t)(l))

void throwoom(JNIEnv *);
void throwstr(JNIEnv *, const char *);
void throwstr_errno(JNIEnv *, const char *);
void throwf(JNIEnv *, const char *, ...);
void throwf_errno(JNIEnv *, const char *, ...);

const char *get_c_str(JNIEnv *, jstring);
jbyte *get_c_bytes(JNIEnv *, jbyteArray);
jint *get_c_ints(JNIEnv *, jintArray);
