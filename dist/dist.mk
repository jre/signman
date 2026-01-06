PREFIX ?= /usr
INSTALL ?= install -C
USER ?= 0
GROUP ?= 0
DEST=${PREFIX}/share/signman

all:
	cd native && ${MAKE}

clean:
	cd native && ${MAKE} clean

install:
	${INSTALL} -d -m 755 -o ${USER} -g ${GROUP} ${DEST}

	${INSTALL} -d -m 755 -o ${USER} -g ${GROUP} ${DEST}/lib
	${INSTALL} -m 444 -o ${USER} -g ${GROUP} lib/* ${DEST}/lib
	${INSTALL} -m 444 -o ${USER} -g ${GROUP} native/*.so ${DEST}/lib || true

	${INSTALL} -d -m 755 -o ${USER} -g ${GROUP} ${DEST}/bin
	${INSTALL} -m 555 -o ${USER} -g ${GROUP} bin/* ${DEST}/bin

	${INSTALL} -m 444 -o ${USER} -g ${GROUP} *.service *.socket /etc/systemd/system/ || true

	${INSTALL} -d -m 755 -o ${USER} -g ${GROUP} ${DEST}/examples
	${INSTALL} -m 444 -o ${USER} -g ${GROUP} examples/* ${DEST}/examples

.PHONY: all clean install
