#include <stddef.h>
#include <stdlib.h>

#include <gpiod.h>

#include "linux_native.h"

#define PIN_STATE_INACTIVE	GENCONST(jbyte, PIN_STATE_INACTIVE)
#define PIN_STATE_ACTIVE	GENCONST(jbyte, PIN_STATE_ACTIVE)
#define PIN_STATE_MASK		GENCONST(jbyte, PIN_STATE_MASK)
#define PIN_DIR_IN		GENCONST(jbyte, PIN_DIR_IN)
#define PIN_DIR_OUT		GENCONST(jbyte, PIN_DIR_OUT)
#define PIN_DIR_MASK		GENCONST(jbyte, PIN_DIR_MASK)
#define PIN_ACTIVE_HIGH		GENCONST(jbyte, PIN_ACTIVE_HIGH)
#define PIN_ACTIVE_LOW		GENCONST(jbyte, PIN_ACTIVE_LOW)
#define PIN_ACTIVE_MASK		GENCONST(jbyte, PIN_ACTIVE_MASK)

#define PIN_STATE_TO_J(ps)						\
	((ps) == GPIOD_LINE_VALUE_ACTIVE ? PIN_STATE_ACTIVE : PIN_STATE_INACTIVE)
#define PIN_STATE_TO_C(ps)						\
	(((ps) & PIN_STATE_MASK) == PIN_STATE_ACTIVE ?			\
		GPIOD_LINE_VALUE_ACTIVE : GPIOD_LINE_VALUE_INACTIVE)
#define PIN_DIR_TO_C(pd)						\
	(((pd) & PIN_DIR_MASK) == PIN_DIR_OUT ?				\
		GPIOD_LINE_DIRECTION_OUTPUT : GPIOD_LINE_DIRECTION_INPUT)
#define PIN_IS_ACTIVE_LOW(pa)			\
	(((pa) & PIN_ACTIVE_MASK) == PIN_ACTIVE_LOW)

struct linux_gpio_state {
	struct gpiod_chip *chip;
	struct gpiod_line_request *request;
	enum gpiod_line_value *values;
	size_t count;
};

static struct linux_gpio_state *
get_state(JNIEnv *env, jstring dev_j, jintArray lines_j)
{
	struct linux_gpio_state *state;
	struct gpiod_chip *chip;
	const char *dev_c;
	size_t count;

	if ((dev_c = get_c_str(env, dev_j)) == NULL)
		return NULL;
	if ((chip = gpiod_chip_open(dev_c)) == NULL)
		throwf_errno(env, "failed to open GPIO device %s", dev_c);
	(*env)->ReleaseStringUTFChars(env, dev_j, dev_c);
	if (chip == NULL)
		return NULL;

	count = (*env)->GetArrayLength(env, lines_j);
	if ((state = calloc(1, sizeof(*state))) == NULL ||
	    (state->values = calloc(count, sizeof(state->values[0]))) == NULL) {
		throwstr_errno(env, "failed to allocate memory");
		free(state);
		gpiod_chip_close(chip);
		return NULL;
	}

	state->chip = chip;
	state->count = count;
	return state;
}

static void
free_state(struct linux_gpio_state *state)
{
	if (state->request != NULL)
		gpiod_line_request_release(state->request);
	gpiod_chip_close(state->chip);
	free(state->values);
	free(state);
}

static struct gpiod_line_config *
get_line_config(JNIEnv *env, jintArray lines_j, jbyteArray cfgbits_j)
{
	struct gpiod_line_config *line_cfg;
	struct gpiod_line_settings *settings;
	jbyte *cfgbits_c;
	size_t count, i;
	jint *lines_c;

	count = (*env)->GetArrayLength(env, lines_j);
	i = (*env)->GetArrayLength(env, cfgbits_j);
	if (count != i) {
		throwf(env, "GPIO pin and configuration arrays lengths differ: "
		    "%zu and %zu", count, i);
		return NULL;
	}

	settings = NULL;
	line_cfg = NULL;
	lines_c = get_c_ints(env, lines_j);
	cfgbits_c = get_c_bytes(env, cfgbits_j);
	if (lines_c == NULL || cfgbits_c == NULL)
		goto done;

	if ((settings = gpiod_line_settings_new()) == NULL ||
	    (line_cfg = gpiod_line_config_new()) == NULL) {
		throwstr_errno(env, "failed to allocate memory");
		goto done;
	}

	for (i = 0; i < count; i++) {
		unsigned int line = lines_c[i];
		gpiod_line_settings_set_active_low(
			settings, PIN_IS_ACTIVE_LOW(cfgbits_c[i]));
		if (gpiod_line_settings_set_direction(
			    settings, PIN_DIR_TO_C(cfgbits_c[i])) < 0 ||
		    gpiod_line_settings_set_output_value(
			    settings, PIN_STATE_TO_C(cfgbits_c[i])) < 0 ||
		    gpiod_line_config_add_line_settings(
			    line_cfg, &line, 1, settings) < 0) {
			throwstr(env, "GPIO configuration failed");
			gpiod_line_config_free(line_cfg);
			line_cfg = NULL;
			goto done;
		}
	}

done:
	if (lines_c != NULL)
		(*env)->ReleaseIntArrayElements(env, lines_j, lines_c, JNI_ABORT);
	if (cfgbits_c != NULL)
		(*env)->ReleaseByteArrayElements(env, cfgbits_j, cfgbits_c, JNI_ABORT);
	if (settings != NULL)
		gpiod_line_settings_free(settings);
	return line_cfg;
}

