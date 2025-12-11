CC ?= cc
CFLAGS ?= -g -O2
CFLAGS += -Wall -W
CFLAGS += -I$${JAVA_HOME:?}/include
CFLAGS += -I$${JAVA_HOME:?}/include/$$(uname -s | tr A-Z a-z)
LDFLAGS ?=
LDFLAGS += -shared -fPIC
LIBS ?=

SRCS_linux = linux_util.c linux_gpio.c linux_spi.c
LIBS_linux = -lgpiod

all: libsignman-native-linux.so

clean:
	rm -f *.so

libsignman-native-linux.so: ${SRCS_linux}
	${CC} ${CFLAGS} ${LDFLAGS} -o $@ ${SRCS_linux} ${LIBS} ${LIBS_linux}
