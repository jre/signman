#include <errno.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "linux_native.h"

static jclass
find_exc(JNIEnv *env, const char *name)
{
	jclass cls;

	if ((cls = (*env)->FindClass(env, name)) == NULL)
		cls = (*env)->FindClass(env, BASE_EXC_CLASS);

	return cls;
}

void
throwoom(JNIEnv *env)
{
	jclass cls;

	if ((cls = find_exc(env, OOM_EXC_CLASS)) != NULL)
		(*env)->ThrowNew(env, cls, "out of memory");
}

void
throwstr(JNIEnv *env, const char *msg)
{
	jclass cls;

	if ((cls = find_exc(env, DEV_EXC_CLASS)) != NULL)
		(*env)->ThrowNew(env, cls, msg);
}

void
throwstr_errno(JNIEnv *env, const char *msg)
{
	char *errstr;
	jclass cls;
	int saved;

	saved = errno;
	if ((cls = find_exc(env, (saved == ENOMEM ?
			OOM_EXC_CLASS : DEV_EXC_CLASS))) == NULL)
		return;

	if (asprintf(&errstr, "%s: %s", msg, strerror(saved)) < 0)
		(*env)->ThrowNew(env, cls, msg);
	else {
		(*env)->ThrowNew(env, cls, errstr);
		free(errstr);
	}
}

void
throwf(JNIEnv *env, const char *fmt, ...)
{
	char *errstr;
	jclass cls;
	va_list ap;
	int res;

	if ((cls = find_exc(env, DEV_EXC_CLASS)) == NULL)
		return;

	va_start(ap, fmt);
	res = vasprintf(&errstr, fmt, ap);
	va_end(ap);
	if (res < 0)
		(*env)->ThrowNew(env, cls, fmt);
	else {
		(*env)->ThrowNew(env, cls, errstr);
		free(errstr);
	}
}

void
throwf_errno(JNIEnv *env, const char *fmt, ...)
{
	char *errstr1, *errstr2;
	int saved, res;
	jclass cls;
	va_list ap;

	saved = errno;
	if ((cls = find_exc(env, (saved == ENOMEM ?
			OOM_EXC_CLASS : DEV_EXC_CLASS))) == NULL)
		return;

	va_start(ap, fmt);
	res = vasprintf(&errstr1, fmt, ap);
	va_end(ap);
	if (res < 0) {
		(*env)->ThrowNew(env, cls, fmt);
		return;
	}

	if (asprintf(&errstr2, "%s: %s", errstr1, strerror(saved)) < 0)
		(*env)->ThrowNew(env, cls, errstr1);
	else {
		(*env)->ThrowNew(env, cls, errstr2);
		free(errstr1);
	}
	free(errstr2);
}

const char *
get_c_str(JNIEnv *env, jstring str)
{
	const char *res;

	if ((res = (*env)->GetStringUTFChars(env, str, NULL)) == NULL)
		throwoom(env);

	return res;
}

jbyte *
get_c_bytes(JNIEnv *env, jbyteArray bytes)
{
	jbyte *res;

	if ((res = (*env)->GetByteArrayElements(env, bytes, NULL)) == NULL)
		throwoom(env);

	return res;
}

jint *
get_c_ints(JNIEnv *env, jintArray ints)
{
	jint *res;

	if ((res = (*env)->GetIntArrayElements(env, ints, NULL)) == NULL)
		throwoom(env);

	return res;
}