static struct gpiod_request_config *
get_request_config(JNIEnv *env, jstring who_j)
{
	struct gpiod_request_config *rcfg;
	const char *who_c;

	if ((who_c = get_c_str(env, who_j)) == NULL)
		return NULL;

	if ((rcfg = gpiod_request_config_new()) == NULL)
		throwstr_errno(env, "failed to allocate memory");
	else
		gpiod_request_config_set_consumer(rcfg, who_c);
	(*env)->ReleaseStringUTFChars(env, who_j, who_c);
	return rcfg;
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    gpio_open
 * Signature: (Ljava/lang/String;Ljava/lang/String;[I[B)J
 */
JNIEXPORT jlong JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_gpio_1open(
    JNIEnv *env, jclass static_class UNUSED,
    jstring dev, jstring who, jintArray lines, jbyteArray cfgbits)
{
	struct gpiod_request_config *req_cfg;
	struct gpiod_line_config *line_cfg;
	struct linux_gpio_state *state;

	state = NULL;
	line_cfg = get_line_config(env, lines, cfgbits);
	req_cfg = get_request_config(env, who);
	if (line_cfg == NULL || req_cfg == NULL)
		goto done;

	if ((state = get_state(env, dev, lines)) == NULL)
		goto done;

	if ((state->request = gpiod_chip_request_lines(
			state->chip, req_cfg, line_cfg)) == NULL) {
		throwstr_errno(env, "failed to request GPIO lines");
		free_state(state);
		state = NULL;
	}

done:
	if (line_cfg != NULL)
		gpiod_line_config_free(line_cfg);
	if (req_cfg != NULL)
		gpiod_request_config_free(req_cfg);

	return PTR_TO_JLONG(state);
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    gpio_get
 * Signature: (J[I)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_gpio_1get(
    JNIEnv *env, jclass static_class UNUSED,
    jlong state_ptr, jintArray lines_j)
{
	struct linux_gpio_state *state = PTR_FROM_JLONG(state_ptr);
	jbyteArray values_j;
	jbyte *values_c;
	size_t count, i;
	jint *lines_c;
	int res;

	values_j = NULL;

	count = (*env)->GetArrayLength(env, lines_j);
	if (count > state->count) {
		throwf(env, "invalid GPIO line count: requested %zu of %zu lines",
		    count, state->count);
		return NULL;
	}

	if ((lines_c = get_c_ints(env, lines_j)) == NULL)
		return NULL;
	res = gpiod_line_request_get_values_subset(
		state->request, count, (unsigned int *)lines_c, state->values);
	(*env)->ReleaseIntArrayElements(env, lines_j, lines_c, JNI_ABORT);
	if (res < 0) {
		throwstr(env, "failed to read GPIO pin value(s)");
		return NULL;
	}
	for (i = 0; i < count; i++)
		if (state->values[i] != GPIOD_LINE_VALUE_ACTIVE &&
		    state->values[i] != GPIOD_LINE_VALUE_INACTIVE) {
			throwstr(env, "failed to read GPIO pin value(s)");
			return NULL;;
		}

	if ((values_j = (*env)->NewByteArray(env, count)) == NULL) {
		throwoom(env);
		return NULL;
	}
	values_c = (*env)->GetPrimitiveArrayCritical(env, values_j, 0);
	if (values_c == NULL) {
		throwoom(env);
		return NULL;
	}
	for (i = 0; i < count; i++)
		values_c[i] = PIN_STATE_TO_J(state->values[i]);
	(*env)->ReleasePrimitiveArrayCritical(env, values_j, values_c, 0);

	return values_j;
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    gpio_set
 * Signature: (J[I[B)V
 */
JNIEXPORT void JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_gpio_1set(
    JNIEnv *env, jclass static_class UNUSED,
    jlong state_ptr, jintArray lines_j, jbyteArray values_j)
{
	struct linux_gpio_state *state = PTR_FROM_JLONG(state_ptr);
	jbyte *values_c;
	size_t count, i;
	jint *lines_c;
	int res;

	count = (*env)->GetArrayLength(env, lines_j);
	values_c = (*env)->GetPrimitiveArrayCritical(env, values_j, 0);
	if (values_c == NULL) {
		throwoom(env);
		return;
	}
	for (i = 0; i < count; i++)
		state->values[i] = PIN_STATE_TO_C(values_c[i]);
	(*env)->ReleasePrimitiveArrayCritical(env, values_j, values_c, 0);

	if ((lines_c = get_c_ints(env, lines_j)) == NULL)
		return;
	res = gpiod_line_request_set_values_subset(
		state->request, count, (unsigned int *)lines_c, state->values);
	(*env)->ReleaseIntArrayElements(env, lines_j, lines_c, JNI_ABORT);
	if (res < 0)
		throwstr(env, "failed to write GPIO pin value(s)");
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    gpio_close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_gpio_1close(
    JNIEnv *env UNUSED, jclass static_class UNUSED,
    jlong state_ptr)
{
	struct linux_gpio_state *state = PTR_FROM_JLONG(state_ptr);

	if (state != NULL)
		free_state(state);
}
