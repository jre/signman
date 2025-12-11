#include <sys/ioctl.h>
#include <sys/param.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <linux/spi/spidev.h>

#include "linux_native.h"

#define SPI_CFG_MODE_MASK	GENCONST(jint, SPI_CFG_MODE_MASK)
#define SPI_CFG_LSB_FIRST	GENCONST(jint, SPI_CFG_LSB_FIRST)
#define SPI_CFG_HALF_DUPLEX	GENCONST(jint, SPI_CFG_HALF_DUPLEX)

static unsigned int spidev_bufsiz;

static int
save_spidev_bufsiz(JNIEnv *env)
{
	char str[11], *endp;
	long parsed;
	int fd;

	memset(str, 0, sizeof(str));
	if ((fd = open("/sys/module/spidev/parameters/bufsiz", O_RDONLY)) == -1 ||
	    read(fd, str, sizeof(str) - 1) < 0) {
		throwstr_errno(env, "failed to read spidev bufsiz");
		if (fd >= 0)
			close(fd);
		return -1;
	}
	close(fd);

	endp = NULL;
	parsed = strtol(str, NULL, 10);
	if (endp == str || parsed <= 0 || parsed > (long)INT_MAX) {
		throwf(env, "failed to parse spidev bufsiz: %s", str);
		return -1;
	}

	spidev_bufsiz = (unsigned int)parsed;
	return 0;
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    spi_open
 * Signature: (Ljava/lang/String;II)I
  */
JNIEXPORT jint JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_spi_1open(
    JNIEnv *env, jclass static_class UNUSED,
    jstring dev_j, jint hz, jint mode)
{
	const char *dev_c;
	uint32_t req32;
	unsigned char req8;
	int fd;

	if (spidev_bufsiz == 0 && save_spidev_bufsiz(env) < 0)
		return -1;
		
	if ((dev_c = get_c_str(env, dev_j)) == NULL)
		return -1;
	if ((fd = open(dev_c, O_RDWR)) < 0) {
		throwf_errno(env, "failed to open device %s", dev_c);
		goto done;
	}

	req32 = SPI_CS_HIGH |
	    (mode & SPI_CFG_LSB_FIRST ? SPI_LSB_FIRST : 0) |
	    (mode & SPI_CFG_HALF_DUPLEX ? SPI_3WIRE : 0);
	switch (mode & SPI_CFG_MODE_MASK) {
	case 1:
		req32 |= SPI_MODE_1;
		break;
	case 2:
		req32 |= SPI_MODE_2;
		break;
	case 3:
		req32 |= SPI_MODE_3;
		break;
	default:
		req32 |= SPI_MODE_0;
		break;
	}

	if (ioctl(fd, SPI_IOC_WR_MODE32, &req32) < 0) {
		throwf_errno(env, "failed to set SPI mode of device %s", dev_c);
		goto fail;
	}

	req32 = hz;
	if (ioctl(fd, SPI_IOC_WR_MAX_SPEED_HZ, &req32) < 0) {
		throwf_errno(env, "failed to set SPI speed of device %s", dev_c);
		goto fail;
	}

	req8 = 8;
	if (ioctl(fd, SPI_IOC_WR_BITS_PER_WORD, &req8) < 0){
		throwf_errno(env, "failed to set SPI word size of device %s", dev_c);
		goto fail;
	}

	goto done;

fail:
	close(fd);
	fd = -1;
done:
	(*env)->ReleaseStringUTFChars(env, dev_j, dev_c);
	return fd;
}

static void
fill_ioc_transfer(struct spi_ioc_transfer *req, jbyte **out, jsize *out_len,
    jbyte **in, jsize *in_len, unsigned int *max_len)
{
	uint32_t len;

	if (*out_len == 0 || *in_len == 0 || *out_len == *in_len)
		len = MAX(*out_len, *in_len);
	else
		len = MIN(*out_len, *in_len);
	if (len > *max_len)
		len = *max_len;

	req->len = len;
	*max_len -= len;
	if (*out_len > 0) {
		req->tx_buf = PTR_TO_U64(*out);
		*out += req->len;
		*out_len -= req->len;
	}
	if (*in_len > 0) {
		req->rx_buf = PTR_TO_U64(*in);
		*in += req->len;
		*in_len -= req->len;
	}
}

static int
do_spi_transfer(JNIEnv *env, int fd, jbyte *out, jsize out_len, jbyte *in, jsize in_len, jboolean split)
{
	struct spi_ioc_transfer req[2];
	unsigned int nreq, max_len;

	while (out_len > 0 || in_len > 0) {
		memset(&req, 0, sizeof(req));
		max_len = (split ? spidev_bufsiz : UINT_MAX);
		nreq = 1;
		fill_ioc_transfer(&(req[0]), &out, &out_len, &in, &in_len, &max_len);
		if ((out_len > 0 || in_len > 0) && max_len > 0) {
			fill_ioc_transfer(&(req[0]), &out, &out_len, &in, &in_len, &max_len);
			nreq++;
		}
		if (ioctl(fd, SPI_IOC_MESSAGE(nreq), &req) < 0) {
			throwstr_errno(env, "failed to perform SPI transfer");
			return -1;
		}
	}

	return 0;
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    spi_io
 * Signature: (I[BIZ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_spi_1io(
    JNIEnv *env, jclass static_class UNUSED,
    jint fd, jbyteArray out_j, jint in_len, jboolean split)
{
	jbyte *out_c, *in_c;
	jbyteArray in_j, ret_j;
	jsize out_len;

	out_len = 0;
	out_c = NULL;
	in_j = NULL;
	in_c = NULL;
	ret_j = NULL;

	if (out_j != NULL) {
		out_len = (*env)->GetArrayLength(env, out_j);
		if ((out_c = get_c_bytes(env, out_j)) == NULL)
			return NULL;
	}

	if (out_len == 0 && in_len == 0)
		goto done;

	if (in_len != 0) {
		if ((in_j = (*env)->NewByteArray(env, in_len)) == NULL) {
			throwoom(env);
			goto done;
		}
		if ((in_c = get_c_bytes(env, in_j)) == NULL) {
			in_j = NULL;
			goto done;
		}
	}

	if (do_spi_transfer(env, fd, out_c, out_len, in_c, in_len, split) == 0)
		ret_j = in_j;

done:
	if (in_c != NULL)
		(*env)->ReleaseByteArrayElements(env, in_j, in_c, 0);
	if (out_c != NULL)
		(*env)->ReleaseByteArrayElements(env, out_j, out_c, JNI_ABORT);
	return ret_j;
}

/*
 * Class:     net_joshe_signman_server_driver_NativeLinux
 * Method:    spi_close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_net_joshe_signman_server_driver_NativeLinux_spi_1close(
    JNIEnv *env, jclass static_class UNUSED,
    jint fd)
{
	if (fd > 0 && close(fd) < 0)
		throwstr_errno(env, "failed to close SPI device");
}
